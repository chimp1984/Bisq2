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

package network.misq.network.p2p.services.overlay;

import lombok.Getter;
import network.misq.network.p2p.NetworkConfig;
import network.misq.network.p2p.node.authorization.AuthorizedNode;
import network.misq.network.p2p.services.overlay.peers.PeerConfig;
import network.misq.network.p2p.services.overlay.peers.PeerGroup;
import network.misq.network.p2p.services.overlay.peers.PeerManager;
import network.misq.network.p2p.services.overlay.peers.exchange.DefaultPeerExchangeStrategy;

import java.util.concurrent.CompletableFuture;

@Getter
public class OverlayNetworkService {
    private final PeerManager peerManager;
    private final PeerGroup peerGroup;
    private final NetworkConfig networkConfig;

    public OverlayNetworkService(AuthorizedNode node, NetworkConfig networkConfig) {
        this.networkConfig = networkConfig;
        PeerConfig peerConfig = networkConfig.getPeerConfig();
        peerGroup = new PeerGroup(node, peerConfig, networkConfig.getNodeId().getServerPort());
        DefaultPeerExchangeStrategy peerExchangeStrategy = new DefaultPeerExchangeStrategy(peerGroup, peerConfig);
        peerManager = new PeerManager(node, peerGroup, peerExchangeStrategy, peerConfig);

    }

    public CompletableFuture<Boolean> bootstrap(String id, int serverPort) {
        return peerManager.bootstrap(networkConfig.getNodeId().getId(), networkConfig.getNodeId().getServerPort());
    }

    public void shutdown() {
        peerManager.shutdown();
    }
}