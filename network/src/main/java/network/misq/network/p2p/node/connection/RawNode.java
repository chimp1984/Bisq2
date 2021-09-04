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

package network.misq.network.p2p.node.connection;


import network.misq.network.p2p.NetworkConfig;
import network.misq.network.p2p.message.Message;
import network.misq.network.p2p.node.socket.SocketFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Stream;

/**
 * Responsibility:
 * - Creates socketFactory based on networkConfig via SocketFactory factory method.
 * - Creates Servers kept in a map by serverId.
 * - Creates inbound and outbound connections.
 * - Checks if a connection has been created when sending a message and creates one otherwise.
 * - Notifies ConnectionListeners when a new connection has been created or one has been closed.
 */
public class RawNode implements Closeable {
    private static final Logger log = LoggerFactory.getLogger(RawNode.class);
    public static final String DEFAULT_SERVER_ID = "default";
    public static final int DEFAULT_SERVER_PORT = 9999;

    private final SocketFactory socketFactory;
    private final RawConnectionListener rawConnectionListener;
    private final Map<String, Server> serverMap = new ConcurrentHashMap<>();
    private final Map<Address, OutboundConnection> outboundConnectionMap = new ConcurrentHashMap<>();
    private final Set<InboundConnection> inboundConnections = new CopyOnWriteArraySet<>();
    private volatile boolean isStopped;

    public RawNode(NetworkConfig networkConfig, RawConnectionListener rawConnectionListener) {
        socketFactory = SocketFactory.from(networkConfig);
        this.rawConnectionListener = rawConnectionListener;
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

    public CompletableFuture<RawConnection> getOrCreateConnection(Address peerAddress) {
        if (outboundConnectionMap.containsKey(peerAddress)) {
            RawConnection rawConnection = outboundConnectionMap.get(peerAddress);
            return CompletableFuture.completedFuture(rawConnection);
        } else {
            return createConnection(peerAddress);
        }
    }

    public Optional<RawConnection> findConnection(String connectionUid) {
        return Stream.concat(outboundConnectionMap.values().stream(), inboundConnections.stream())
                .filter(e -> e.getId().equals(connectionUid))
                .findAny();
    }

    public void disconnect(RawConnection connection) {
        log.info("disconnect connection {}", connection);
        connection.close();
        onDisconnect(connection);
    }

    /**
     * Sends to outbound connection if available, otherwise create the connection and then send the message.
     * At that layer we do not send to a potentially existing inbound connection as we do not know the peers address
     * at inbound connections. Higher layers can utilize that and use the send(Message message, RawConnection connection)
     * method instead.
     */
    public CompletableFuture<RawConnection> send(Message message, Address address) {
        return getOrCreateConnection(address)
                .thenCompose(connection -> send(message, connection));
    }

    public CompletableFuture<RawConnection> send(Message message, RawConnection connection) {
        return connection.send(message)
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
        outboundConnectionMap.values().forEach(RawConnection::close);
        outboundConnectionMap.clear();
        inboundConnections.forEach(RawConnection::close);
        inboundConnections.clear();

        socketFactory.close();
    }

    public Optional<Address> findMyAddress() {
        return findMyAddress(DEFAULT_SERVER_ID);
    }

    public Optional<Address> findPeerAddress(RawConnection connection) {
        if (connection instanceof OutboundConnection) {
            return Optional.of(((OutboundConnection) connection).getAddress());
        } else {
            return Optional.empty();
        }
    }

    public SocketFactory getSocketFactory() {
        return socketFactory;
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
        try {
            InboundConnection connection = new InboundConnection(socket, getServerSocketResult);
            connection.listen(exception -> handleException(connection, exception));
            inboundConnections.add(connection);
            rawConnectionListener.onConnection(connection);
        } catch (IOException exception) {
            handleException(exception);
        }
    }

    private CompletableFuture<RawConnection> createConnection(Address peerAddress) {
        CompletableFuture<RawConnection> future = new CompletableFuture<>();
        RawConnection rawConnection = null;
        try {
            Socket socket = getSocket(peerAddress);
            log.debug("Create new outbound connection to {}", peerAddress);
            OutboundConnection outboundConnection = new OutboundConnection(socket, peerAddress);
            rawConnection = outboundConnection;
            outboundConnection.listen(exception -> {
                handleException(outboundConnection, exception);
                future.completeExceptionally(exception);
            });

            outboundConnectionMap.put(peerAddress, outboundConnection);
            rawConnectionListener.onConnection(outboundConnection);
            future.complete(outboundConnection);
        } catch (IOException exception) {
            if (rawConnection == null) {
                handleException(exception);
            } else {
                handleException(rawConnection, exception);
            }
            future.completeExceptionally(exception);
        }
        return future;
    }

    private void onDisconnect(RawConnection connection) {
        if (connection instanceof InboundConnection) {
            inboundConnections.remove(connection);
        } else if (connection instanceof OutboundConnection) {
            OutboundConnection outboundConnection = (OutboundConnection) connection;
            Address peerAddress = outboundConnection.getAddress();
            outboundConnectionMap.remove(peerAddress);
        }
        rawConnectionListener.onDisconnect(connection);
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

    private void handleException(RawConnection connection, Throwable exception) {
        if (isStopped) {
            return;
        }
        handleException(exception);
        onDisconnect(connection);
    }
}
