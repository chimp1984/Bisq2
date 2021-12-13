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
import network.misq.network.p2p.ServiceNode;
import network.misq.network.p2p.node.Address;
import network.misq.network.p2p.node.transport.Transport;
import network.misq.network.p2p.services.mesh.peers.PeerGroup;
import network.misq.network.p2p.services.mesh.peers.SeedNodeRepository;
import network.misq.network.p2p.services.mesh.peers.exchange.PeerExchangeStrategy;

import java.util.*;

/**
 * Parses the program arguments which are relevant for that domain and stores it in the options field.
 */
public class NetworkServiceOptionsParser {
    @Getter
    private final NetworkService.Config config;

    public NetworkServiceOptionsParser(ApplicationOptions applicationOptions, String[] args) {
        String baseDirPath = applicationOptions.appDir();

        Set<Transport.Type> supportedTransportTypes = Set.of(Transport.Type.CLEAR_NET, Transport.Type.TOR, Transport.Type.I2P);

        ServiceNode.Config serviceNodeConfig = new ServiceNode.Config(Set.of(
                ServiceNode.Service.CONFIDENTIAL,
                ServiceNode.Service.MESH,
                ServiceNode.Service.DATA,
                ServiceNode.Service.RELAY,
                ServiceNode.Service.MONITOR));

        PeerGroup.Config peerGroupConfig = new PeerGroup.Config();
        PeerExchangeStrategy.Config peerExchangeStrategyConfig = new PeerExchangeStrategy.Config();

        Map<Transport.Type, List<Address>> seedsByTransportType = Map.of(Transport.Type.TOR, Arrays.asList(Address.localHost(1000), Address.localHost(1001)),
                Transport.Type.I2P, Arrays.asList(Address.localHost(1000), Address.localHost(1001)),
                Transport.Type.CLEAR_NET, Arrays.asList(Address.localHost(1000), Address.localHost(1001)));
       
        SeedNodeRepository seedNodeRepository = new SeedNodeRepository(seedsByTransportType);
        Transport.Config transportConfig = new Transport.Config(baseDirPath);
        config = new NetworkService.Config(baseDirPath,
                transportConfig,
                supportedTransportTypes,
                serviceNodeConfig,
                peerGroupConfig,
                peerExchangeStrategyConfig,
                seedNodeRepository,
                Optional.empty());
    }
}