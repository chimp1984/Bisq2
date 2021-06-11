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
import network.misq.network.p2p.Address;
import network.misq.network.p2p.NetworkId;
import network.misq.network.p2p.NetworkType;
import network.misq.network.p2p.P2pService;
import network.misq.network.p2p.message.Message;
import network.misq.network.p2p.node.Connection;
import network.misq.network.p2p.node.MessageListener;
import network.misq.network.p2p.node.proxy.TorNetworkProxy;
import network.misq.security.KeyPairRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.KeyPair;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * High level API for network access to p2p network as well to http services (over Tor). If user has only I2P selected
 * for p2p network a tor instance will be still bootstrapped for usage for the http requests. Only if user has
 * clearNet enabled clearNet is used for https.
 */
public class NetworkService {
    private static final Logger log = LoggerFactory.getLogger(NetworkService.class);
    @Getter
    private final Optional<String> socks5ProxyAddress;
    @Getter
    private final Set<NetworkType> supportedNetworkTypes;

    public Optional<TorNetworkProxy> getTorNetworkProxy() {
        return null;
    }

    public static record Options(P2pService.Option p2pServiceOption, Optional<String> socks5ProxyAddress) {
    }

    private P2pService p2pService;

    public NetworkService(Options options, KeyPairRepository keyPairRepository) {
        socks5ProxyAddress = options.socks5ProxyAddress;
        p2pService = new P2pService(options.p2pServiceOption(), keyPairRepository);
        supportedNetworkTypes = options.p2pServiceOption().networkConfigs().stream().map(networkConfig -> networkConfig.getNetworkType()).collect(Collectors.toSet());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////
    // API P2pService
    ///////////////////////////////////////////////////////////////////////////////////////////////////

    public CompletableFuture<Boolean> initialize() {
        CompletableFuture<Boolean> bootstrap = p2pService.bootstrap();
        // For now we dont want to wait for bootstrap done at startup
        return CompletableFuture.completedFuture(true);
    }

    public CompletableFuture<Connection> confidentialSend(Message message, NetworkId peerNetworkId, KeyPair myKeyPair) {
        CompletableFuture<Connection> future = new CompletableFuture<>();
        p2pService.confidentialSend(message, peerNetworkId, myKeyPair);
        return future;
    }

    public Set<Address> findMyAddresses() {
        return p2pService.findMyAddresses();
    }

    public void addMessageListener(MessageListener messageListener) {
        p2pService.addMessageListener(messageListener);
    }

    public void removeMessageListener(MessageListener messageListener) {
        p2pService.removeMessageListener(messageListener);
    }

    public void shutdown() {
        p2pService.shutdown();
    }
}
