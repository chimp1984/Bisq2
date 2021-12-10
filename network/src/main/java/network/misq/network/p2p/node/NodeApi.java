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

package network.misq.network.p2p.node;

import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;
import network.misq.network.p2p.message.Message;
import network.misq.network.p2p.node.transport.Transport;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface NodeApi {
    String DEFAULT_NODE_ID = "default";

    CompletableFuture<Transport.ServerSocketResult> initializeServer(int port);

    CompletableFuture<Connection> send(Message message, Address address);

    CompletableFuture<Connection> send(Message message, Connection connection);

    CompletableFuture<Connection> getConnection(Address address);

    CompletableFuture<Void> shutdown();

    Optional<Socks5Proxy> getSocksProxy() throws IOException;

    void addMessageListener(MessageListener messageListener);

    void removeMessageListener(MessageListener messageListener);

    void addConnectionListener(ConnectionListener connectionListener);

    void removeConnectionListener(ConnectionListener connectionListener);

    Optional<Address> findMyAddress();
}
