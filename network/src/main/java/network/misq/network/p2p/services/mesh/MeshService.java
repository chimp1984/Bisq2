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
import network.misq.network.p2p.services.mesh.peers.PeerGroup;
import network.misq.network.p2p.services.mesh.peers.PeerGroupHealth;
import network.misq.network.p2p.services.mesh.peers.PeerGroupService;
import network.misq.network.p2p.services.mesh.peers.exchange.PeerExchangeService;
import network.misq.network.p2p.services.mesh.peers.exchange.PeerExchangeStrategy;
import network.misq.network.p2p.services.mesh.peers.exchange.PeerExchangeConfig;

import java.util.List;
import java.util.concurrent.CompletableFuture;


@Getter
public class MeshService {
    private final PeerGroup peerGroup;
    private final PeerGroupService peerGroupService;

    public static record Config(PeerExchangeConfig peerExchangeConfig, List<Address> seedNodeAddresses) {
    }

    public MeshService(Node node, Config config) {
        List<Address> seedNodeAddresses = config.seedNodeAddresses();
        PeerGroup.Config peerGroupConfig = new PeerGroup.Config();
        peerGroup = new PeerGroup(node, peerGroupConfig);
        PeerExchangeStrategy peerExchangeStrategy = new PeerExchangeStrategy(peerGroup, seedNodeAddresses, config.peerExchangeConfig());
        PeerExchangeService peerExchangeService = new PeerExchangeService(node, peerExchangeStrategy);
        PeerGroupHealth peerGroupHealth = new PeerGroupHealth(node, peerGroup);
        peerGroupService = new PeerGroupService(peerGroup, peerGroupHealth, peerExchangeService);
    }

    public CompletableFuture<Boolean> initialize() {
        return peerGroupService.initialize();
    }

    public CompletableFuture<Void> shutdown() {
        return peerGroupService.shutdown();
    }
}