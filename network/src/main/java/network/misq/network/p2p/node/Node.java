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


import network.misq.network.p2p.NetworkConfig;
import network.misq.network.p2p.message.Message;
import network.misq.network.p2p.node.connection.Connection;
import network.misq.network.p2p.node.connection.ConnectionHandshake;
import network.misq.network.p2p.node.connection.InboundConnection;
import network.misq.network.p2p.node.connection.OutboundConnection;
import network.misq.network.p2p.node.connection.authorization.AuthorizationService;
import network.misq.network.p2p.node.connection.authorization.AuthorizedMessage;
import network.misq.network.p2p.node.connection.authorization.UnrestrictedAuthorizationService;
import network.misq.network.p2p.node.socket.NetworkType;
import network.misq.network.p2p.node.socket.NodeId;
import network.misq.network.p2p.node.socket.SocketFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Stream;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Responsibility:
 * - Creates socketFactory based on networkConfig via SocketFactory factory method.
 * - Creates Servers kept in a map by serverId.
 * - Creates inbound and outbound connections.
 * - Checks if a connection has been created when sending a message and creates one otherwise.
 * - Notifies ConnectionListeners when a new connection has been created or one has been closed.
 */
public class Node implements MessageListener, Closeable {
    private static final Logger log = LoggerFactory.getLogger(Node.class);
    public static final String DEFAULT_SERVER_ID = "default";
    public static final int DEFAULT_SERVER_PORT = 9999;

    private final SocketFactory socketFactory;
    private final Map<String, Server> serverMap = new ConcurrentHashMap<>();
    private final Map<Address, OutboundConnection> outboundConnectionMap = new ConcurrentHashMap<>();
    private final Set<InboundConnection> inboundConnections = new CopyOnWriteArraySet<>();
    private final AuthorizationService authorizationService;
    private final Set<NetworkType> supportedNetworkTypes;
    private final NetworkType networkType;
    private final NodeId nodeId;
    private final NetworkConfig networkConfig;
    private volatile boolean isStopped;
    private final Set<MessageListener> messageListeners = new CopyOnWriteArraySet<>();
    private final Set<ConnectionListener> connectionListeners = new CopyOnWriteArraySet<>();

