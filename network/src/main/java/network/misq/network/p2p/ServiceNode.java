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
import lombok.Getter;
import network.misq.network.p2p.message.Message;
import network.misq.network.p2p.node.*;
import network.misq.network.p2p.node.transport.Transport;
import network.misq.network.p2p.services.confidential.ConfidentialMessageService;
import network.misq.network.p2p.services.data.DataService;
import network.misq.network.p2p.services.data.filter.DataFilter;
import network.misq.network.p2p.services.data.inventory.RequestInventoryResult;
import network.misq.network.p2p.services.mesh.MeshService;
import network.misq.network.p2p.services.mesh.monitor.MonitorService;
import network.misq.network.p2p.services.mesh.router.gossip.GossipResult;
import network.misq.network.p2p.services.relay.RelayService;
import network.misq.security.PubKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.KeyPair;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Creates nodeRepository and a default node
 * Creates services according to services defined in Config
 */
public class ServiceNode {
    private static final Logger log = LoggerFactory.getLogger(ServiceNode.class);

    public static record Config(Set<Service> services) {
    }

    public enum Service {
        MESH,
        DATA,
        CONFIDENTIAL,
        RELAY,
        MONITOR
    }

    private final NodesById nodesById;
    private final Node defaultNode;
    private Optional<ConfidentialMessageService> confidentialMessageService;
    @Getter
    private Optional<MeshService> meshService;
    private Optional<DataService> dataService;
    private Optional<RelayService> relayService;
    private Optional<MonitorService> monitorService;

    public ServiceNode(Config config,
                       Node.Config nodeConfig,
                       MeshService.Config meshServiceConfig,
                       DataService.Config dataServiceConfig,
                       ConfidentialMessageService.Config confMsgServiceConfig) {
        nodesById = new NodesById(nodeConfig);
        defaultNode = nodesById.getDefaultNode();

        Set<Service> services = config.services();
        if (services.contains(Service.CONFIDENTIAL)) {
            confidentialMessageService = Optional.of(new ConfidentialMessageService(nodesById, confMsgServiceConfig));
        }

        if (services.contains(Service.MESH)) {
            meshService = Optional.of(new MeshService(defaultNode, meshServiceConfig));

            if (services.contains(Service.DATA)) {
                dataService = Optional.of(new DataService(defaultNode, meshService.get(), dataServiceConfig));
            }

            if (services.contains(Service.RELAY)) {
                relayService = Optional.of(new RelayService(defaultNode));
            }

            if (services.contains(Service.MONITOR)) {
                monitorService = Optional.of(new MonitorService(defaultNode));
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////////////

    public CompletableFuture<Boolean> bootstrap(String nodeId, int serverPort) {
        return initializeServer(nodeId, serverPort)
                .thenCompose(res -> initializeMesh());
    }

    public CompletableFuture<Transport.ServerSocketResult> initializeServer(String nodeId, int serverPort) {
        return nodesById.initializeServer(nodeId, serverPort);
    }

    public CompletableFuture<Boolean> initializeMesh() {
        return meshService.map(MeshService::initialize)
                .orElse(CompletableFuture.completedFuture(true));
    }

    public CompletableFuture<Connection> confidentialSend(Message message, Address address, PubKey pubKey, KeyPair myKeyPair, String nodeId) {
        return confidentialMessageService.map(service -> service.send(message, address, pubKey, myKeyPair, nodeId))
                .orElseThrow(() -> new RuntimeException("ConfidentialMessageService not present at confidentialSend"));
    }

    public CompletableFuture<Connection> relay(Message message, NetworkId networkId, KeyPair myKeyPair) {
        return relayService.map(service -> service.relay(message, networkId, myKeyPair))
                .orElseThrow(() -> new RuntimeException("RelayService not present at relay"));
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

    public CompletableFuture<Void> shutdown() {
        CountDownLatch latch = new CountDownLatch(1 + // For nodesById
                ((int) confidentialMessageService.stream().count()) +
                ((int) meshService.stream().count()) +
                ((int) relayService.stream().count()) +
                ((int) dataService.stream().count()) +
                ((int) monitorService.stream().count()));
        return CompletableFuture.runAsync(() -> {
            nodesById.shutdown().whenComplete((v, t) -> latch.countDown());
            confidentialMessageService.ifPresent(service -> service.shutdown().whenComplete((v, t) -> latch.countDown()));
            meshService.ifPresent(service -> service.shutdown().whenComplete((v, t) -> latch.countDown()));
            dataService.ifPresent(service -> service.shutdown().whenComplete((v, t) -> latch.countDown()));
            relayService.ifPresent(service -> service.shutdown().whenComplete((v, t) -> latch.countDown()));
            monitorService.ifPresent(service -> service.shutdown().whenComplete((v, t) -> latch.countDown()));

            try {
                latch.await(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                log.error("Shutdown interrupted by timeout");
            }
        });
    }

    public void addMessageListener(Node.MessageListener messageListener) {
        confidentialMessageService.ifPresent(service -> service.addMessageListener(messageListener));
    }

    public void removeMessageListener(Node.MessageListener messageListener) {
        confidentialMessageService.ifPresent(service -> service.removeMessageListener(messageListener));
    }

    public Optional<Address> findMyAddress(String nodeId) {
        return nodesById.findMyAddress(nodeId);
    }
    
    public Optional<Node> findNode(String nodeId) {
        return nodesById.findNode(nodeId);
    }

    public Map<String, Address> getAddressesByNodeId() {
        return nodesById.getAddressesByNodeId();
    }
}
