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

package network.misq.network.p2p.node.connection;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import network.misq.network.p2p.node.socket.SocketFactory;

import java.net.Socket;

@Slf4j
public class InboundConnection extends RawConnection {
    @Getter
    private final SocketFactory.GetServerSocketResult getServerSocketResult;

    public InboundConnection(Socket socket, SocketFactory.GetServerSocketResult getServerSocketResult) {
        super(socket);
        this.getServerSocketResult = getServerSocketResult;
        log.debug("Create inboundConnection from server: {}", getServerSocketResult);
    }

    @Override
    public String toString() {
        return getServerSocketResult + " / " + id;
    }
}
