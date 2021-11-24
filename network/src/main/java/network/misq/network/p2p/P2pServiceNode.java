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


import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;
import network.misq.network.p2p.message.Message;
import network.misq.network.p2p.node.*;
import network.misq.network.p2p.node.transport.Transport;
import network.misq.network.p2p.services.confidential.ConfMsgService;
import network.misq.network.p2p.services.data.DataService;
import network.misq.network.p2p.services.data.filter.DataFilter;
import network.misq.network.p2p.services.data.inventory.RequestInventoryResult;
import network.misq.network.p2p.services.mesh.MeshService;
import network.misq.network.p2p.services.mesh.router.gossip.GossipResult;
import network.misq.network.p2p.services.relay.RelayService;
import network.misq.security.PubKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Creates nodeRepository and a default node
 * Creates services according to services defined in Config
 */
public class P2pServiceNode {
    private static final Logger log = LoggerFactory.getLogger(P2pServiceNode.class);

    public static record Config(Set<Service> services) {
    }

    public enum Service {
        OVERLAY,
        DATA,
        CONFIDENTIAL,
        RELAY
    }

    private final NodesById nodesById;
    private final Node defaultNode;
    private Optional<ConfMsgService> confidentialMessageService;
    private Optional<DataService> dataService;
    private Optional<MeshService> overlayNetworkService;
    private Optional<RelayService> relayService;

    public P2pServiceNode(Config config,
                          Node.Config nodeConfig,
                          MeshService.Config meshServiceConfig,
                          DataService.Config dataServiceConfig,
                          ConfMsgService.Config confMsgServiceConfig) {
        nodesById = new NodesById(nodeConfig);
        defaultNode = nodesById.getDefaultNode();

        Set<Service> services = config.services();
        if (services.contains(Service.CONFIDENTIAL)) {
            confidentialMessageService = Optional.of(new ConfMsgService(nodesById, confMsgServiceConfig));
        }
        if (services.contains(Service.RELAY)) {
            relayService = Optional.of(new RelayService(defaultNode));
        }
        if (services.contains(Service.OVERLAY)) {
            overlayNetworkService = Optional.of(new MeshService(defaultNode, meshServiceConfig));

            if (services.contains(Service.DATA)) {
                dataService = Optional.of(new DataService(defaultNode, overlayNetworkService.get(), dataServiceConfig));
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////////////

    public CompletableFuture<Transport.ServerSocketResult> initializeServer(String nodeId, int serverPort) {
        return nodesById.initializeServer(nodeId, serverPort);
    }

    public CompletableFuture<Boolean> initializeOverlay() {
        checkArgument(overlayNetworkService.isPresent());
        return overlayNetworkService.get().bootstrap();
    }

    public CompletableFuture<Connection> confidentialSend(Message message, Address address, PubKey pubKey, KeyPair myKeyPair, String connectionId)
            throws GeneralSecurityException {
        checkArgument(confidentialMessageService.isPresent());
        return confidentialMessageService.get().send(message, address, pubKey, myKeyPair, connectionId);
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

    public Optional<Socks5Proxy> getSocksProxy() throws IOException {
        return defaultNode.getSocksProxy();
    }

    public void shutdown() {
        nodesById.shutdown();
        confidentialMessageService.ifPresent(ConfMsgService::shutdown);
        relayService.ifPresent(RelayService::shutdown);
        overlayNetworkService.ifPresent(MeshService::shutdown);
        dataService.ifPresent(DataService::shutdown);
    }

    public void addMessageListener(MessageListener messageListener) {
        confidentialMessageService.ifPresent(service -> service.addMessageListener(messageListener));
    }

    public void removeMessageListener(MessageListener messageListener) {
        confidentialMessageService.ifPresent(service -> service.removeMessageListener(messageListener));
    }
}
