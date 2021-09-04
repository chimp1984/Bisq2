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


import network.misq.common.util.CompletableFutureUtils;
import network.misq.network.p2p.message.Message;
import network.misq.network.p2p.node.MessageListener;
import network.misq.network.p2p.node.capability.Connection;
import network.misq.network.p2p.node.connection.Address;
import network.misq.network.p2p.node.socket.NetworkType;
import network.misq.network.p2p.node.socket.SocketFactory;
import network.misq.network.p2p.services.data.filter.DataFilter;
import network.misq.network.p2p.services.data.inventory.RequestInventoryResult;
import network.misq.network.p2p.services.data.storage.Storage;
import network.misq.network.p2p.services.overlay.router.gossip.GossipResult;
import network.misq.security.KeyPairRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * High level API for the p2p network.
 */
public class P2pService {
    private static final Logger log = LoggerFactory.getLogger(P2pService.class);

    public Optional<SocketFactory> getNetworkProxy(NetworkType networkType) {
        return Optional.ofNullable(p2pNodes.get(networkType).getNetworkProxy());
    }

    public static record Option(String appDirPath, Set<NetworkConfig> networkConfigs) {
    }

    private final Map<NetworkType, P2pServiceNode> p2pNodes = new ConcurrentHashMap<>();

    public P2pService(Option option, KeyPairRepository keyPairRepository) {
        this(option.appDirPath(), option.networkConfigs(), keyPairRepository);
    }

    public P2pService(String appDirPath, Set<NetworkConfig> networkConfigs, KeyPairRepository keyPairRepository) {
        Storage storage = new Storage("");//todo
        networkConfigs.forEach(networkConfig -> {
            NetworkType networkType = networkConfig.getNetworkType();
            P2pServiceNode p2PServiceNode = new P2pServiceNode(networkConfig, storage, keyPairRepository);
            p2pNodes.put(networkType, p2PServiceNode);
        });
    }

    public P2pService() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Completes if all networkNodes are completed. Return true if all servers have been successfully completed
     * otherwise returns false.
     */
    public CompletableFuture<Boolean> initializeServer(BiConsumer<SocketFactory.GetServerSocketResult, Throwable> resultHandler) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        AtomicInteger completed = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        int numNodes = p2pNodes.size();
        p2pNodes.values().forEach(networkNode -> {
            networkNode.initializeServer()
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
        p2pNodes.values().forEach(networkNode -> {
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

    public CompletableFuture<Connection> confidentialSend(Message message, NetworkId peerNetworkId, KeyPair myKeyPair) {
        CompletableFuture<Connection> future = new CompletableFuture<>();
        peerNetworkId.getAddressByNetworkType().forEach((networkType, address) -> {
            try {
                if (p2pNodes.containsKey(networkType)) {
                    p2pNodes.get(networkType)
                            .confidentialSend(message, peerNetworkId, myKeyPair)
                            .whenComplete((connection, throwable) -> {
                                if (connection != null) {
                                    future.complete(connection);
                                } else {
                                    log.error(throwable.toString(), throwable);
                                    future.completeExceptionally(throwable);
                                }
                            });
                } else {
                    p2pNodes.values().forEach(networkNode -> {
                        networkNode.relay(message, peerNetworkId, myKeyPair)
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
        p2pNodes.values().forEach(networkNode -> {
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
        p2pNodes.values().forEach(dataService -> {
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
        p2pNodes.values().forEach(networkNode -> {
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

    public void addMessageListener(MessageListener messageListener) {
        p2pNodes.values().forEach(networkNode -> {
            networkNode.addMessageListener(messageListener);
        });
    }

    public void removeMessageListener(MessageListener messageListener) {
        p2pNodes.values().forEach(networkNode -> {
            networkNode.removeMessageListener(messageListener);
        });
    }

    public void shutdown() {
        p2pNodes.values().forEach(P2pServiceNode::shutdown);
    }

    public Optional<Address> findMyAddress(NetworkType networkType) {
        return p2pNodes.get(networkType).findMyAddress();
    }

    public Set<Address> findMyAddresses() {
        return p2pNodes.values().stream()
                .flatMap(networkNode -> networkNode.findMyAddress().stream())
                .collect(Collectors.toSet());
    }
}
