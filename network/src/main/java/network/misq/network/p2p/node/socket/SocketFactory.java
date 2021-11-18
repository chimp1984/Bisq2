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

package network.misq.network.p2p.node.socket;

import network.misq.common.util.StringUtils;
import network.misq.network.p2p.NetworkConfig;
import network.misq.network.p2p.node.Address;

import java.io.Closeable;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface SocketFactory extends Closeable {
    static SocketFactory from(NetworkConfig networkConfig) {
        return switch (networkConfig.getNetworkType()) {
            case TOR -> new TorSocketFactory(networkConfig);
            case I2P -> new I2pSocketFactory(networkConfig);
            case CLEAR -> new ClearNetSocketFactory(networkConfig);
        };
    }

    CompletableFuture<Boolean> initialize();

    CompletableFuture<GetServerSocketResult> getServerSocket(String serverId, int serverPort);

    Socket getSocket(Address address) throws IOException;

    Optional<Address> getServerAddress(String serverId);

    void close();

    record GetServerSocketResult(String serverId, ServerSocket serverSocket, Address address) {
        @Override
        public String toString() {
            return serverId + " @ " + StringUtils.truncate(address.toString());
        }
    }
}
