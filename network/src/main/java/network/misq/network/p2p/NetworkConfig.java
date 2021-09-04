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

package network.misq.network.p2p;

import lombok.Getter;
import network.misq.network.p2p.node.socket.NetworkType;
import network.misq.network.p2p.node.socket.NodeId;
import network.misq.network.p2p.services.overlay.discovery.SeedNodeRepository;
import network.misq.network.p2p.services.overlay.peers.PeerConfig;
import network.misq.network.p2p.services.overlay.peers.exchange.PeerExchangeConfig;

import java.util.Set;

@Getter
public class NetworkConfig {
    public enum Service {
        OVERLAY,
        DATA,
        CONFIDENTIAL,
        RELAY
    }

    private final String baseDirPath;
    private final NodeId nodeId;
    private final NetworkType networkType;
    private final PeerConfig peerConfig;
    private final Set<Service> services;

    public NetworkConfig(String baseDirPath, NodeId nodeId, NetworkType networkType) {
        this(baseDirPath,
                nodeId,
                networkType,
                new PeerConfig(new PeerExchangeConfig(), new SeedNodeRepository().getNodes(networkType)),
                Set.of(Service.OVERLAY, Service.DATA, Service.CONFIDENTIAL, Service.RELAY));
    }

    public NetworkConfig(String baseDirPath,
                         NodeId nodeId,
                         NetworkType networkType,
                         PeerConfig peerConfig,
                         Set<Service> services) {
        this.baseDirPath = baseDirPath;
        this.nodeId = nodeId;
        this.networkType = networkType;
        this.peerConfig = peerConfig;
        this.services = services;
    }
}
