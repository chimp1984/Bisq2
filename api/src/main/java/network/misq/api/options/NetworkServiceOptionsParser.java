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

package network.misq.api.options;

import lombok.Getter;
import network.misq.application.options.ApplicationOptions;
import network.misq.network.NetworkService;
import network.misq.network.p2p.P2pServiceNode;
import network.misq.network.p2p.node.Address;
import network.misq.network.p2p.node.proxy.NetworkType;
import network.misq.network.p2p.services.mesh.MeshService;
import network.misq.network.p2p.services.mesh.peers.PeerConfig;
import network.misq.network.p2p.services.mesh.peers.exchange.PeerExchangeConfig;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Parses the program arguments which are relevant for that domain and stores it in the options field.
 */
public class NetworkServiceOptionsParser {
    @Getter
    private final NetworkService.Config config;

    public NetworkServiceOptionsParser(ApplicationOptions applicationOptions, String[] args) {
        String baseDirPath = applicationOptions.appDir();

        Set<NetworkType> supportedNetworkTypes = Set.of(NetworkType.CLEAR, NetworkType.TOR, NetworkType.I2P);

        P2pServiceNode.Config p2pServiceNodeConfig = new P2pServiceNode.Config(Set.of(
                P2pServiceNode.Service.CONFIDENTIAL,
                P2pServiceNode.Service.OVERLAY,
                P2pServiceNode.Service.DATA,
                P2pServiceNode.Service.RELAY));

        List<Address> seedNodes = List.of(Address.localHost(1111));
        PeerConfig peerConfig = new PeerConfig(new PeerExchangeConfig(), seedNodes);
        MeshService.Config overlayServiceConfig = new MeshService.Config(peerConfig);

        config = new NetworkService.Config(baseDirPath,
                supportedNetworkTypes,
                p2pServiceNodeConfig,
                overlayServiceConfig,
                Optional.empty());
    }
}