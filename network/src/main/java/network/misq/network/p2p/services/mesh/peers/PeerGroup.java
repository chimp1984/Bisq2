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

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import network.misq.network.p2p.node.Address;
import network.misq.network.p2p.node.Connection;
import network.misq.network.p2p.node.ConnectionListener;
import network.misq.network.p2p.node.Node;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Maintains different collections of peers and connections
 */
@Slf4j
public class PeerGroup implements ConnectionListener {
    @Getter
    public static class Config {
        private final int minNumConnectedPeers;
        private final int maxNumConnectedPeers;
        private final int minNumReportedPeers;

        public Config() {
            this(8, 12, 1);
        }

        public Config(int minNumConnectedPeers,
                      int maxNumConnectedPeers,
                      int minNumReportedPeers) {
            this.minNumConnectedPeers = minNumConnectedPeers;
            this.maxNumConnectedPeers = maxNumConnectedPeers;
            this.minNumReportedPeers = minNumReportedPeers;
        }
    }


    private final Node node;
    @Getter
    private final Config config;
    @Getter
    private final Map<Address, Peer> connectedPeerByAddress = new ConcurrentHashMap<>();
    @Getter
    private final Map<String, Connection> connectionsById = new ConcurrentHashMap<>();

    @Getter
    private final Set<Peer> reportedPeers = new CopyOnWriteArraySet<>();
    @Getter
    private final Set<Peer> persistedPeers = new CopyOnWriteArraySet<>();

    @Getter
    private final Set<Connection> inboundConnections = new CopyOnWriteArraySet<>();
    @Getter
    private final Set<Connection> outboundConnections = new CopyOnWriteArraySet<>();

    public PeerGroup(Node node, Config config) {
        this.node = node;
        this.config = config;
        node.addConnectionListener(this);
    }

    @Override
    public void onConnection(Connection connection) {
        Peer peer = new Peer(connection.getPeersCapability());
        connectedPeerByAddress.put(peer.getAddress(), peer); //todo inbound and outbound could conflict
        connectionsById.put(connection.getId(), connection);
    }

    @Override
    public void onDisconnect(Connection connection) {
        connectedPeerByAddress.remove(connection.getPeerAddress());//todo inbound and outbound could conflict
        connectionsById.remove(connection.getId());
    }

    public void addReportedPeers(Set<Peer> peers) {
        reportedPeers.addAll(peers);
    }

    public Set<Address> getConnectedPeerAddresses() {
        return connectedPeerByAddress.keySet();
    }

    public Collection<Peer> getConnectedPeerByAddress() {
        return connectedPeerByAddress.values();
    }

    public Set<Peer> getAllConnectedPeers() {
        return new HashSet<>(connectedPeerByAddress.values());
    }

    public boolean notMyself(Address address) {
        return node.getMyAddress().stream().noneMatch(myAddress -> myAddress.equals(address));
    }

    public boolean notMyself(Peer peer) {
        return notMyself(peer.getAddress());
    }

}
