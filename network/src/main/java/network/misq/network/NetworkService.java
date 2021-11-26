/*
 * This file is part of Misq.
 *
 * Misq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Misq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Misq. If not, see <http://www.gnu.org/licenses/>.
 */

package network.misq.network;


import lombok.Getter;
import network.misq.network.http.HttpService;
import network.misq.network.http.common.BaseHttpClient;
import network.misq.network.p2p.NetworkId;
import network.misq.network.p2p.P2pServiceNode;
import network.misq.network.p2p.P2pServiceNodesByTransportType;
import network.misq.network.p2p.message.Message;
import network.misq.network.p2p.node.Connection;
import network.misq.network.p2p.node.MessageListener;
import network.misq.network.p2p.node.transport.Transport;
import network.misq.network.p2p.services.data.DataService;
import network.misq.network.p2p.services.mesh.peers.SeedNodeRepository;
import network.misq.security.KeyPairRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.KeyPair;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * High level API for network access to p2p network as well to http services (over Tor). If user has only I2P selected
 * for p2p network a tor instance will be still bootstrapped for usage for the http requests. Only if user has
 * clearNet enabled clearNet is used for https.
 */
public class NetworkService {
    private static final Logger log = LoggerFactory.getLogger(NetworkService.class);

    public static record Config(String baseDirPath,
                                Set<Transport.Type> supportedTransportTypes,
                                P2pServiceNode.Config p2pServiceNodeConfig,
                                SeedNodeRepository seedNodeRepository,
                                Optional<String> socks5ProxyAddress) {
    }

    @Getter
    private final HttpService httpService;
    @Getter
    private final Optional<String> socks5ProxyAddress; // Optional proxy address of external tor instance 
    @Getter
    private final Set<Transport.Type> supportedTransportTypes;
    private final P2pServiceNodesByTransportType p2pService;

    public NetworkService(Config config, KeyPairRepository keyPairRepository) {
        httpService = new HttpService();
        socks5ProxyAddress = config.socks5ProxyAddress;
        supportedTransportTypes = config.supportedTransportTypes();
        p2pService = new P2pServiceNodesByTransportType(config.baseDirPath(),
                supportedTransportTypes,
                config.p2pServiceNodeConfig(),
                config.seedNodeRepository(),
                new DataService.Config(config.baseDirPath()),
                keyPairRepository);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////
    // API P2pService
    ///////////////////////////////////////////////////////////////////////////////////////////////////

    public CompletableFuture<Boolean> initialize() {
        CompletableFuture<Boolean> bootstrap = p2pService.initializeMesh();
        // For now we dont want to wait for bootstrap done at startup
        // return CompletableFuture.completedFuture(true);
        return bootstrap;
    }

    public CompletableFuture<Connection> confidentialSend(Message message, NetworkId peerNetworkId, KeyPair myKeyPair, String connectionId) {
        return p2pService.confidentialSend(message, peerNetworkId, myKeyPair, connectionId);
    }

    public void addMessageListener(MessageListener messageListener) {
        p2pService.addMessageListener(messageListener);
    }

    public void removeMessageListener(MessageListener messageListener) {
        p2pService.removeMessageListener(messageListener);
    }


    public CompletableFuture<BaseHttpClient> getHttpClient(String url, String userAgent, Transport.Type transportType) {
        return httpService.getHttpClient(url, userAgent, transportType, p2pService.getSocksProxy(), socks5ProxyAddress);
    }

    public CompletableFuture<Void> shutdown() {
        CountDownLatch latch = new CountDownLatch(2);
        return CompletableFuture.runAsync(() -> {
            p2pService.shutdown().whenComplete((v, t) -> latch.countDown());
            httpService.shutdown().whenComplete((v, t) -> latch.countDown());
            try {
                latch.await(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                log.error("Shutdown interrupted by timeout");
            }
        });
    }

    
       /*public Set<Address> findMyAddresses() {
        return p2pService.findMyAddresses();
    }*/

}
