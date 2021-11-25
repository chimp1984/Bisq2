/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package network.misq.network.p2p.services.mesh.peers.exchange;

import com.google.common.annotations.VisibleForTesting;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import network.misq.common.util.CompletableFutureUtils;
import network.misq.network.p2p.message.Message;
import network.misq.network.p2p.node.Address;
import network.misq.network.p2p.node.Connection;
import network.misq.network.p2p.node.MessageListener;
import network.misq.network.p2p.node.Node;
import network.misq.network.p2p.services.mesh.peers.Peer;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Responsible for executing the peer exchange protocol with the given peer.
 * We use the PeerExchangeStrategy for the selection of nodes and strategy for bootstrap.
 * We could use a set of different strategies and select randomly one. This could increase network resilience.
 */
@Slf4j
@Getter
public class PeerExchangeService implements MessageListener {
    private final Node node;
    private final PeerExchangeStrategy peerExchangeStrategy;
    private final Map<Address, PeerExchangeRequestHandler> requestHandlerMap = new ConcurrentHashMap<>();

    public PeerExchangeService(Node node, PeerExchangeStrategy peerExchangeStrategy) {
        this.node = node;
        this.peerExchangeStrategy = peerExchangeStrategy;
        this.node.addMessageListener(this);
    }

    public CompletableFuture<Boolean> initialize() {
        List<Address> addresses = peerExchangeStrategy.getAddressesForPeerExchange();
        checkArgument(!addresses.isEmpty(),
                "addresses must not be empty. We expect at least seed nodes.");
        List<CompletableFuture<Boolean>> allFutures = addresses.stream()
                .map(this::doPeerExchange)
                .collect(Collectors.toList());
        return CompletableFutureUtils.allOf(allFutures)
                .orTimeout(300, TimeUnit.SECONDS)
                .thenCompose(resultList -> {
                    int numSuccess = (int) resultList.stream().filter(e -> e).count();
                    log.info("Peer exchange with {} peers completed. {} requests successfully completed.",
                            addresses.size(), numSuccess);
                    boolean sufficientSuccess = peerExchangeStrategy.sufficientSuccess(numSuccess, addresses.size());
                    return CompletableFuture.completedFuture(sufficientSuccess);
                });
    }
@VisibleForTesting
     CompletableFuture<Boolean> doPeerExchange(Address address) {
        return node.getConnection(address)
                .orTimeout(60, TimeUnit.SECONDS)
                .thenCompose(connection -> {
                    PeerExchangeRequestHandler handler = new PeerExchangeRequestHandler(node);
                    requestHandlerMap.put(address, handler);
                    Set<Peer> peers = peerExchangeStrategy.getPeersForPeerExchange(address);
                    return handler.request(connection, peers);
                })
                .orTimeout(60, TimeUnit.SECONDS)
                .handle((peers, throwable) -> {
                    requestHandlerMap.remove(address);
                    if (throwable == null) {
                        peerExchangeStrategy.addPeersFromPeerExchange(peers);
                        return true;
                    } else {
                        log.error("doPeerExchange failed", throwable);
                        return false;
                    }
                });
    }

    public CompletableFuture<Void> shutdown() {
        requestHandlerMap.values().forEach(PeerExchangeRequestHandler::dispose);
        requestHandlerMap.clear();
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void onMessage(Message message, Connection connection, String nodeId) {
        if (message instanceof PeerExchangeRequest request) {
            log.debug("Node {} received PeerExchangeRequest with myPeers {}", node.getMyAddress(), request.peers());
            peerExchangeStrategy.addPeersFromPeerExchange(request.peers());
            Set<Peer> myPeers = peerExchangeStrategy.getPeersForPeerExchange(connection.getPeerAddress());
            node.send(new PeerExchangeResponse(request.nonce(), myPeers), connection);
            log.debug("Node {} send PeerExchangeResponse with my myPeers {}", node.getMyAddress(), myPeers);
        }
    }

}