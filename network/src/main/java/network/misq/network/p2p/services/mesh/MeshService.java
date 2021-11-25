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

package network.misq.network.p2p.services.mesh;

import lombok.Getter;
import network.misq.network.p2p.node.Address;
import network.misq.network.p2p.node.Node;
import network.misq.network.p2p.services.mesh.peers.PeerConfig;
import network.misq.network.p2p.services.mesh.peers.PeerGroup;
import network.misq.network.p2p.services.mesh.peers.exchange.PeerExchangeService;
import network.misq.network.p2p.services.mesh.peers.exchange.old.PeerExchangeConfig;

import java.util.List;
import java.util.concurrent.CompletableFuture;


@Getter
public class MeshService {
    private final PeerGroup peerGroup;
    private final PeerExchangeService peerExchangeService;

    public static record Config(PeerExchangeConfig peerExchangeConfig, List<Address> seedNodeAddresses) {
    }

    /* private final PeerManager peerManager;
     */

    public MeshService(Node node, MeshService.Config config) {
        List<Address> seedNodeAddresses = config.seedNodeAddresses();
        PeerConfig peerConfig = new PeerConfig(config.peerExchangeConfig(), seedNodeAddresses);
        peerGroup = new PeerGroup(node, peerConfig);
        peerExchangeService = new PeerExchangeService(node, peerGroup, config.seedNodeAddresses());
    
       /*  DefaultPeerExchangeStrategy peerExchangeStrategy = new DefaultPeerExchangeStrategy(peerGroup, peerConfig);
        peerManager = new PeerManager(node, peerGroup, peerExchangeStrategy, peerConfig);*/
    }

    public CompletableFuture<Boolean> initialize() {
        return peerExchangeService.initialize();
    }

    public CompletableFuture<Void> shutdown() {
        return peerExchangeService.shutdown();
    }
}