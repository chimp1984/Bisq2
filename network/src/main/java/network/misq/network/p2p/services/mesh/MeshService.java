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
import network.misq.network.p2p.node.Node;
import network.misq.network.p2p.services.mesh.peers.PeerConfig;
import network.misq.network.p2p.services.mesh.peers.PeerGroup;
import network.misq.network.p2p.services.mesh.peers.PeerManager;
import network.misq.network.p2p.services.mesh.peers.exchange.DefaultPeerExchangeStrategy;

import java.util.concurrent.CompletableFuture;


@Getter
public class MeshService {
    public static record Config(PeerConfig peerConfig) {
    }

    private final PeerManager peerManager;
    private final PeerGroup peerGroup;

    public MeshService(Node node, Config config) {
        PeerConfig peerConfig = config.peerConfig();
        peerGroup = new PeerGroup(node, peerConfig);
        DefaultPeerExchangeStrategy peerExchangeStrategy = new DefaultPeerExchangeStrategy(peerGroup, peerConfig);
        peerManager = new PeerManager(node, peerGroup, peerExchangeStrategy, peerConfig);

    }

    public CompletableFuture<Boolean> bootstrap() {
        return peerManager.bootstrap();
    }

    public CompletableFuture<Void> shutdown() {
        return peerManager.shutdown();
    }
}