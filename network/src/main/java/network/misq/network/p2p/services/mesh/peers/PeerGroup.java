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
import network.misq.network.p2p.node.*;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private final Config config;
    @Getter
    private final Set<Peer> reportedPeers = new CopyOnWriteArraySet<>();

    //todo persist
    @Getter
    private final Set<Peer> persistedPeers = new CopyOnWriteArraySet<>();
    @Getter
    private final Set<InboundConnection> inboundConnections = new CopyOnWriteArraySet<>();
    @Getter
    private final Set<OutboundConnection> outboundConnections = new CopyOnWriteArraySet<>();

    public PeerGroup(Node node, Config config) {
        this.node = node;
        this.config = config;
        this.node.addConnectionListener(this);
    }

    @Override
    public void onConnection(Connection connection) {
        if (connection instanceof OutboundConnection outboundConnection) {
            outboundConnections.add(outboundConnection);
        } else {
            inboundConnections.add((InboundConnection) connection);
        }
    }

    @Override
    public void onDisconnect(Connection connection) {
        if (connection instanceof OutboundConnection outboundConnection) {
            outboundConnections.remove(outboundConnection);
        } else {
            inboundConnections.remove((InboundConnection) connection);
        }
    }

    public void addReportedPeers(Set<Peer> peers) {
        reportedPeers.addAll(peers);
    }

    public void removeReportedPeers(Collection<Peer> peers) {
        reportedPeers.removeAll(peers);
    }

    public void removePersistedPeers(Collection<Peer> peers) {
        persistedPeers.removeAll(peers);
    }

    public Set<Address> getConnectedPeerAddresses() {
        return getAllConnectedPeers().stream().map(Peer::getAddress).collect(Collectors.toSet());
    }

    public Set<Peer> getAllConnectedPeers() {
        return getAllConnectionsAsStream().map(connection -> new Peer(connection.getPeersCapability(), connection.isOutboundConnection()))
                .collect(Collectors.toSet());
    }

    public Stream<Connection> getAllConnectionsAsStream() {
        return Stream.concat(outboundConnections.stream(), inboundConnections.stream());
    }

    public int getNumAllConnections() {
        return (int) getAllConnectionsAsStream().count();
    }


    public int getMinNumReportedPeers() {
        return config.getMinNumReportedPeers();
    }

    public int getMinNumConnectedPeers() {
        return config.getMinNumConnectedPeers();
    }

    public int getMaxNumConnectedPeers() {
        return config.getMaxNumConnectedPeers();
    }

    public boolean notMyself(Address address) {
        return node.findMyAddress().stream().noneMatch(myAddress -> myAddress.equals(address));
    }

    public boolean notMyself(Peer peer) {
        return notMyself(peer.getAddress());
    }

    public String getConnectionMatrix() {
        int numConnections = outboundConnections.size() + inboundConnections.size();
        StringBuilder sb = new StringBuilder();
        sb.append("Num connections: ").append(numConnections)
                .append("\n").append("Num all connections: ").append(getNumAllConnections())
                .append("\n").append("Num outbound connections: ").append(outboundConnections.size())
                .append("\n").append("Num inbound connections: ").append(inboundConnections.size())
                .append("\n");
        outboundConnections.forEach(connection -> sb.append(node).append(" --> ").append(connection.getPeerAddress().toString()).append("\n"));
        inboundConnections.forEach(connection -> sb.append(node).append(" <-- ").append(connection.getPeerAddress().toString()).append("\n"));
        return sb.toString();
    }
}