    public Node(NetworkConfig networkConfig) {
        socketFactory = SocketFactory.from(networkConfig);
        supportedNetworkTypes = networkConfig.getNodeId().getNetworkTypes();
        networkType = networkConfig.getNetworkType();
        nodeId = networkConfig.getNodeId();
        this.networkConfig = networkConfig;
        authorizationService = new UnrestrictedAuthorizationService();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////////////

    public CompletableFuture<SocketFactory.GetServerSocketResult> initializeServer(String serverId, int serverPort) {
        return socketFactory.initialize()
                .thenCompose(e -> createServerAndListen(serverId, serverPort));
    }

    public CompletableFuture<SocketFactory.GetServerSocketResult> createServerAndListen(String serverId, int serverPort) {
        return socketFactory.getServerSocket(serverId, serverPort)
                .thenCompose(result -> {
                    Server server = new Server(result,
                            socket -> onClientSocket(socket, result),
                            exception -> {
                                serverMap.remove(serverId);
                                handleException(exception);
                            });
                    serverMap.put(serverId, server);
                    return CompletableFuture.completedFuture(result);
                });
    }

    public CompletableFuture<Connection> getConnection(Address peerAddress) {
        if (outboundConnectionMap.containsKey(peerAddress)) {
            Connection connection = outboundConnectionMap.get(peerAddress);
            return CompletableFuture.completedFuture(connection);
        } else {
            return createConnection(peerAddress);
        }
    }

    public Optional<Connection> findConnection(String connectionUid) {
        return getAllConnections()
                .filter(e -> e.getId().equals(connectionUid))
                .findAny();
    }

    private Stream<Connection> getAllConnections() {
        return Stream.concat(outboundConnectionMap.values().stream(), inboundConnections.stream());
    }

    //todo
    public Optional<Connection> findConnection(Address peerAddress) {
        return getAllConnections()
                .filter(c -> c.getPeerAddress().equals(peerAddress))
                .findAny();
    }

    public void disconnect(Connection connection) {
        log.info("disconnect connection {}", connection);
        connection.close();
        onDisconnect(connection);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener
    ///////////////////////////////////////////////////////////////////////////////////////////////////

    public void onMessage(Message message, Connection connection) {
        if (!isStopped && message instanceof AuthorizedMessage authorizedMessage) {
            if (authorizationService.isAuthorized(authorizedMessage)) {
                messageListeners.forEach(listener -> listener.onMessage(authorizedMessage.message(), connection));
            } else {
                log.warn("Handling message at onMessage is not permitted by authorizationService");
            }
        }
    }


    /**
     * Sends to outbound connection if available, otherwise create the connection and then send the message.
     * At that layer we do not send to a potentially existing inbound connection as we do not know the peers address
     * at inbound connections. Higher layers can utilize that and use the send(Message message, RawConnection connection)
     * method instead.
     */
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

    public void close() {
        if (isStopped) {
            return;
        }
        isStopped = true;

        serverMap.values().forEach(Server::close);
        serverMap.clear();
        outboundConnectionMap.values().forEach(Connection::close);
        outboundConnectionMap.clear();
        inboundConnections.forEach(Connection::close);
        inboundConnections.clear();

        socketFactory.close();
    }

    public Optional<Address> findMyAddress() {
        return findMyAddress(DEFAULT_SERVER_ID);
    }

    public Optional<Address> findPeerAddress(Connection connection) {
        if (connection instanceof OutboundConnection) {
            return Optional.of(((OutboundConnection) connection).getAddress());
        } else {
            return Optional.empty();
        }
    }

    public SocketFactory getSocketFactory() {
        return socketFactory;
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
    public Address getMyAddress() {
        Optional<Address> myAddress = findMyAddress();
        checkArgument(myAddress.isPresent(), "myAddress need to be present at a getMyAddress call");
        return myAddress.get();
    }

    public NetworkType getNetworkType() {
        return networkConfig.getNetworkType();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////////////

    private Socket getSocket(Address address) throws IOException {
        return socketFactory.getSocket(address);
    }

    private Optional<Address> findMyAddress(String serverId) {
        if (serverMap.containsKey(serverId)) {
            return Optional.of(serverMap.get(serverId).getAddress());
        } else if (!serverMap.isEmpty()) {
            return serverMap.values().stream().map(Server::getAddress).findAny();
        } else {
            return Optional.empty();
        }
    }

    private void onClientSocket(Socket socket, SocketFactory.GetServerSocketResult getServerSocketResult) {
        Capability myCapability = new Capability(getServerSocketResult.address(), supportedNetworkTypes);
        ConnectionHandshake.onSocket(socket, myCapability, authorizationService)
                .whenComplete((peersCapability, throwable) -> {
                    if (throwable == null) {
                        try {
                            //todo use peersCapability
                            InboundConnection connection = new InboundConnection(socket, getServerSocketResult, peersCapability, this);
                            connection.startListen(exception -> handleException(connection, exception));
                            inboundConnections.add(connection);
                            connectionListeners.forEach(listener -> listener.onConnection(connection));
                        } catch (IOException exception) {
                            handleException(exception);
                        }
                    } else {
                        handleException(throwable);
                    }
                });
    }

    private CompletableFuture<Connection> createConnection(Address peerAddress) {

        Socket socket;
        try {
            socket = getSocket(peerAddress);
        } catch (IOException e) {
            handleException(e);
            return CompletableFuture.failedFuture(e);
        }
        CompletableFuture<Connection> future = new CompletableFuture<>();

        //todo associated server address
        Address myServerAddress = new ArrayList<>(serverMap.values()).get(0).getAddress();

        Capability myCapability = new Capability(myServerAddress, supportedNetworkTypes);
        ConnectionHandshake.connect(socket, myCapability, authorizationService)
                .whenComplete((peersCapability, throwable) -> {
                    log.debug("Create new outbound connection to {}", peerAddress);
                    checkArgument(peerAddress.equals(peersCapability.address()), 
                            "Peers reported address must match address we used to connect");
                    OutboundConnection connection = new OutboundConnection(socket, peerAddress, peersCapability, this);
                    try {
                        connection.startListen(exception -> {
                            handleException(connection, exception);
                            future.completeExceptionally(exception);
                        });

                        outboundConnectionMap.put(peerAddress, connection);
                        connectionListeners.forEach(listener -> listener.onConnection(connection));
                        future.complete(connection);
                    } catch (IOException exception) {
                        handleException(connection, exception);
                        future.completeExceptionally(exception);
                    }
                });
        return future;
    }

    private void onDisconnect(Connection connection) {
        if (connection instanceof InboundConnection) {
            inboundConnections.remove(connection);
        } else if (connection instanceof OutboundConnection outboundConnection) {
            Address peerAddress = outboundConnection.getAddress();
            outboundConnectionMap.remove(peerAddress);
        }
        connectionListeners.forEach(listener -> listener.onDisconnect(connection));
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
}
