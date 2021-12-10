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

package network.misq.network.p2p.services.mesh.peers;

import lombok.extern.slf4j.Slf4j;
import network.misq.network.p2p.services.mesh.peers.exchange.PeerExchangeService;

import java.util.concurrent.CompletableFuture;

/**
 * Coordinating different specialized managers for bootstrapping into the network and maintaining a healthy
 * group of neighbor peers.
 * <p>
 * It starts by bootstrapping the network of neighbor peers using the PeerExchange which provides information of
 * other available peers in the network.
 * After the initial network of mostly outbound connections has been created we switch strategy to maintain a preferred
 * composition of peers managed in PeerGroupHealth.
 * <p>
 * <p>
 * Strategy (using default numbers from config):
 * 1. Bootstrap
 * Use 2 seed nodes and 8 persisted nodes (using PeerExchangeSelection) for the exchange protocol
 * - Send our reported and connected peers in the exchange protocol (using PeerExchangeSelection) and add the
 * ones reported back from the peer to our reported peers.
 * - Once a connection is established the peer gets added to connected peers inside peer group
 * 2. Repeat until sufficient connections are reached
 * Once the CompletableFuture from PeerExchange.bootstrap completes we check how many connection attempts have led to successful
 * connections.
 * - If we have reached our target of 8 connections we stop and return true to the calling client.
 * - If we did not reach our target we repeat after a delay the exchange protocol using reported peers
 * (using PeerExchangeSelection).
 */
@Slf4j
public class PeerGroupService {

    private final PeerGroupHealth peerGroupHealth;
    private final PeerExchangeService peerExchangeService;
    private volatile boolean isStopped;

    public PeerGroupService(PeerGroupHealth peerGroupHealth,
                            PeerExchangeService peerExchangeService) {
        this.peerGroupHealth = peerGroupHealth;
        this.peerExchangeService = peerExchangeService;
    }

    public CompletableFuture<Boolean> initialize() {
        return peerExchangeService.startPeerExchange()
                .thenCompose(repeatBootstrap -> {
                    if (repeatBootstrap) {
                        // If we did not have sufficient successful requests we repeat with a delay.
                        peerExchangeService.repeatPeerExchangeWithDelay();
                    }
                    return peerGroupHealth.initialize();
                });
    }

    public void shutdown() {
        if (isStopped) {
            return;
        }
        isStopped = true;
        peerGroupHealth.shutdown();
        peerExchangeService.shutdown();
    }
}
