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

package network.misq.network.p2p.services.mesh.monitor;

import lombok.Getter;
import lombok.Setter;
import network.misq.common.util.OsUtils;
import network.misq.network.NetworkService;
import network.misq.network.p2p.ServiceNode;
import network.misq.network.p2p.node.Address;
import network.misq.network.p2p.node.transport.Transport;
import network.misq.network.p2p.services.mesh.peers.PeerGroup;
import network.misq.network.p2p.services.mesh.peers.SeedNodeRepository;
import network.misq.network.p2p.services.mesh.peers.exchange.PeerExchangeStrategy;
import network.misq.security.KeyPairRepository;

import java.io.File;
import java.util.*;

public class NetworkMonitor {
    private final Set<Transport.Type> supportedTransportTypes;
    private final ServiceNode.Config serviceNodeConfig;
    private final String baseDirPath;
    private final KeyPairRepository keyPairRepository;


    @Setter
    @Getter
    private int numSeeds = 1;
    @Setter
    @Getter
    private int numNodes = 4;
    private int jitter = 50; // 50%

    public NetworkMonitor() {
        baseDirPath = OsUtils.getUserDataDir() + File.separator + "NetworkMonitor";

        //Set<Transport.Type> supportedTransportTypes = Set.of(Transport.Type.CLEAR_NET, Transport.Type.TOR, Transport.Type.I2P);
        supportedTransportTypes = Set.of(Transport.Type.CLEAR_NET);

        serviceNodeConfig = new ServiceNode.Config(Set.of(
                ServiceNode.Service.CONFIDENTIAL,
                ServiceNode.Service.MESH,
                ServiceNode.Service.DATA,
                ServiceNode.Service.RELAY,
                ServiceNode.Service.MONITOR));

        KeyPairRepository.Conf keyPairRepositoryConf = new KeyPairRepository.Conf(baseDirPath);
        keyPairRepository = new KeyPairRepository(keyPairRepositoryConf);
    }

    public List<Address> getSeedAddresses() {
        List<Address> seedAddresses = new ArrayList<>();
        for (int i = 0; i < numSeeds; i++) {
            seedAddresses.add(Address.localHost(1000 + i));
        }
        return seedAddresses;
    }

    public List<Address> getNodeAddresses() {
        List<Address> addresses = new ArrayList<>();
        for (int i = 0; i < numNodes; i++) {
            addresses.add(Address.localHost(2000 + i));
        }
        return addresses;
    }

    public NetworkService createNetworkService() {
        PeerGroup.Config peerGroupConfig = new PeerGroup.Config(1, 2, 1);
        PeerExchangeStrategy.Config peerExchangeConfig = new PeerExchangeStrategy.Config(1, 40, 20);

        List<Address> seedAddresses = getSeedAddresses();
        Map<Transport.Type, List<Address>> seedsByTransportType = Map.of(Transport.Type.TOR, seedAddresses,
                Transport.Type.I2P, seedAddresses,
                Transport.Type.CLEAR_NET, seedAddresses);

        SeedNodeRepository seedNodeRepository = new SeedNodeRepository(seedsByTransportType);

        NetworkService.Config networkServiceConfig = new NetworkService.Config(baseDirPath,
                supportedTransportTypes,
                serviceNodeConfig,
                peerGroupConfig,
                peerExchangeConfig,
                seedNodeRepository,
                Optional.empty());

        return new NetworkService(networkServiceConfig, keyPairRepository);
    }

    public double getVariance() {
        if (jitter == 0) {
            return 1;
        }
        return 1d - new Random().nextInt(jitter) / 100d;
    }
}