/*
 * This file is part of Misq.
 *
 * Misq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Misq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Misq. If not, see <http://www.gnu.org/licenses/>.
 */

package network.misq.network.p2p.node;


import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;
import network.misq.common.util.NetworkUtils;
import network.misq.common.util.StringUtils;
import network.misq.network.p2p.message.Message;
import network.misq.network.p2p.node.authorization.AuthorizationService;
import network.misq.network.p2p.node.authorization.AuthorizedMessage;
import network.misq.network.p2p.node.transport.ClearNetTransport;
import network.misq.network.p2p.node.transport.I2PTransport;
import network.misq.network.p2p.node.transport.TorTransport;
import network.misq.network.p2p.node.transport.Transport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.*;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.concurrent.CompletableFuture.runAsync;

/**
 * Responsibility:
 * - Creates Transport based on TransportType
 * - Creates 1 Server associated with that server
 * - Creates inbound and outbound connections.
 * - Checks if a connection has been created when sending a message and creates one otherwise.
 * - Performs initial connection handshake for exchanging capability and performing authorization
 * - Performs authorization protocol at sending and receiving messages
 * - Notifies ConnectionListeners when a new connection has been created or closed.
 * - Notifies MessageListeners when a new message has been received.
 */
public class Node {
    private static final Logger log = LoggerFactory.getLogger(Node.class);
    public static final String DEFAULT_NODE_ID = "default";

    public static record Config(Transport.Type transportType,
                                Set<Transport.Type> supportedTransportTypes,
                                AuthorizationService authorizationService,
                                Transport.Config transportConfig) {
    }

    private final Transport transport;
    private final Set<Transport.Type> supportedTransportTypes;
    private final AuthorizationService authorizationService;
    private final String nodeId;

    private final Map<Address, OutboundConnection> outboundConnectionMap = new ConcurrentHashMap<>();
    private final Set<InboundConnection> inboundConnections = new CopyOnWriteArraySet<>();
    private final Set<MessageListener> messageListeners = new CopyOnWriteArraySet<>();
    private final Set<ConnectionListener> connectionListeners = new CopyOnWriteArraySet<>();

    private Optional<Server> server = Optional.empty();
    private Optional<Capability> myCapability;
    private volatile boolean isStopped;

