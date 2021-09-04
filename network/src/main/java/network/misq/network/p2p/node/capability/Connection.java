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

package network.misq.network.p2p.node.capability;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import network.misq.network.p2p.node.connection.Address;
import network.misq.network.p2p.node.connection.OutboundConnection;
import network.misq.network.p2p.node.connection.RawConnection;
import network.misq.network.p2p.node.socket.NetworkType;

@Slf4j
public class Connection {

    private final RawConnection rawConnection;
    @Getter
    private final NetworkType networkType;
    @Getter
    private final String nodeId;
    @Getter
    private final Capability capability;
    @Getter
    private final String id;

    public Connection(RawConnection rawConnection, NetworkType networkType, String nodeId, Capability capability) {
        this.rawConnection = rawConnection;
        this.networkType = networkType;
        this.nodeId = nodeId;
        this.capability = capability;

        id = rawConnection.getId();
    }

    public Address getPeerAddress() {
        return capability.address();
    }

    // Only at outbound connections we can be sure that the peer address is correct.
    // The announced peer address in capability is not guaranteed to be valid.
    // For most cases that is sufficient as the peer would not gain anything if lying about their address
    // as it would make them unreachable for receiving messages from newly established connections. But there are
    // cases where we need to be sure that it is the real address, like if we might use the peer address for banning a
    // not correctly behaving peer.
    public boolean getPeerAddressVerified() {
        return isOutboundConnection();
    }

    public boolean isOutboundConnection() {
        return rawConnection instanceof OutboundConnection;
    }

    RawConnection getRawConnection() {
        return rawConnection;
    }

    @Override
    public String toString() {
        return "Connection{" +
                "\r\n     id='" + id + '\'' +
                ",\r\n     peerAddress=" + getPeerAddress() +
                ",\r\n     networkType=" + networkType +
                ",\r\n     nodeId=" + nodeId +
                ",\r\n     capability=" + capability +
                "\r\n}";
    }
}
