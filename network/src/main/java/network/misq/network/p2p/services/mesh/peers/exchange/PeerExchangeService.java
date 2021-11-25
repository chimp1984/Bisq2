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

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import network.misq.common.util.CompletableFutureUtils;
import network.misq.network.p2p.message.Message;
import network.misq.network.p2p.node.Address;
import network.misq.network.p2p.node.Connection;
import network.misq.network.p2p.node.MessageListener;
import network.misq.network.p2p.node.Node;
import network.misq.network.p2p.services.mesh.peers.Peer;
import network.misq.network.p2p.services.mesh.peers.PeerGroup;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
@Getter
public class PeerExchangeService implements MessageListener {
    private final Node node;
    private final PeerGroup peerGroup;
    private final List<Address> seedNodeAddresses;
    private final Set<Address> successCandidates = new CopyOnWriteArraySet<>();
    private final Map<Address, PeerExchangeRequestHandler> requestHandlerMap = new ConcurrentHashMap<>();

    public PeerExchangeService(Node node, PeerGroup peerGroup, List<Address> seedNodeAddresses) {
        this.node = node;
        this.peerGroup = peerGroup;
        this.seedNodeAddresses = seedNodeAddresses;
        node.addMessageListener(this);
    }

    /**
     * We start a peer exchange with 2 seed nodes and
     */
    public CompletableFuture<Boolean> initialize() {
        return CompletableFutureUtils.either(doPeerExchange(getShuffled(seedNodeAddresses), 2),
                doPeerExchange(getAddressesForPeerExchange(), 5));
    }

    CompletableFuture<Boolean> doPeerExchange(List<Address> addressList,
                                              int numRequests) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        checkArgument(numRequests <= addressList.size(),
                "numRequests must not be larger as seedNodeAddresses");

        // We set up all requestHandlers for parallel executions
        for (int i = 0; i < numRequests; i++) {
            Address candidate = addressList.remove(0);
            if (requestHandlerMap.containsKey(candidate)) {
                continue;
            }
            PeerExchangeRequestHandler requestHandler = new PeerExchangeRequestHandler(node, candidate);
            requestHandlerMap.put(candidate, requestHandler);
            log.debug("Create requestHandler for candidate {}", candidate);
            requestHandler.getFuture().whenComplete((peers, throwable) -> {
                Address requestHandlerAddress = requestHandler.getAddress();
                if (throwable == null) {
                    peerGroup.addPeersFromPeerExchange(peers);
                    requestHandlerMap.remove(requestHandlerAddress);
                    successCandidates.add(requestHandlerAddress);
                    log.debug("RequestHandler completed for {}. peers={}", candidate, peers);
                    if (requestHandlerMap.isEmpty()) {
                        log.info("All requestHandlers completed");
                        future.complete(true);
                    }
                } else {
                    log.info("RequestHandler failed for {}", candidate);
                    if (!addressList.isEmpty()) {
                        requestHandlerMap.remove(requestHandlerAddress);
                        log.info("We try again with addressList {}", addressList);
                        doPeerExchange(addressList, 1);
                    } else {
                        log.warn("We did not get sufficient successful responses.");
                        future.complete(false);
                    }
                }
            });
            requestHandlerMap.put(candidate, requestHandler);
        }
        requestHandlerMap.values().forEach(e -> e.start(peerGroup.getPeersForPeerExchange(e.getAddress())));
        return future;
    }

    public List<Address> getAddressesForPeerExchange() {
        return peerGroup.getExchangedPeers().stream()
                .sorted(Comparator.comparing(Peer::getDate))
                .map(Peer::getAddress)
                .collect(Collectors.toList());
    }

    public CompletableFuture<Void> shutdown() {
        return CompletableFuture.completedFuture(null);
    }


    @Override
    public void onMessage(Message message, Connection connection, String nodeId) {
        if (message instanceof PeerExchangeRequest request) {
            log.debug("Node {} received PeerExchangeRequest with peers {}", node.getMyAddress(), request.getPeers());
            peerGroup.addPeersFromPeerExchange(request.getPeers());
            node.send(new PeerExchangeResponse(request.getNonce(), peerGroup.getPeersForPeerExchange(connection.getPeerAddress())), connection);
            log.debug("Node {} send PeerExchangeResponse with my peers {}", node.getMyAddress(), peerGroup.getPeersForPeerExchange(connection.getPeerAddress()));
        }
    }

    private List<Address> getShuffled(Collection<Address> addresses) {
        List<Address> list = new ArrayList<>(addresses);
        Collections.shuffle(list);
        return list;
    }
}