    public Node(Config config, String nodeId) {
        transport = getNetworkProxy(config.transportType(), config.transportConfig());
        supportedTransportTypes = config.supportedTransportTypes();
        authorizationService = config.authorizationService();
        this.nodeId = nodeId;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////
    // Server
    ///////////////////////////////////////////////////////////////////////////////////////////////////

    public CompletableFuture<Transport.ServerSocketResult> initializeServer(int port) {
        return transport.initialize()
                .thenCompose(e -> createServerAndListen(port));
    }

    public CompletableFuture<Transport.ServerSocketResult> createServerAndListen(int port) {
        return transport.getServerSocket(port, nodeId)
                .thenCompose(serverSocketResult -> {
                    myCapability = Optional.of(new Capability(serverSocketResult.address(), supportedTransportTypes));
                    server = Optional.of(new Server(serverSocketResult,
                            socket -> onClientSocket(socket, serverSocketResult, myCapability.get()),
                            this::handleException));
                    return CompletableFuture.completedFuture(serverSocketResult);
                });
    }

    private void onClientSocket(Socket socket, Transport.ServerSocketResult serverSocketResult, Capability myCapability) {
        ConnectionHandshake connectionHandshake = new ConnectionHandshake(socket, myCapability, authorizationService);
        connectionHandshake.onSocket()
                .whenComplete((peersCapability, throwable) -> {
                    if (throwable == null) {
                        try {
                            InboundConnection connection = new InboundConnection(socket, serverSocketResult, peersCapability, this::onMessage);
                            connection.startListen(exception -> handleException(connection, exception));
                            inboundConnections.add(connection);
                            runAsync(() -> connectionListeners.forEach(listener -> listener.onConnection(connection)));
                        } catch (IOException exception) {
                            handleException(exception);
                        }
                    } else {
                        handleException(throwable);
                    }
                });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////
    // Send
    ///////////////////////////////////////////////////////////////////////////////////////////////////

    public CompletableFuture<Connection> send(Message message, Address address) {
        return getConnection(address)
                .thenCompose(connection -> send(message, connection));
    }

    public CompletableFuture<Connection> send(Message message, Connection connection) {
        return authorizationService.createToken(message.getClass())
                .thenCompose(token -> connection.send(new AuthorizedMessage(message, token)))
                .exceptionally(exception -> {
                    handleException(connection, exception);
                    return connection;
                });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////
    // OutboundConnection
    ///////////////////////////////////////////////////////////////////////////////////////////////////

    public CompletableFuture<Connection> getConnection(Address address) {
        if (outboundConnectionMap.containsKey(address)) {
            return CompletableFuture.completedFuture(outboundConnectionMap.get(address));
        } else {
            return createOutboundConnection(address);
        }
    }

    private CompletableFuture<Connection> createOutboundConnection(Address address) {
        return myCapability.map(capability -> createOutboundConnection(address, capability))
                .orElseGet(() -> initializeServer(NetworkUtils.findFreeSystemPort())
                        .thenCompose(serverSocketResult -> createOutboundConnection(address, myCapability.get())));
    }

    private CompletableFuture<Connection> createOutboundConnection(Address address, Capability myCapability) {
        Socket socket;
        try {
            socket = transport.getSocket(address);
        } catch (IOException e) {
            handleException(e);
            return CompletableFuture.failedFuture(e);
        }

        CompletableFuture<Connection> future = new CompletableFuture<>();
        ConnectionHandshake connectionHandshake = new ConnectionHandshake(socket, myCapability, authorizationService);
        connectionHandshake.start()
                .whenComplete((peersCapability, throwable) -> {
                    log.debug("Create new outbound connection to {}", address);
                    checkArgument(address.equals(peersCapability.address()),
                            "Peers reported address must match address we used to connect");
                    OutboundConnection connection = new OutboundConnection(socket, address, peersCapability, this::onMessage);
                    try {
                        connection.startListen(exception -> {
                            handleException(connection, exception);
                            future.completeExceptionally(exception);
                        });

                        outboundConnectionMap.put(address, connection);
                        runAsync(() -> connectionListeners.forEach(listener -> listener.onConnection(connection)));
                        future.complete(connection);
                    } catch (IOException exception) {
                        handleException(connection, exception);
                        future.completeExceptionally(exception);
                    }
                });
        return future;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////
    // MessageHandler
    ///////////////////////////////////////////////////////////////////////////////////////////////////

    private void onMessage(Message message, Connection connection) {
        if (!isStopped && message instanceof AuthorizedMessage authorizedMessage) {
            if (authorizationService.isAuthorized(authorizedMessage)) {
                runAsync(() -> messageListeners.forEach(listener -> listener.onMessage(authorizedMessage.message(), connection, nodeId)));
            } else {
                log.warn("Message authorization failed. authorizedMessage={}", StringUtils.truncate(authorizedMessage.toString()));
            }
        }
    }

    public CompletableFuture<Void> shutdown() {
        log.info("shutdown {}", this);
        if (isStopped) {
            return CompletableFuture.completedFuture(null);
        }
        isStopped = true;

        CountDownLatch latch = new CountDownLatch(outboundConnectionMap.values().size() +
                inboundConnections.size() +
                ((int) server.stream().count())
                + 1); // For transport
        return CompletableFuture.runAsync(() -> {
            outboundConnectionMap.values().forEach(connection -> connection.shutdown().whenComplete((v, t) -> latch.countDown()));
            inboundConnections.forEach(connection -> connection.shutdown().whenComplete((v, t) -> latch.countDown()));
            server.ifPresent(server -> server.shutdown().whenComplete((v, t) -> latch.countDown()));
            transport.shutdown().whenComplete((v, t) -> latch.countDown());

            try {
                latch.await(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                log.error("Shutdown interrupted by timeout");
            }
            outboundConnectionMap.clear();
            inboundConnections.clear();
            messageListeners.clear();
        });
    }

    public Optional<Socks5Proxy> getSocksProxy() throws IOException {
        return transport.getSocksProxy();
    }

    public void addMessageListener(MessageListener messageListener) {
        messageListeners.add(messageListener);
    }

    public void removeMessageListener(MessageListener messageListener) {
        messageListeners.remove(messageListener);
    }

    public void addConnectionListener(ConnectionListener connectionListener) {
        connectionListeners.add(connectionListener);
    }

    public void removeConnectionListener(ConnectionListener connectionListener) {
        connectionListeners.remove(connectionListener);
    }

    //todo
    // Only to be used when we know that we have already created the default server
   /* public Address getMyAddress() {
        Optional<Address> myAddress = findMyAddress();
        checkArgument(myAddress.isPresent(), "myAddress need to be present at a getMyAddress call");
        return myAddress.get();
    }*/

    //todo
 /*   public Optional<Address> findMyAddress() {
        return findMyAddress(DEFAULT_CONNECTION_ID);
    }*/

    ///////////////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////////////

    /*private Optional<Address> findMyAddress(String serverId) {
        if (serverMap.containsKey(serverId)) {
            return Optional.of(serverMap.get(serverId).getAddress());
        } else if (!serverMap.isEmpty()) {
            return serverMap.values().stream().map(Server::getAddress).findAny();
        } else {
            return Optional.empty();
        }
    }*/


    private void onDisconnect(Connection connection) {
        if (connection instanceof InboundConnection) {
            inboundConnections.remove(connection);
        } else if (connection instanceof OutboundConnection outboundConnection) {
            outboundConnectionMap.remove(outboundConnection.getAddress());
        }
        runAsync(() -> connectionListeners.forEach(listener -> listener.onDisconnect(connection)));
    }

    private void handleException(Throwable exception) {
        if (isStopped) {
            return;
        }
        if (exception instanceof EOFException) {
            // log.debug(exception.toString(), exception);
        } else if (exception instanceof SocketException) {
            // log.debug(exception.toString(), exception);
        } else {
            log.error(exception.toString(), exception);
        }
    }

    private void handleException(Connection connection, Throwable exception) {
        if (isStopped) {
            return;
        }
        handleException(exception);
        onDisconnect(connection);
    }


    private Transport getNetworkProxy(Transport.Type transportType, Transport.Config config) {
        return switch (transportType) {
            case TOR -> TorTransport.getInstance(config);
            case I2P -> I2PTransport.getInstance(config);
            case CLEAR_NET -> ClearNetTransport.getInstance(config);
        };
    }

    public Optional<Address> getMyAddress() {
        return server.map(Server::getAddress);
    }
}
