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
import network.misq.common.util.NetworkUtils;
import network.misq.network.p2p.message.Message;
import network.misq.network.p2p.node.Address;
import network.misq.network.p2p.node.Connection;
import network.misq.network.p2p.node.Node;
import network.misq.network.p2p.node.authorization.UnrestrictedAuthorizationService;
import network.misq.network.p2p.node.transport.Transport;
import network.misq.network.p2p.services.confidential.ConfidentialMessageService;
import network.misq.network.p2p.services.data.DataService;
import network.misq.network.p2p.services.data.filter.DataFilter;
import network.misq.network.p2p.services.data.inventory.RequestInventoryResult;
import network.misq.network.p2p.services.mesh.MeshService;
import network.misq.network.p2p.services.mesh.peers.PeerGroup;
import network.misq.network.p2p.services.mesh.peers.SeedNodeRepository;
import network.misq.network.p2p.services.mesh.peers.exchange.PeerExchangeStrategy;
import network.misq.network.p2p.services.mesh.peers.keepalive.KeepAliveService;
import network.misq.network.p2p.services.mesh.router.gossip.GossipResult;
import network.misq.security.KeyPairRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.security.KeyPair;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ServiceNodesByTransport {
    private static final Logger log = LoggerFactory.getLogger(ServiceNodesByTransport.class);

    private final Map<Transport.Type, ServiceNode> serviceNodesByTransport = new ConcurrentHashMap<>();

    public ServiceNodesByTransport(String baseDirPath,
                                   Set<Transport.Type> supportedTransportTypes,
                                   ServiceNode.Config serviceNodeConfig,
                                   PeerGroup.Config peerGroupConfig,
                                   PeerExchangeStrategy.Config peerExchangeConfig,
                                   SeedNodeRepository seedNodeRepository,
                                   DataService.Config dataServiceConfig,
                                   KeyPairRepository keyPairRepository) {
        long socketTimeout = TimeUnit.SECONDS.toMillis(10);
        Transport.Config transportConfig = new Transport.Config(baseDirPath);
        ConfidentialMessageService.Config confMsgServiceConfig = new ConfidentialMessageService.Config(keyPairRepository);
        KeepAliveService.Config keepAliveServiceConfig = new KeepAliveService.Config(socketTimeout / 2,
                socketTimeout / 4);

        supportedTransportTypes.forEach(transportType -> {
            Node.Config nodeConfig = new Node.Config(transportType,
                    supportedTransportTypes,
                    new UnrestrictedAuthorizationService(),
                    transportConfig,
                    (int) socketTimeout);

            List<Address> seedNodeAddresses = seedNodeRepository.addressesByTransportType().get(transportType);
            MeshService.Config meshServiceConfig = new MeshService.Config(peerGroupConfig,
                    peerExchangeConfig,
                    keepAliveServiceConfig,
                    seedNodeAddresses);

            ServiceNode serviceNode = new ServiceNode(serviceNodeConfig,
                    nodeConfig,
                    meshServiceConfig,
                    dataServiceConfig,
                    confMsgServiceConfig);
            serviceNodesByTransport.put(transportType, serviceNode);
        });
    }

    public ServiceNodesByTransport() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////////////

    public CompletableFuture<Boolean> bootstrap() {
        return bootstrap(null);
    }

    public CompletableFuture<Boolean> bootstrap(@Nullable BiConsumer<Boolean, Throwable> resultHandler) {
        return bootstrap(NetworkUtils.findFreeSystemPort(), resultHandler);
    }

    public CompletableFuture<Boolean> bootstrap(int port) {
        return bootstrap(port, null);
    }

    public CompletableFuture<Boolean> bootstrap(int port, @Nullable BiConsumer<Boolean, Throwable> resultHandler) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        AtomicInteger completed = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        int numNodes = serviceNodesByTransport.size();
        serviceNodesByTransport.values().forEach(networkNode -> {
            networkNode.bootstrap(Node.DEFAULT_NODE_ID, port)
                    .whenComplete((result, throwable) -> {
                        if (result != null) {
                            int compl = completed.incrementAndGet();
                            if (compl + failed.get() == numNodes) {
                                future.complete(compl == numNodes);
                            }
                        } else {
                            log.error(throwable.toString(), throwable);
                            if (failed.incrementAndGet() + completed.get() == numNodes) {
                                future.complete(false);
                            }
                        }
                        if (resultHandler != null) {
                            resultHandler.accept(result, throwable);
                        }
                    });
        });
        return future;
    }

    public CompletableFuture<Connection> confidentialSend(Message message, NetworkId networkId, KeyPair myKeyPair, String connectionId) {
        CompletableFuture<Connection> future = new CompletableFuture<>();
        networkId.addressByNetworkType().forEach((transportType, address) -> {
            if (serviceNodesByTransport.containsKey(transportType)) {
                serviceNodesByTransport.get(transportType)
                        .confidentialSend(message, networkId.addressByNetworkType().get(transportType), networkId.pubKey(), myKeyPair, connectionId)
                        .whenComplete((connection, throwable) -> {
                            if (connection != null) {
                                future.complete(connection);
                            } else {
                                log.error(throwable.toString(), throwable);
                                future.completeExceptionally(throwable);
                            }
                        });
            } else {
                serviceNodesByTransport.values().forEach(networkNode -> {
                    networkNode.relay(message, networkId, myKeyPair)
                            .whenComplete((connection, throwable) -> {
                                if (connection != null) {
                                    future.complete(connection);
                                } else {
                                    log.error(throwable.toString(), throwable);
                                    future.completeExceptionally(throwable);
                                }
                            });
                });
            }
        });
        return future;
    }

    public void requestAddData(Message message, Consumer<GossipResult> resultHandler) {
        serviceNodesByTransport.values().forEach(networkNode -> {
            networkNode.requestAddData(message)
                    .whenComplete((gossipResult, throwable) -> {
                        if (gossipResult != null) {
                            resultHandler.accept(gossipResult);
                        } else {
                            log.error(throwable.toString());
                        }
                    });
        });
    }

    public void requestRemoveData(Message message, Consumer<GossipResult> resultHandler) {
        serviceNodesByTransport.values().forEach(dataService -> {
            dataService.requestRemoveData(message)
                    .whenComplete((gossipResult, throwable) -> {
                        if (gossipResult != null) {
                            resultHandler.accept(gossipResult);
                        } else {
                            log.error(throwable.toString());
                        }
                    });
        });
    }

    public void requestInventory(DataFilter dataFilter, Consumer<RequestInventoryResult> resultHandler) {
        serviceNodesByTransport.values().forEach(networkNode -> {
            networkNode.requestInventory(dataFilter)
                    .whenComplete((requestInventoryResult, throwable) -> {
                        if (requestInventoryResult != null) {
                            resultHandler.accept(requestInventoryResult);
                        } else {
                            log.error(throwable.toString());
                        }
                    });
        });
    }

    public Optional<Socks5Proxy> getSocksProxy() {
        if (serviceNodesByTransport.containsKey(Transport.Type.TOR)) {
            try {
                return serviceNodesByTransport.get(Transport.Type.TOR).getSocksProxy();
            } catch (IOException e) {
                return Optional.empty();
            }
        } else {
            return Optional.empty();
        }
    }

    public void addMessageListener(Node.MessageListener messageListener) {
        serviceNodesByTransport.values().forEach(networkNode -> {
            networkNode.addMessageListener(messageListener);
        });
    }

    public void removeMessageListener(Node.MessageListener messageListener) {
        serviceNodesByTransport.values().forEach(networkNode -> {
            networkNode.removeMessageListener(messageListener);
        });
    }

    public CompletableFuture<Void> shutdown() {
        CountDownLatch latch = new CountDownLatch(serviceNodesByTransport.size());
        return CompletableFuture.runAsync(() -> {
            serviceNodesByTransport.values()
                    .forEach(serviceNode -> serviceNode.shutdown().whenComplete((v, t) -> latch.countDown()));
            try {
                latch.await(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                log.error("Shutdown interrupted by timeout");
            }
            serviceNodesByTransport.clear();
        });
    }

    public Map<Transport.Type, Map<String, Address>> findMyAddresses() {
        return serviceNodesByTransport.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getAddressesByNodeId()));
    }

    public Optional<Map<String, Address>> findMyAddresses(Transport.Type transport) {
        return Optional.ofNullable(findMyAddresses().get(transport));
    }

    public Optional<Address> findMyAddresses(Transport.Type transport, String nodeId) {
        return findMyAddresses(transport).map(map -> map.get(nodeId));
    }

    public Optional<ServiceNode> findServiceNode(Transport.Type transport) {
        return Optional.ofNullable(serviceNodesByTransport.get(transport));
    }

    public Optional<Node> findNode(Transport.Type transport, String nodeId) {
        return Optional.ofNullable(serviceNodesByTransport.get(transport))
                .flatMap(serviceNode -> serviceNode.findNode(nodeId));
    }
}
