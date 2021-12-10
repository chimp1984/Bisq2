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
import network.misq.network.p2p.node.Address;
import network.misq.network.p2p.services.mesh.peers.Peer;
import network.misq.network.p2p.services.mesh.peers.PeerGroup;

import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class PeerExchangeStrategy {
    private static final long REPORTED_PEERS_LIMIT = 200;
    private static final long MAX_AGE = TimeUnit.DAYS.toMillis(10);

    @Getter
    public static class Config {
        private final int numSeeNodesAtBoostrap;
        private final int numPersistedPeersAtBoostrap;
        private final int numReportedPeersAtBoostrap;

        public Config() {
            this(2, 40, 20);
        }

        public Config(int numSeeNodesAtBoostrap,
                      int numPersistedPeersAtBoostrap,
                      int numReportedPeersAtBoostrap) {
            this.numSeeNodesAtBoostrap = numSeeNodesAtBoostrap;
            this.numPersistedPeersAtBoostrap = numPersistedPeersAtBoostrap;
            this.numReportedPeersAtBoostrap = numReportedPeersAtBoostrap;
        }
    }

    private final PeerGroup peerGroup;
    private final List<Address> seedNodeAddresses;
    private final Config config;
    private final Set<Address> usedAddresses = new CopyOnWriteArraySet<>();

    public PeerExchangeStrategy(PeerGroup peerGroup, List<Address> seedNodeAddresses, Config config) {
        this.peerGroup = peerGroup;
        this.seedNodeAddresses = seedNodeAddresses;
        this.config = config;
    }

    List<Address> getAddressesForPeerExchange() {
        int numSeeNodesAtBoostrap = config.getNumSeeNodesAtBoostrap();
        int numPersistedPeersAtBoostrap = config.getNumPersistedPeersAtBoostrap();
        int numReportedPeersAtBoostrap = config.getNumReportedPeersAtBoostrap();
        int maxNumConnectedPeers = peerGroup.getMaxNumConnectedPeers();

        List<Address> seeds = getShuffled(seedNodeAddresses).stream()
                .filter(peerGroup::notMyself)
                .filter(this::isNotUsed)
                .limit(numSeeNodesAtBoostrap)
                .collect(Collectors.toList());

        // Usually we don't have reported peers at startup, but in case of repeated bootstrap attempts we likely have some.
        // It could be also that other nodes have started peer exchange to ourselves before we start the peer exchange.
        Set<Address> reported = peerGroup.getReportedPeers().stream()
                .sorted(Comparator.comparing(Peer::getDate))
                .map(Peer::getAddress)
                .filter(this::isNotUsed)
                .limit(numReportedPeersAtBoostrap)
                .collect(Collectors.toSet());

        Set<Address> persisted = peerGroup.getPersistedPeers().stream()
                .sorted(Comparator.comparing(Peer::getDate))
                .map(Peer::getAddress)
                .filter(this::isNotUsed)
                .limit(numPersistedPeersAtBoostrap)
                .collect(Collectors.toSet());

        Set<Address> connectedPeerAddresses = peerGroup.getConnectedPeerAddresses().stream()
                .filter(this::notASeed)
                .filter(this::isNotUsed)
                .collect(Collectors.toSet());

        List<Address> priorityList = new ArrayList<>(seeds);
        priorityList.addAll(reported);
        priorityList.addAll(persisted);
        priorityList.addAll(connectedPeerAddresses);

        int numConnections = peerGroup.getAllConnectedPeers().size();
        int minNumConnectedPeers = peerGroup.getMinNumConnectedPeers();
        int targetNumConnectedPeers = minNumConnectedPeers + (maxNumConnectedPeers - minNumConnectedPeers) / 2;
        int missing = Math.max(0, targetNumConnectedPeers - numConnections);

        List<Address> candidates = priorityList.stream()
                .limit(missing)
                .distinct()
                .collect(Collectors.toList());
        usedAddresses.addAll(candidates);
        return candidates;
    }

    Set<Peer> getPeers(Address peerAddress) {
        List<Peer> list = new ArrayList<>(peerGroup.getAllConnectedPeers());
        list.addAll(peerGroup.getReportedPeers().stream()
                .sorted(Comparator.comparing(Peer::getDate))
                .collect(Collectors.toList()));
        return list.stream()
                .filter(peer -> isValid(peerAddress, peer))
                .limit(REPORTED_PEERS_LIMIT)
                .collect(Collectors.toSet());
    }

    void addReportedPeers(Set<Peer> peers, Address peerAddress) {
        Set<Peer> filtered = peers.stream()
                .filter(peer -> isValid(peerAddress, peer))
                .limit(REPORTED_PEERS_LIMIT)
                .collect(Collectors.toSet());
        peerGroup.addReportedPeers(filtered);
    }

    boolean repeatBootstrap(long numSuccess, int numRequests) {
        boolean moreThenHalfFailed = numRequests - numSuccess > numRequests / 2;
        return moreThenHalfFailed ||
                !sufficientConnections() ||
                !sufficientReportedPeers();
    }

    void shutdown() {
        usedAddresses.clear();
    }

    private boolean sufficientConnections() {
        return peerGroup.getAllConnectedPeers().size() >= peerGroup.getMinNumConnectedPeers();
    }

    private boolean sufficientReportedPeers() {
        return peerGroup.getReportedPeers().size() >= peerGroup.getMinNumReportedPeers();
    }

    private boolean notASeed(Address address) {
        return seedNodeAddresses.stream().noneMatch(seedAddress -> seedAddress.equals(address));
    }

    private boolean notASeed(Peer peer) {
        return notASeed(peer.getAddress());
    }

    private boolean isDateValid(Peer peer) {
        return peer.getAge() < MAX_AGE;
    }

    private boolean isNotUsed(Address address) {
        return !usedAddresses.contains(address);
    }

    private boolean notTargetPeer(Address peerAddress, Peer peer) {
        return !peer.getAddress().equals(peerAddress);
    }

    private boolean isValid(Address peerAddress, Peer peer) {
        return notTargetPeer(peerAddress, peer) &&
                peerGroup.notMyself(peer) &&
                notASeed(peer) &&
                isDateValid(peer);
    }

    private List<Address> getShuffled(Collection<Address> addresses) {
        List<Address> list = new ArrayList<>(addresses);
        Collections.shuffle(list);
        return list;
    }
}
