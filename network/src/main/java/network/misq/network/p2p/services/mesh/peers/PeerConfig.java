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

import lombok.Getter;
import network.misq.network.p2p.node.Address;
import network.misq.network.p2p.services.mesh.peers.exchange.old.PeerExchangeConfig;

import java.util.List;

@Getter
public class PeerConfig {
    private final PeerExchangeConfig peerExchangeConfig;
    private final List<Address> seedNodeAddresses;
    private final int minNumConnectedPeers;
    private final int maxNumConnectedPeers;
    private final int minNumReportedPeers;

    public PeerConfig(PeerExchangeConfig peerExchangeConfig, List<Address> seedNodeAddresses) {
        this(peerExchangeConfig, seedNodeAddresses, 8, 12, 1);
    }

    public PeerConfig(PeerExchangeConfig peerExchangeConfig,
                      List<Address> seedNodeAddresses,
                      int minNumConnectedPeers,
                      int maxNumConnectedPeers,
                      int minNumReportedPeers) {
        this.peerExchangeConfig = peerExchangeConfig;
        this.seedNodeAddresses = seedNodeAddresses;
        this.minNumConnectedPeers = minNumConnectedPeers;
        this.maxNumConnectedPeers = maxNumConnectedPeers;
        this.minNumReportedPeers = minNumReportedPeers;
    }
}
