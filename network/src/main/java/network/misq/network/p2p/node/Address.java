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

package network.misq.network.p2p.node;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import network.misq.common.util.NetworkUtils;
import network.misq.common.util.StringUtils;
import network.misq.network.p2p.node.transport.TransportType;

import java.io.Serializable;
import java.util.StringTokenizer;

@EqualsAndHashCode
@Getter
public class Address implements Serializable {
    public static Address localHost(int port) {
        return new Address("127.0.0.1", port);
    }

    private final String host;
    private final int port;

    public Address(String fullAddress) {
        StringTokenizer st = new StringTokenizer(fullAddress, ":");
        if (st.countTokens() == 2) {
            this.host = st.nextToken();
            this.port = Integer.parseInt(st.nextToken());
        } else {
            this.host = st.nextToken();
            this.port = -1;
        }
    }

    public Address(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public boolean isTor() {
        return NetworkUtils.isTorAddress(host);
    }

    public boolean isClearNet() {
        return NetworkUtils.isClearNetAddress(host);
    }

    public boolean isI2p() {
        return NetworkUtils.isI2pAddress(host);
    }

    public TransportType getNetworkType() {
        if (isTor()) {
            return TransportType.TOR;
        } else if (isClearNet()) {
            return TransportType.CLEAR_NET;
        } else if (isI2p()) {
            return TransportType.I2P;
        } else
            throw new IllegalArgumentException("NetworkType cannot be derived from address. " + this);
    }

    public String toShortString() {
        return StringUtils.truncate(host) + ":" + port;
    }

    @Override
    public String toString() {
        return host + ":" + port;
    }
}
