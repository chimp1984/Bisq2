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
import network.misq.common.util.CompletableFutureUtils;
import network.misq.common.util.NetworkUtils;
import network.misq.network.p2p.message.Message;
import network.misq.network.p2p.node.Address;
import network.misq.network.p2p.node.Connection;
import network.misq.network.p2p.node.MessageListener;
import network.misq.network.p2p.node.Node;
import network.misq.network.p2p.node.authorization.UnrestrictedAuthorizationService;
import network.misq.network.p2p.node.transport.Transport;
import network.misq.network.p2p.services.confidential.ConfidentialMessageService;
import network.misq.network.p2p.services.data.DataService;
import network.misq.network.p2p.services.data.filter.DataFilter;
import network.misq.network.p2p.services.data.inventory.RequestInventoryResult;
import network.misq.network.p2p.services.mesh.MeshService;
import network.misq.network.p2p.services.mesh.peers.SeedNodeRepository;
import network.misq.network.p2p.services.mesh.peers.exchange.PeerExchangeStrategy;
import network.misq.network.p2p.services.mesh.router.gossip.GossipResult;
import network.misq.security.KeyPairRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Maintains P2pServiceNode per NetworkType
 */
public class P2pServiceNodesByTransportType {
    private static final Logger log = LoggerFactory.getLogger(P2pServiceNodesByTransportType.class);

    private final Map<Transport.Type, P2pServiceNode> p2pServiceNodeByTransportType = new ConcurrentHashMap<>();

    public P2pServiceNodesByTransportType(String baseDirPath,
                                          Set<Transport.Type> supportedTransportTypes,
                                          P2pServiceNode.Config p2pServiceNodeConfig,
                                          SeedNodeRepository seedNodeRepository,
                                          DataService.Config dataServiceConfig,
                                          KeyPairRepository keyPairRepository) {
        supportedTransportTypes.forEach(transportType -> {
            Node.Config config = new Node.Config(transportType,
                    supportedTransportTypes,
                    new UnrestrictedAuthorizationService(),
                    new Transport.Config(baseDirPath));
            MeshService.Config meshServiceConfig = new MeshService.Config(new PeerExchangeStrategy.Config(),
                    seedNodeRepository.addressesByTransportType().get(transportType));
            P2pServiceNode p2PServiceNode = new P2pServiceNode(p2pServiceNodeConfig,
                    config,
                    meshServiceConfig,
                    dataServiceConfig,
                    new ConfidentialMessageService.Config(keyPairRepository));
            p2pServiceNodeByTransportType.put(transportType, p2PServiceNode);
        });
    }

    public P2pServiceNodesByTransportType() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Completes if all networkNodes are completed. Return true if all servers have been successfully completed
     * otherwise returns false.
     */
    public CompletableFuture<Boolean> initializeServer(BiConsumer<Transport.ServerSocketResult, Throwable> resultHandler) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        AtomicInteger completed = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        int numNodes = p2pServiceNodeByTransportType.size();
        p2pServiceNodeByTransportType.values().forEach(networkNode -> {
            networkNode.initializeServer(Node.DEFAULT_NODE_ID, NetworkUtils.findFreeSystemPort())
                    .whenComplete((serverInfo, throwable) -> {
                        if (serverInfo != null) {
                            resultHandler.accept(serverInfo, null);
                            int compl = completed.incrementAndGet();
                            if (compl + failed.get() == numNodes) {
                                future.complete(compl == numNodes);
                            }
                        } else {
                            log.error(throwable.toString(), throwable);
                            resultHandler.accept(null, throwable);
                            if (failed.incrementAndGet() + completed.get() == numNodes) {
                                future.complete(false);
                            }
                        }
                    });
        });
        return future;
    }

    /**
     * Completes if all networkNodes are completed. Return true if all servers have been successfully completed
     * and at least one has been successful.
     */
    public CompletableFuture<Boolean> initializeMesh() {
        List<CompletableFuture<Boolean>> allFutures = new ArrayList<>();
        p2pServiceNodeByTransportType.values().forEach(p2pServiceNode -> {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            allFutures.add(future);
            p2pServiceNode.initializeMesh()
                    .whenComplete((success, throwable) -> {
                        if (throwable == null) {
                            future.complete(success); // Can be still false
                        } else {
                            future.complete(false);
                        }
                    });
        });
        return CompletableFutureUtils.allOf(allFutures)                                 // We require all futures to be completed
                .thenApply(resultList -> resultList.stream().anyMatch(e -> e))  // If at least one network succeeded
                .thenCompose(CompletableFuture::completedFuture);               // If at least one was successful we report a success
    }

    public CompletableFuture<Connection> confidentialSend(Message message, NetworkId networkId, KeyPair myKeyPair, String connectionId) {
        CompletableFuture<Connection> future = new CompletableFuture<>();
        networkId.addressByNetworkType().forEach((transportType, address) -> {
            try {
                if (p2pServiceNodeByTransportType.containsKey(transportType)) {
                    p2pServiceNodeByTransportType.get(transportType)
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
                    p2pServiceNodeByTransportType.values().forEach(networkNode -> {
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
            } catch (GeneralSecurityException e) {
                e.printStackTrace();
            }
        });
        return future;
    }

    public void requestAddData(Message message, Consumer<GossipResult> resultHandler) {
        p2pServiceNodeByTransportType.values().forEach(networkNode -> {
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
        p2pServiceNodeByTransportType.values().forEach(dataService -> {
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
        p2pServiceNodeByTransportType.values().forEach(networkNode -> {
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
        if (p2pServiceNodeByTransportType.containsKey(Transport.Type.TOR)) {
            try {
                return p2pServiceNodeByTransportType.get(Transport.Type.TOR).getSocksProxy();
            } catch (IOException e) {
                return Optional.empty();
            }
        } else {
            return Optional.empty();
        }
    }

    public void addMessageListener(MessageListener messageListener) {
        p2pServiceNodeByTransportType.values().forEach(networkNode -> {
            networkNode.addMessageListener(messageListener);
        });
    }

    public void removeMessageListener(MessageListener messageListener) {
        p2pServiceNodeByTransportType.values().forEach(networkNode -> {
            networkNode.removeMessageListener(messageListener);
        });
    }

    public CompletableFuture<Void> shutdown() {
        CountDownLatch latch = new CountDownLatch(p2pServiceNodeByTransportType.size());
        return CompletableFuture.runAsync(() -> {
            p2pServiceNodeByTransportType.values()
                    .forEach(p2pServiceNode -> p2pServiceNode.shutdown().whenComplete((v, t) -> latch.countDown()));
            try {
                latch.await(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                log.error("Shutdown interrupted by timeout");
            }
            p2pServiceNodeByTransportType.clear();
        });
    }


    //todo
  /*  public Optional<Address> findMyAddress(NetworkType networkType) {
        return p2pNodes.get(networkType).findMyAddress();
    }

    public Set<Address> findMyAddresses() {
        return p2pNodes.values().stream()
                .flatMap(networkNode -> networkNode.findMyAddress().stream())
                .collect(Collectors.toSet());
    }*/

    public Set<Address> findMyAddresses() {
        return new HashSet<>(); //todo
    }
}
