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

import network.misq.network.p2p.node.CloseConnectionMessage;
import network.misq.network.p2p.node.CloseReason;
import network.misq.network.p2p.node.Connection;
import network.misq.network.p2p.node.Node;
import network.misq.network.p2p.services.mesh.peers.exchange.PeerExchangeService;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class PeerGroupHealth {
    private static final long INTERVAL = TimeUnit.MINUTES.toMillis(5);
    private static final int MAX_REPORTED = 2000;
    private static final int MAX_PERSISTED = 1000;

    private final Node node;
    private final PeerGroup peerGroup;
    private final PeerExchangeService peerExchangeService;
    private final Timer timer = new Timer();

    public PeerGroupHealth(Node node, PeerGroup peerGroup, PeerExchangeService peerExchangeService) {
        this.node = node;
        this.peerGroup = peerGroup;
        this.peerExchangeService = peerExchangeService;
    }

    public CompletableFuture<Boolean> initialize() {
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                maybeCloseConnections();
                maybeCreateConnections();
                maybeRemoveReportedPeers();
                maybeRemovePersistedPeers();
            }

        }, INTERVAL);
        return CompletableFuture.completedFuture(true);
    }

    public void shutdown() {
        timer.cancel();
    }

    /**
     * If we exceed our maxNumConnectedPeers we try to find enough old inbound connections to remove
     * and if not sufficient we add also old outbound connections.
     */
    private void maybeCloseConnections() {
        int maxNumConnectedPeers = peerGroup.getMaxNumConnectedPeers();
        int numConnections = peerGroup.getAllConnectedPeers().size();
        int overFlow = numConnections - maxNumConnectedPeers;
        if (overFlow < 0) {
            return;
        }
        List<Connection> inbound = new ArrayList<>(peerGroup.getInboundConnections());
        inbound.sort(Comparator.comparing(c -> c.getMetrics().getDate()));
        List<Connection> candidates = new ArrayList<>();
        if (!inbound.isEmpty()) {
            candidates.addAll(inbound.subList(0, Math.min(overFlow, inbound.size())));
        }
        int missing = overFlow - candidates.size();
        if (missing > 0) {
            List<Connection> outbound = new ArrayList<>(peerGroup.getOutboundConnections());
            outbound.sort(Comparator.comparing(c -> c.getMetrics().getDate()));
            if (!outbound.isEmpty()) {
                candidates.addAll(outbound.subList(0, Math.min(missing, outbound.size())));
            }
        }

        candidates.forEach(connection -> node.send(new CloseConnectionMessage(CloseReason.TOO_MANY_CONNECTIONS), connection));
    }

    private void maybeCreateConnections() {
        int minNumConnectedPeers = peerGroup.getMinNumConnectedPeers();
        int numConnections = peerGroup.getAllConnectedPeers().size();
        int missing = minNumConnectedPeers - numConnections;
        if (missing < 0) {
            return;
        }

        // We use peer exchange protocol for establishing new connections.
        // The calculation how many connections we need is done inside PeerExchangeService/PeerExchangeStrategy
        peerExchangeService.startPeerExchange();
    }

    private void maybeRemoveReportedPeers() {
        List<Peer> reportedPeers = new ArrayList<>(peerGroup.getReportedPeers());
        int overFlow = reportedPeers.size() - MAX_REPORTED;
        if (overFlow > 0) {
            reportedPeers.sort(Comparator.comparing(Peer::getDate));
            List<Peer> candidates = reportedPeers.subList(0, Math.min(overFlow, reportedPeers.size()));
            peerGroup.removeReportedPeers(candidates);
        }
    }

    private void maybeRemovePersistedPeers() {
        List<Peer> persistedPeers = new ArrayList<>(peerGroup.getPersistedPeers());
        int overFlow = persistedPeers.size() - MAX_PERSISTED;
        if (overFlow > 0) {
            persistedPeers.sort(Comparator.comparing(Peer::getDate));
            List<Peer> candidates = persistedPeers.subList(0, Math.min(overFlow, persistedPeers.size()));
            peerGroup.removePersistedPeers(candidates);
        }
    }
}
