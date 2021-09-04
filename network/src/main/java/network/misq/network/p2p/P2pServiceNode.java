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

package network.misq.network.p2p;


import network.misq.network.p2p.message.Message;
import network.misq.network.p2p.node.MessageListener;
import network.misq.network.p2p.node.authorization.AuthorizedNode;
import network.misq.network.p2p.node.capability.Connection;
import network.misq.network.p2p.node.connection.Address;
import network.misq.network.p2p.node.socket.NetworkType;
import network.misq.network.p2p.node.socket.SocketFactory;
import network.misq.network.p2p.services.confidential.ConfidentialMessageService;
import network.misq.network.p2p.services.data.DataService;
import network.misq.network.p2p.services.data.filter.DataFilter;
import network.misq.network.p2p.services.data.inventory.RequestInventoryResult;
import network.misq.network.p2p.services.data.storage.Storage;
import network.misq.network.p2p.services.overlay.OverlayNetworkService;
import network.misq.network.p2p.services.overlay.router.gossip.GossipResult;
import network.misq.network.p2p.services.relay.RelayService;
import network.misq.security.KeyPairRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * P2P node supporting services defined in NetworkConfig.
 */
public class P2pServiceNode {
    private static final Logger log = LoggerFactory.getLogger(P2pServiceNode.class);

    private final NetworkConfig networkConfig;
    private final Storage storage;
    private final AuthorizedNode node;
    private Optional<ConfidentialMessageService> confidentialMessageService;
    private Optional<DataService> dataService;
    private Optional<OverlayNetworkService> overlayNetworkService;
    private Optional<RelayService> relayService;

    public P2pServiceNode(NetworkConfig networkConfig, Storage storage, KeyPairRepository keyPairRepository) {
        this.networkConfig = networkConfig;
        this.storage = storage;

        node = new AuthorizedNode(networkConfig);
        Set<NetworkConfig.Service> services = networkConfig.getServices();
        if (services.contains(NetworkConfig.Service.CONFIDENTIAL)) {
            confidentialMessageService = Optional.of(new ConfidentialMessageService(node, keyPairRepository));
        }
        if (services.contains(NetworkConfig.Service.RELAY)) {
            relayService = Optional.of(new RelayService(node));
        }
        if (services.contains(NetworkConfig.Service.OVERLAY)) {
            overlayNetworkService = Optional.of(new OverlayNetworkService(node, networkConfig));

            if (services.contains(NetworkConfig.Service.DATA)) {
                dataService = Optional.of(new DataService(node, overlayNetworkService.get(), storage));
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////////////

    public CompletableFuture<SocketFactory.GetServerSocketResult> initializeServer() {
        return node.initializeServer(networkConfig.getNodeId().getId(), networkConfig.getNodeId().getServerPort());
    }

    public CompletableFuture<Boolean> initializeOverlay() {
        checkArgument(overlayNetworkService.isPresent());
        return overlayNetworkService.get().bootstrap(networkConfig.getNodeId().getId(), networkConfig.getNodeId().getServerPort());
    }

    public CompletableFuture<Connection> confidentialSend(Message message, NetworkId networkId, KeyPair myKeyPair)
            throws GeneralSecurityException {
        checkArgument(confidentialMessageService.isPresent());
        return confidentialMessageService.get().send(message, networkId, myKeyPair);
    }

    public CompletableFuture<Connection> relay(Message message, NetworkId networkId, KeyPair myKeyPair) {
        checkArgument(confidentialMessageService.isPresent());
        return confidentialMessageService.get().relay(message, networkId, myKeyPair);
    }

    public CompletableFuture<GossipResult> requestAddData(Message message) {
        //  return dataService.requestAddData(message);
        return null;
    }

    public CompletableFuture<GossipResult> requestRemoveData(Message message) {
        checkArgument(dataService.isPresent());
        return dataService.get().requestRemoveData(message);
    }

    public CompletableFuture<RequestInventoryResult> requestInventory(DataFilter dataFilter) {
        checkArgument(dataService.isPresent());
        return dataService.get().requestInventory(dataFilter);
    }

    public void addMessageListener(MessageListener messageListener) {
        confidentialMessageService.ifPresent(service -> service.addMessageListener(messageListener));
    }

    public void removeMessageListener(MessageListener messageListener) {
        confidentialMessageService.ifPresent(service -> service.removeMessageListener(messageListener));
    }

    public void shutdown() {
        dataService.ifPresent(DataService::shutdown);
        overlayNetworkService.ifPresent(OverlayNetworkService::shutdown);
        confidentialMessageService.ifPresent(ConfidentialMessageService::shutdown);
        relayService.ifPresent(RelayService::shutdown);
        node.close();
        storage.shutdown();
    }

    public Optional<Address> findMyAddress() {
        return node.findMyAddress();
    }

    public NetworkType getNetworkType() {
        return networkConfig.getNetworkType();
    }

    public SocketFactory getNetworkProxy() {
        return node.getSocketFactory();
    }
}
