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

package network.misq.network.p2p.services.peergroup.exchange;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import network.misq.common.util.StringUtils;
import network.misq.network.p2p.message.Message;
import network.misq.network.p2p.node.Connection;
import network.misq.network.p2p.node.Node;
import network.misq.network.p2p.services.peergroup.Peer;

import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Getter
@Slf4j
class PeerExchangeRequestHandler implements Connection.Listener {
    private static final long TIMEOUT = 90;

    private final Node node;
    private final Connection connection;
    private final CompletableFuture<Set<Peer>> future = new CompletableFuture<>();
    private final int nonce;

    PeerExchangeRequestHandler(Node node, Connection connection ) {
        this.node = node;
        this.connection = connection;
        connection.addListener(this);
        nonce = new Random().nextInt();
    }

    CompletableFuture<Set<Peer>> request(Set<Peer> peersForPeerExchange) {
        future.orTimeout(TIMEOUT, TimeUnit.SECONDS);
        log.debug("Node {} send PeerExchangeRequest to {} with my peers {}",
                node, connection.getPeerAddress(), peersForPeerExchange);
        node.send(new PeerExchangeRequest(nonce, peersForPeerExchange), connection)
                .whenComplete((c, throwable) -> {
                    if (throwable != null) {
                        future.completeExceptionally(throwable);
                        dispose();
                    }
                });
        return future;
    }

    @Override
    public void onMessage(Message message) {
        if (message instanceof PeerExchangeResponse response) {
            if (response.nonce() == nonce) {
                String addresses = StringUtils.truncate(response.peers().stream()
                        .map(peer -> peer.getAddress().toString())
                        .collect(Collectors.toList()).toString());
                log.info("Node {} received PeerExchangeResponse from {} with peers: {}",
                        node, connection.getPeerAddress(), addresses);
                removeListeners();
                future.complete( response.peers());
            } else {
                log.warn("Node {} received a PeerExchangeResponse from {} with an invalid nonce. response.nonce()={}, nonce={}",
                        node, connection.getPeerAddress(), response.nonce(), nonce);
            }
        }
    }

    @Override
    public void onConnectionClosed() {
        dispose();
    }

    void dispose() {
        removeListeners();
        future.cancel(true);
    }

    private void removeListeners() {
        connection.removeListener(this);
    }
}