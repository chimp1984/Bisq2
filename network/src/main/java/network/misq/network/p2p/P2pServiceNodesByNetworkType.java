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
import network.misq.network.p2p.node.transport.TransportType;
import network.misq.network.p2p.services.confidential.ConfMsgService;
import network.misq.network.p2p.services.data.DataService;
import network.misq.network.p2p.services.data.filter.DataFilter;
import network.misq.network.p2p.services.data.inventory.RequestInventoryResult;
import network.misq.network.p2p.services.mesh.MeshService;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Maintains P2pServiceNode per NetworkType
 */
public class P2pServiceNodesByNetworkType {
    private static final Logger log = LoggerFactory.getLogger(P2pServiceNodesByNetworkType.class);

    private final Map<TransportType, P2pServiceNode> map = new ConcurrentHashMap<>();

    public P2pServiceNodesByNetworkType(String baseDirPath,
                                        Set<TransportType> supportedTransportTypes,
                                        P2pServiceNode.Config p2pServiceNodeConfig,
                                        MeshService.Config meshServiceConfig,
                                        DataService.Config dataServiceConfig,
                                        KeyPairRepository keyPairRepository) {
        supportedTransportTypes.forEach(networkType -> {
            Node.Config config = new Node.Config(networkType,
                    supportedTransportTypes,
                    new UnrestrictedAuthorizationService(),
                    new Transport.Config(baseDirPath));
            P2pServiceNode p2PServiceNode = new P2pServiceNode(p2pServiceNodeConfig,
                    config,
                    meshServiceConfig,
                    dataServiceConfig,
                    new ConfMsgService.Config(keyPairRepository));
            map.put(networkType, p2PServiceNode);
        });
    }

    public P2pServiceNodesByNetworkType() {
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
        int numNodes = map.size();
        map.values().forEach(networkNode -> {
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
    public CompletableFuture<Boolean> initializeOverlay() {
        List<CompletableFuture<Boolean>> allFutures = new ArrayList<>();
        map.values().forEach(networkNode -> {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            allFutures.add(future);
            networkNode.initializeOverlay()
                    .whenComplete((success, e) -> {
                        if (e == null) {
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
                if (map.containsKey(transportType)) {
                    map.get(transportType)
                            .confidentialSend(message, networkId.addressByNetworkType().get(transportType),networkId.pubKey(), myKeyPair, connectionId)
                            .whenComplete((connection, throwable) -> {
                                if (connection != null) {
                                    future.complete(connection);
                                } else {
                                    log.error(throwable.toString(), throwable);
                                    future.completeExceptionally(throwable);
                                }
                            });
                } else {
                    map.values().forEach(networkNode -> {
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
        map.values().forEach(networkNode -> {
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
        map.values().forEach(dataService -> {
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
        map.values().forEach(networkNode -> {
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
        if (map.containsKey(TransportType.TOR)) {
            try {
                return map.get(TransportType.TOR).getSocksProxy();
            } catch (IOException e) {
                return Optional.empty();
            }
        } else {
            return Optional.empty();
        }
    }

    public void addMessageListener(MessageListener messageListener) {
        map.values().forEach(networkNode -> {
            networkNode.addMessageListener(messageListener);
        });
    }

    public void removeMessageListener(MessageListener messageListener) {
        map.values().forEach(networkNode -> {
            networkNode.removeMessageListener(messageListener);
        });
    }

    public void shutdown() {
        map.values().forEach(P2pServiceNode::shutdown);
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
