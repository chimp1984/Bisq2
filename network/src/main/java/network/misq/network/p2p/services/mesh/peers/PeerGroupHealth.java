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

package network.misq.network.p2p.services.mesh.peers;

import lombok.extern.slf4j.Slf4j;
import network.misq.common.timer.TimerUtil;
import network.misq.common.util.MathUtils;
import network.misq.network.p2p.node.*;
import network.misq.network.p2p.services.mesh.peers.exchange.PeerExchangeService;
import network.misq.network.p2p.services.mesh.peers.keepalive.KeepAliveService;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class PeerGroupHealth {
    // private static final long INTERVAL = TimeUnit.MINUTES.toMillis(5);
    private static final long INTERVAL = TimeUnit.SECONDS.toMillis(2);

    private static final int MAX_REPORTED = 2000;
    private static final int MAX_PERSISTED = 1000;
    private static final long BOOTSTRAP_TIME = TimeUnit.SECONDS.toMillis(2);

    private final Node node;
    private final PeerGroup peerGroup;
    private final PeerExchangeService peerExchangeService;
    private final KeepAliveService keepAliveService;
    private Timer timer;

    public PeerGroupHealth(Node node, PeerGroup peerGroup,
                           PeerExchangeService peerExchangeService,
                           KeepAliveService.Config keepAliveServiceConfig) {
        this.node = node;
        this.peerGroup = peerGroup;
        this.peerExchangeService = peerExchangeService;
        keepAliveService = new KeepAliveService(node, peerGroup, keepAliveServiceConfig);
    }

    public CompletableFuture<Boolean> initialize() {
        timer = TimerUtil.runPeriodically(() -> {
            maybeCloseDuplicateConnections();
            maybeCloseConnections();
            maybeCreateConnections();
            maybeRemoveReportedPeers();
            maybeRemovePersistedPeers();
        }, INTERVAL, TimeUnit.MILLISECONDS);
        keepAliveService.initialize();
        return CompletableFuture.completedFuture(true);
    }

    public void shutdown() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    /**
     * Remove duplicate connections (inbound connections which have an outbound connection with the same address)
     */
    private void maybeCloseDuplicateConnections() {
        Map<Address, Connection> outboundConnectionsByAddress = peerGroup.getOutboundConnections().stream()
                .collect(Collectors.toMap(Connection::getPeerAddress, c -> c));
        peerGroup.getInboundConnections().stream()
                .filter(inboundConnection -> outboundConnectionsByAddress.containsKey(inboundConnection.getPeerAddress()))
                .filter(this::isNotBootstrapping)
                .peek(connection -> log.info("Node {} send CloseConnectionMessage to peer {} as we have an " +
                                "outbound connection with the same address.",
                        node, connection.getPeersCapability().address().toString()))
                .forEach(connection -> node.send(new CloseConnectionMessage(CloseReason.DUPLICATE_CONNECTION), connection));
    }

    /**
     * If we exceed our maxNumConnectedPeers we try to find enough old inbound connections to remove
     * and if not sufficient we add also old outbound connections.
     */
    private void maybeCloseConnections() {
        int maxNumConnectedPeers = peerGroup.getMaxNumConnectedPeers();
        int numAllConnections = peerGroup.getNumAllConnections();
        int exceeding = numAllConnections - maxNumConnectedPeers;
        if (exceeding <= 0) {
            return;
        }

        // Remove the oldest inbound connections
        List<InboundConnection> inbound = new ArrayList<>(peerGroup.getInboundConnections());
        inbound.sort(Comparator.comparing(c -> c.getMetrics().getCreationDate()));
        List<Connection> candidates = new ArrayList<>();
        if (!inbound.isEmpty()) {
            List<InboundConnection> list = inbound.subList(0, Math.min(exceeding, inbound.size())).stream()
                    .filter(this::isNotBootstrapping)
                    .collect(Collectors.toList());
            candidates.addAll(list);
        }

        int stillExceeding = exceeding - candidates.size();
        if (stillExceeding > 0) {
            List<Connection> outbound = new ArrayList<>(peerGroup.getOutboundConnections());
            outbound.sort(Comparator.comparing(c -> c.getMetrics().getCreationDate()));
            if (!outbound.isEmpty()) {
                List<Connection> list = outbound.subList(0, Math.min(stillExceeding, outbound.size())).stream()
                        .filter(this::isNotBootstrapping)
                        .collect(Collectors.toList());
                candidates.addAll(list);
            }
        }
        if (!candidates.isEmpty()) {
            log.info("Node {} has {} connections. Our max connections target is {}. " +
                            "We close {} connections.",
                    node, numAllConnections, maxNumConnectedPeers, candidates.size());
        }
        candidates.stream()
                .peek(connection -> log.info("Node {} send CloseConnectionMessage to peer {} as we have too many connections.",
                        node, connection.getPeersCapability().address().toString()))
                .forEach(connection -> node.send(new CloseConnectionMessage(CloseReason.TOO_MANY_CONNECTIONS), connection));
    }

    private void maybeCreateConnections() {
        int minNumConnectedPeers = peerGroup.getMinNumConnectedPeers();
        int numOutboundConnections = peerGroup.getOutboundConnections().size();
        // We want to have at least 40% of our minNumConnectedPeers as outbound connections 
        int missingOutboundConnections = MathUtils.roundDoubleToInt(minNumConnectedPeers * 0.4) - numOutboundConnections;
        if (missingOutboundConnections <= 0) {
            // We have enough outbound connections, lets check if we have sufficient connections in total
            int numAllConnections = peerGroup.getNumAllConnections();
            int missing = minNumConnectedPeers - numAllConnections;
            if (missing <= 0) {
                return;
            }
        }

        // We use the peer exchange protocol for establishing new connections.
        // The calculation how many connections we need is done inside PeerExchangeService/PeerExchangeStrategy
        peerExchangeService.doFurtherPeerExchange();
    }

    private void maybeRemoveReportedPeers() {
        List<Peer> reportedPeers = new ArrayList<>(peerGroup.getReportedPeers());
        int exceeding = reportedPeers.size() - MAX_REPORTED;
        if (exceeding > 0) {
            reportedPeers.sort(Comparator.comparing(Peer::getDate));
            List<Peer> candidates = reportedPeers.subList(0, Math.min(exceeding, reportedPeers.size()));
            log.info("Remove {} reported peers: {}", candidates.size(), candidates);
            peerGroup.removeReportedPeers(candidates);
        }
    }

    private void maybeRemovePersistedPeers() {
        List<Peer> persistedPeers = new ArrayList<>(peerGroup.getPersistedPeers());
        int exceeding = persistedPeers.size() - MAX_PERSISTED;
        if (exceeding > 0) {
            persistedPeers.sort(Comparator.comparing(Peer::getDate));
            List<Peer> candidates = persistedPeers.subList(0, Math.min(exceeding, persistedPeers.size()));
            log.info("Remove {} persisted peers: {}", candidates.size(), candidates);
            peerGroup.removePersistedPeers(candidates);
        }
    }

    private boolean isNotBootstrapping(Connection connection) {
        return connection.getMetrics().getAge() > BOOTSTRAP_TIME;
    }
}
