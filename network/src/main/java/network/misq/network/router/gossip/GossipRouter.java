/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package network.misq.network.router.gossip;

import network.misq.common.util.CollectionUtil;
import network.misq.network.Address;
import network.misq.network.message.Message;
import network.misq.network.node.Connection;
import network.misq.network.node.MessageListener;
import network.misq.network.node.Node;
import network.misq.network.peers.PeerGroup;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Responsibility:
 * - Creates PeerGroup for peer management
 * - Broadcasts messages to peers provided by PeerGroup
 * - Notifies MessageListeners on messages which have been sent by via a GossipMessage
 */
public class GossipRouter implements MessageListener {
    private static final long BROADCAST_TIMEOUT = 90;

    private final Node node;
    private final PeerGroup peerGroup;
    private final Set<MessageListener> messageListeners = new CopyOnWriteArraySet<>();

    public GossipRouter(Node node, PeerGroup peerGroup) {
        this.node = node;
        this.peerGroup = peerGroup;

        node.addMessageListener(this);
    }

    @Override
    public void onMessage(Message message, Connection connection) {
        if (message instanceof GossipMessage) {
            GossipMessage gossipMessage = (GossipMessage) message;
            messageListeners.forEach(listener -> listener.onMessage(gossipMessage.getMessage(), connection));
        }
    }

    public CompletableFuture<GossipResult> broadcast(Message message) {
        long ts = System.currentTimeMillis();
        CompletableFuture<GossipResult> future = new CompletableFuture<>();
        future.orTimeout(BROADCAST_TIMEOUT, TimeUnit.SECONDS);
        AtomicInteger numSuccess = new AtomicInteger(0);
        AtomicInteger numFaults = new AtomicInteger(0);
        Set<Address> connectedPeerAddresses = peerGroup.getConnectedPeerAddresses();
        int target = connectedPeerAddresses.size();
        connectedPeerAddresses.forEach(address -> {
            node.send(new GossipMessage(message), address)
                    .whenComplete((connection, t) -> {
                        if (connection != null) {
                            numSuccess.incrementAndGet();
                        } else {
                            numFaults.incrementAndGet();
                        }
                        if (numSuccess.get() + numFaults.get() == target) {
                            future.complete(new GossipResult(numSuccess.get(),
                                    numFaults.get(),
                                    System.currentTimeMillis() - ts));
                        }
                    });
        });
        return future;
    }

    public Address getPeerAddressesForInventoryRequest() {
        return CollectionUtil.getRandomElement(peerGroup.getConnectedPeerAddresses());
    }

    public void addMessageListener(MessageListener messageListener) {
        messageListeners.add(messageListener);
    }

    public void removeMessageListener(MessageListener messageListener) {
        messageListeners.remove(messageListener);
    }

    public void shutdown() {
        messageListeners.clear();

        node.removeMessageListener(this);
    }
}
