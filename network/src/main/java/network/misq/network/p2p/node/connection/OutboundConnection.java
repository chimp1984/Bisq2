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
import network.misq.common.util.StringUtils;
import network.misq.network.p2p.message.Message;
import network.misq.network.p2p.node.Address;
import network.misq.network.p2p.node.Capability;
import network.misq.network.p2p.node.MessageListener;

import java.net.Socket;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Slf4j
public class OutboundConnection extends Connection {

    @Getter
    private final Address address;

    public OutboundConnection(Socket socket,
                              Address address,
                              Capability capability,
                              BiConsumer<Message, Connection> messageHandler) {
        super(socket, capability, messageHandler);

        this.address = address;
        log.debug("Create outboundConnection to {}", address);
    }

    @Override
    public String toString() {
        return StringUtils.truncate(address.toString()) + " / " + id;
    }
}
