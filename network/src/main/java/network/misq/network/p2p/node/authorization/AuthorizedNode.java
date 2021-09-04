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

package network.misq.network.p2p.node.authorization;


import network.misq.network.p2p.NetworkConfig;
import network.misq.network.p2p.message.Message;
import network.misq.network.p2p.node.MessageListener;
import network.misq.network.p2p.node.capability.CapabilityAwareNode;
import network.misq.network.p2p.node.capability.Connection;
import network.misq.network.p2p.node.capability.ConnectionListener;
import network.misq.network.p2p.node.connection.Address;
import network.misq.network.p2p.node.socket.NetworkType;
import network.misq.network.p2p.node.socket.SocketFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Responsibility:
 * - Creates CapabilityAwareNode
 * - Adds an AccessToken to the outgoing message.
 * - On received messages checks with the permissionControl if the AccessToken is valid.
 * <p>
 * TODO make PermissionControl mocks for BSQ bonded or LN (sphinx) based transport layer to see if other monetary token based ddos
 *      protection strategies work inside the current design
 */
public class AuthorizedNode implements MessageListener, ConnectionListener, Closeable {
    private static final Logger log = LoggerFactory.getLogger(AuthorizedNode.class);

    private final AuthorizationService authorizationService;
    private final Set<MessageListener> messageListeners = new CopyOnWriteArraySet<>();
    private final Set<ConnectionListener> connectionListeners = new CopyOnWriteArraySet<>();
    private final CapabilityAwareNode node;
    private final NetworkConfig networkConfig;
    private volatile boolean isStopped;

    public AuthorizedNode(NetworkConfig networkConfig) {
        node = new CapabilityAwareNode(networkConfig, this, this);
        this.networkConfig = networkConfig;
        authorizationService = new UnrestrictedAuthorizationService();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////
    // ConnectionListener
    ///////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onConnection(Connection connection) {
        connectionListeners.forEach(listener -> listener.onConnection(connection));
    }


    @Override
    public void onDisconnect(Connection connection) {
        connectionListeners.forEach(listener -> listener.onDisconnect(connection));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener
    ///////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMessage(Message message, Connection connection) {
        if (!isStopped && message instanceof AuthorizedMessage authorizedMessage) {
            if (authorizationService.isAuthorized(authorizedMessage)) {
                messageListeners.forEach(listener -> listener.onMessage(authorizedMessage.message(), connection));
            } else {
                log.warn("Handling message at onMessage is not permitted by authorizationService");
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////////////

    public CompletableFuture<Connection> send(Message message, Address peerAddress) {
        return node.getConnection(peerAddress)
                .thenCompose(connection -> send(message, connection));
    }

    public CompletableFuture<Connection> send(Message message, Connection connection) {
        return authorizationService.createToken(message)
                .thenCompose(token -> node.send(new AuthorizedMessage(message, token), connection));
    }

    public void close() {
        isStopped = true;
        messageListeners.clear();
        connectionListeners.clear();
        authorizationService.shutdown();
        node.close();
    }

    // Only to be used when we know that we have already created the default server
    public Address getMyAddress() {
        Optional<Address> myAddress = node.findMyAddress();
        checkArgument(myAddress.isPresent(), "myAddress need to be present at a getMyAddress call");
        return myAddress.get();
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


    ///////////////////////////////////////////////////////////////////////////////////////////////////
    // Delegates
    ///////////////////////////////////////////////////////////////////////////////////////////////////

    public CompletableFuture<SocketFactory.GetServerSocketResult> initializeServer(String serverId, int serverPort) {
        return node.initializeServer(serverId, serverPort);
    }

    public Optional<Address> findMyAddress() {
        return node.findMyAddress();
    }

    public CompletableFuture<Connection> getConnection(Address peerAddress) {
        return node.getConnection(peerAddress);
    }

    public Optional<Connection> findConnection(Address peerAddress) {
        return node.findConnection(peerAddress);
    }

    public NetworkType getNetworkType() {
        return networkConfig.getNetworkType();
    }

    public SocketFactory getSocketFactory() {
        return node.getSocketFactory();
    }
}
