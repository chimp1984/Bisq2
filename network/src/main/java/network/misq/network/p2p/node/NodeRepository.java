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
import network.misq.network.p2p.node.connection.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Maintains nodes per nodeId
 */
public class NodeRepository implements MessageListener {
    private static final Logger log = LoggerFactory.getLogger(NodeRepository.class);

    private final Map<String, Node> nodesByNodeId = new ConcurrentHashMap<>();
    private final NodeConfig nodeConfig;
    private final Set<MessageListener> messageListeners = new CopyOnWriteArraySet<>();

    public NodeRepository(NodeConfig nodeConfig) {
        this.nodeConfig = nodeConfig;
    }

    public Node getOrCreateNode(String nodeId) {
        if (nodesByNodeId.containsKey(nodeId)) {
            return nodesByNodeId.get(nodeId);
        } else {
            Node node = new Node(nodeConfig, nodeId);
            nodesByNodeId.put(nodeId, node);
            node.addMessageListener(this);
            return node;
        }
    }

    @Override
    public void onMessage(Message message, Connection connection) {
        messageListeners.forEach(messageListener -> messageListener.onMessage(message, connection));
    }

    public void addMessageListener(MessageListener listener) {
        messageListeners.add(listener);
    }

    public void removeMessageListener(MessageListener listener) {
        messageListeners.remove(listener);
    }

    public void shutdown() {
        nodesByNodeId.values().forEach(node -> node.removeMessageListener(this));
        nodesByNodeId.values().forEach(Node::shutdown);
        nodesByNodeId.clear();
        messageListeners.clear();
    }
}
