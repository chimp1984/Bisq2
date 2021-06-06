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


import com.google.common.annotations.VisibleForTesting;
import network.misq.network.confidential.ConfidentialMessageService;
import network.misq.network.data.DataService;
import network.misq.network.data.filter.DataFilter;
import network.misq.network.data.inventory.RequestInventoryResult;
import network.misq.network.data.storage.Storage;
import network.misq.network.message.Message;
import network.misq.network.node.Connection;
import network.misq.network.node.MessageListener;
import network.misq.network.node.Node;
import network.misq.network.node.proxy.GetServerSocketResult;
import network.misq.network.peers.PeerConfig;
import network.misq.network.peers.PeerGroup;
import network.misq.network.peers.PeerManager;
import network.misq.network.peers.exchange.DefaultPeerExchangeStrategy;
import network.misq.network.router.gossip.GossipResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * High level API for the p2p network.
 */
public class NetworkNode {
    private static final Logger log = LoggerFactory.getLogger(NetworkNode.class);

    private final NetworkConfig networkConfig;
    private final Storage storage;
    private final KeyPairRepository keyPairRepository;
    private final PeerManager peerManager;
    private final Node node;
    private final ConfidentialMessageService confidentialMessageService;
    private final DataService dataService;

    public NetworkNode(NetworkConfig networkConfig, Storage storage, KeyPairRepository keyPairRepository) {
        this.networkConfig = networkConfig;
        this.storage = storage;
        this.keyPairRepository = keyPairRepository;

        node = new Node(networkConfig);

        PeerConfig peerConfig = networkConfig.getPeerConfig();
        PeerGroup peerGroup = new PeerGroup(node, peerConfig, networkConfig.getNodeId().getServerPort());
        DefaultPeerExchangeStrategy peerExchangeStrategy = new DefaultPeerExchangeStrategy(peerGroup, peerConfig);
        peerManager = new PeerManager(node, peerGroup, peerExchangeStrategy, peerConfig);

        confidentialMessageService = new ConfidentialMessageService(node, peerGroup, keyPairRepository);

        dataService = new DataService(node, peerGroup, storage);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////////////

    public CompletableFuture<GetServerSocketResult> initializeServer() {
        return node.initializeServer(networkConfig.getNodeId().getId(), networkConfig.getNodeId().getServerPort());
    }

    public CompletableFuture<Boolean> bootstrap() {
        return peerManager.bootstrap(networkConfig.getNodeId().getId(), networkConfig.getNodeId().getServerPort());
    }

    public CompletableFuture<Connection> confidentialSend(Message message, NetworkId networkId, KeyPair myKeyPair)
            throws GeneralSecurityException {
        return confidentialMessageService.send(message, networkId, myKeyPair);
    }

    public CompletableFuture<Connection> relay(Message message, NetworkId networkId, KeyPair myKeyPair) {
        return confidentialMessageService.relay(message, networkId, myKeyPair);
    }

    public CompletableFuture<GossipResult> requestAddData(Message message) {
        //  return dataService.requestAddData(message);
        return null;
    }

    public CompletableFuture<GossipResult> requestRemoveData(Message message) {
        return dataService.requestRemoveData(message);
    }

    public CompletableFuture<RequestInventoryResult> requestInventory(DataFilter dataFilter) {
        return dataService.requestInventory(dataFilter);
    }

    public void addMessageListener(MessageListener messageListener) {
        confidentialMessageService.addMessageListener(messageListener);
    }

    public void removeMessageListener(MessageListener messageListener) {
        confidentialMessageService.removeMessageListener(messageListener);
    }

    public void shutdown() {
        dataService.shutdown();
        peerManager.shutdown();
        confidentialMessageService.shutdown();
        node.shutdown();
        storage.shutdown();
    }

    public Optional<Address> findMyAddress() {
        return node.findMyAddress();
    }

    @VisibleForTesting
    public PeerManager getPeerManager() {
        return peerManager;
    }

    public NetworkType getNetworkType() {
        return networkConfig.getNetworkType();
    }
}
