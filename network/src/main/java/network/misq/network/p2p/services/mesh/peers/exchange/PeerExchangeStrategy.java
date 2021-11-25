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

import lombok.extern.slf4j.Slf4j;
import network.misq.network.p2p.node.Address;
import network.misq.network.p2p.services.mesh.peers.Peer;
import network.misq.network.p2p.services.mesh.peers.PeerGroup;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Simple implements the strategy how to select the peers for peer exchange.
 */
@Slf4j
public class PeerExchangeStrategy {
    private final PeerGroup peerGroup;
    private final PeerExchangeConfig peerExchangeConfig;
    // We keep track of the addresses we contacted so in case we need to make a repeated request round that we do not \
    // pick the same addresses.
    private final Set<Address> usedAddresses = new HashSet<>();
    private final List<Address> seedNodeAddresses;

    public PeerExchangeStrategy(PeerGroup peerGroup, List<Address> seedNodeAddresses, PeerExchangeConfig peerExchangeConfig) {
        this.peerGroup = peerGroup;
        this.seedNodeAddresses = seedNodeAddresses;
        this.peerExchangeConfig = peerExchangeConfig;
    }

    public boolean sufficientSuccess(int numSuccess, int numRequests) {
        return numSuccess > numRequests / 2;
    }

    public void addPeersFromPeerExchange(Set<Peer> peers) {
        Set<Peer> collect = peers.stream()
                .filter(peerGroup::notMyself)
                .collect(Collectors.toSet());
        peerGroup.addReportedPeers(collect);
    }

    public Set<Peer> getPeersForPeerExchange(Address peerAddress) {
        List<Peer> list = peerGroup.getReportedPeers().stream()
                .sorted(Comparator.comparing(Peer::getDate))
                .limit(100)
                .collect(Collectors.toList());
        Set<Peer> allConnectedPeers = peerGroup.getAllConnectedPeers();
        list.addAll(allConnectedPeers);
        return list.stream()
                .filter(this::notASeed)
                .filter(peerGroup::notMyself)
                .filter(peer -> notTargetPeer(peerAddress, peer))
                .collect(Collectors.toSet());
    }

    public List<Address> getAddressesForPeerExchange() {
        int numSeeNodesAtBoostrap = peerExchangeConfig.getNumSeeNodesAtBoostrap();
        int numPersistedPeersAtBoostrap = peerExchangeConfig.getNumPersistedPeersAtBoostrap();
        int numReportedPeersAtBoostrap = peerExchangeConfig.getNumReportedPeersAtBoostrap();
        int minNumConnectedPeers = peerGroup.getConfig().getMinNumConnectedPeers();

        Set<Address> seeds = seedNodeAddresses.stream()
                .filter(peerGroup::notMyself)
                .filter(this::notUsedYet)
                .limit(numSeeNodesAtBoostrap)
                .collect(Collectors.toSet()); //2

        // Usually we don't have reported peers at startup, but in case or repeated bootstrap attempts we likely have
        // as well it could be that other nodes have started peer exchange to ourself before we start the peer exchange.
        Set<Address> reported = peerGroup.getReportedPeers().stream()
                .map(Peer::getAddress)
                .filter(peerGroup::notMyself)
                /* .filter(this::notUsedYet)
                 .limit(numReportedPeersAtBoostrap)*/
                .collect(Collectors.toSet()); //4

        Set<Address> persisted = peerGroup.getPersistedPeers().stream()
                .map(Peer::getAddress)
                .filter(peerGroup::notMyself)
                /* .filter(this::notUsedYet)
                 .limit(numPersistedPeersAtBoostrap)*/
                .collect(Collectors.toSet()); //8

        Set<Address> connectedPeerAddresses = peerGroup.getConnectedPeerAddresses().stream()
                .filter(peerGroup::notMyself)
                /* .filter(this::notUsedYet)
                 .filter(this::notASeed)*/
                .collect(Collectors.toSet());

        // If we have already connections (at repeated bootstraps) we limit the new set to what is missing to reach out
        // target.
        int numConnections = peerGroup.getAllConnectedPeers().size();
        int candidates = seeds.size() + reported.size() + persisted.size();
        int missingConnections = numConnections > 0 ?
                minNumConnectedPeers - numConnections //8
                : candidates;
        missingConnections = Math.max(0, missingConnections);

        // In case we apply the limit it will be applied at the persisted first which is intended as those are the least
        // likely to be successful.
        List<Address> all = new ArrayList<>(seeds);
        all.addAll(reported);
        all.addAll(persisted);
        all.addAll(connectedPeerAddresses);

        List<Address> result = all.stream()
                /*.limit(missingConnections)*/
                .collect(Collectors.toList());
        usedAddresses.addAll(result);
        return result;
    }

    public boolean notASeed(Address address) {
        return seedNodeAddresses.stream().noneMatch(seedAddress -> seedAddress.equals(address));
    }

    public boolean notASeed(Peer peer) {
        return notASeed(peer.getAddress());
    }

    public boolean repeatBootstrap(long numSuccess, int numFutures) {
        long failures = numFutures - numSuccess;
        boolean moreThenHalfFailed = failures > numFutures / 2;
        return numSuccess == 0 ||
                moreThenHalfFailed ||
                !sufficientConnections() ||
                !sufficientReportedPeers();
    }

    public long getRepeatBootstrapDelay() {
        return peerExchangeConfig.getRepeatPeerExchangeDelay();
    }

    private boolean sufficientReportedPeers() {
        return peerGroup.getReportedPeers().size() >= peerGroup.getConfig().getMinNumReportedPeers();
    }

    private boolean sufficientConnections() {
        return peerGroup.getAllConnectedPeers().size() >= peerGroup.getConfig().getMinNumConnectedPeers();
    }

    private boolean notUsedYet(Address address) {
        //todo check deactivated atm
        return true || !usedAddresses.contains(address);
    }

    private boolean notTargetPeer(Address peerAddress, Peer peer) {
        return !peer.getAddress().equals(peerAddress);
    }

    private List<Address> getShuffled(Collection<Address> addresses) {
        List<Address> list = new ArrayList<>(addresses);
        Collections.shuffle(list);
        return list;
    }

}
