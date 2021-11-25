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

package network.misq.network.p2p.services.mesh.peers.exchange;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import network.misq.network.p2p.message.Message;
import network.misq.network.p2p.node.Connection;
import network.misq.network.p2p.node.MessageListener;
import network.misq.network.p2p.node.Node;
import network.misq.network.p2p.services.mesh.peers.Peer;

import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Getter
@Slf4j
class PeerExchangeRequestHandler implements MessageListener {
    private static final long TIMEOUT = 90;

    private final Node node;
    private final CompletableFuture<Set<Peer>> future = new CompletableFuture<>();
    private final int nonce;

    PeerExchangeRequestHandler(Node node) {
        this.node = node;
        nonce = new Random().nextInt();
        this.node.addMessageListener(this);
    }

    @Override
    public void onMessage(Message message, Connection connection, String nodeId) {
        if (message instanceof PeerExchangeResponse response) {
            if (response.nonce() == nonce) {
                log.debug("Node {} received PeerExchangeResponse with peers {}",
                        node.getMyAddress(), response.peers());
                future.complete(response.peers());
                node.removeMessageListener(this);
            }
        }
    }

    CompletableFuture<Set<Peer>> request(Connection connection, Set<Peer> peersForPeerExchange) {
        future.orTimeout(TIMEOUT, TimeUnit.SECONDS);
        log.debug("Node {} send PeerExchangeRequest to {} with my peers {}",
                node.getMyAddress(), connection.getPeerAddress(), peersForPeerExchange);
        node.send(new PeerExchangeRequest(nonce, peersForPeerExchange), connection)
                .whenComplete((c, throwable) -> {
                    if (throwable != null) {
                        future.completeExceptionally(throwable);
                        dispose();
                    }
                });
        return future;
    }

    void dispose() {
        node.removeMessageListener(this);
        future.cancel(true);
    }
}