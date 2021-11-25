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
import network.misq.network.p2p.node.transport.Transport;
import network.misq.network.p2p.services.mesh.discovery.SeedNodeRepository;

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

        P2pServiceNode.Config p2pServiceNodeConfig = new P2pServiceNode.Config(Set.of(
                P2pServiceNode.Service.CONFIDENTIAL,
                P2pServiceNode.Service.MESH,
                P2pServiceNode.Service.DATA,
                P2pServiceNode.Service.RELAY));

        List<Address> seedNodes = List.of(Address.localHost(1111));

        Map<Transport.Type, List<Address>> addressByTransportType = Map.of(Transport.Type.TOR, Arrays.asList(Address.localHost(1000), Address.localHost(1001)),
                Transport.Type.I2P, Arrays.asList(Address.localHost(1000), Address.localHost(1001)),
                Transport.Type.CLEAR_NET, Arrays.asList(Address.localHost(1000), Address.localHost(1001)));
        SeedNodeRepository seedNodeRepository = new SeedNodeRepository(addressByTransportType);

        config = new NetworkService.Config(baseDirPath,
                supportedTransportTypes,
                p2pServiceNodeConfig,
                seedNodeRepository,
                Optional.empty());
    }
}