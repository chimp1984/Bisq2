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


import network.misq.network.p2p.message.Message;
import network.misq.network.p2p.node.transport.Transport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.*;

/**
 * Maintains nodes per nodeId.
 * Provides delegate methods to node with given nodeId
 */
public class NodesById implements MessageListener {
    private static final Logger log = LoggerFactory.getLogger(NodesById.class);

    private final Map<String, Node> nodesByNodeId = new ConcurrentHashMap<>();
    private final Node.Config nodeConfig;
    private final Set<MessageListener> messageListeners = new CopyOnWriteArraySet<>();

    public NodesById(Node.Config nodeConfig) {
        this.nodeConfig = nodeConfig;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////////////

    public Node getDefaultNode() {
        return getOrCreateNode(Node.DEFAULT_NODE_ID);
    }

    public CompletableFuture<Connection> send(String nodeId, Message message, Address address) {
        return getOrCreateNode(nodeId).send(message, address);
    }

    public CompletableFuture<Connection> send(String nodeId, Message message, Connection connection) {
        return getOrCreateNode(nodeId).send(message, connection);
    }

    public CompletableFuture<Connection> getConnection(String nodeId, Address address) {
        return getOrCreateNode(nodeId).getConnection(address);
    }

    public CompletableFuture<Transport.ServerSocketResult> initializeServer(String nodeId, int serverPort) {
        return getOrCreateNode(nodeId).initializeServer(serverPort);
    }

    public void addMessageListener(MessageListener listener) {
        messageListeners.add(listener);
    }

    public void removeMessageListener(MessageListener listener) {
        messageListeners.remove(listener);
    }

    public void addMessageListener(String nodeId, MessageListener listener) {
        getOrCreateNode(nodeId).addMessageListener(listener);
    }

    public void removeMessageListener(String nodeId, MessageListener listener) {
        Optional.ofNullable(nodesByNodeId.get(nodeId)).ifPresent(node -> node.removeMessageListener(listener));
    }

    public Optional<Address> getMyAddress(String nodeId) {
        return Optional.ofNullable(nodesByNodeId.get(nodeId)).flatMap(Node::getMyAddress);
    }

    public CompletableFuture<Void> shutdown() {
        CountDownLatch latch = new CountDownLatch(nodesByNodeId.size()); 
        return CompletableFuture.runAsync(() -> {
            nodesByNodeId.values().forEach(node -> node.shutdown().whenComplete((v, t) -> latch.countDown()));

            try {
                latch.await(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                log.error("Shutdown interrupted by timeout");
            }
            nodesByNodeId.clear();
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener
    ///////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMessage(Message message, Connection connection, String nodeId) {
        messageListeners.forEach(messageListener -> messageListener.onMessage(message, connection, nodeId));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////////////

    private Node getOrCreateNode(String nodeId) {
        if (nodesByNodeId.containsKey(nodeId)) {
            return nodesByNodeId.get(nodeId);
        } else {
            Node node = new Node(nodeConfig, nodeId);
            nodesByNodeId.put(nodeId, node);
            node.addMessageListener(this);
            return node;
        }
    }

}
