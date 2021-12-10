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

package network.misq.network.p2p.services.mesh.peers.keepalive;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import network.misq.network.p2p.message.Message;
import network.misq.network.p2p.node.Connection;
import network.misq.network.p2p.node.MessageListener;
import network.misq.network.p2p.node.Node;

import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Getter
@Slf4j
class KeepAliveHandler implements MessageListener {
    private static final long TIMEOUT = 90;

    private final Node node;
    private final CompletableFuture<Void> future = new CompletableFuture<>();
    private final int nonce;

    KeepAliveHandler(Node node) {
        this.node = node;
        nonce = new Random().nextInt();
        node.addMessageListener(this);
        log.error("new KeepAliveHandler at node {} nonce={}", node.print(), nonce);
    }

    @Override
    public void onMessage(Message message, Connection connection, String nodeId) {
        if (message instanceof Pong pong) {
            if (pong.requestNonce() == nonce) {
                log.info("Node {} received Pong from {} with nonce {}. Connection={}",
                        node.print(), connection.getPeerAddress().print(), pong.requestNonce(), connection.getId());
                node.removeMessageListener(this);
                future.complete(null);
            } else {
                log.debug("Expected case if we receive message from different connection. " +
                                "Node {} received Pong from {} with invalid nonce {}. Request nonce was {}. Connection={}",
                        node.print(), connection.getPeerAddress().print(), pong.requestNonce(), nonce, connection.getId());
            }
        }
    }

    CompletableFuture<Void> sendPing(Connection connection) {
        future.orTimeout(TIMEOUT, TimeUnit.SECONDS);
        log.info("Node {} send Ping to {} with nonce {}. Connection={}",
                node.print(), connection.getPeerAddress().print(), nonce, connection.getId());
        node.send(new Ping(nonce), connection)
                .whenComplete((c, throwable) -> {
                    if (throwable != null) {
                        future.completeExceptionally(throwable);
                        dispose();
                    }
                });
        return future;
    }

    void dispose() {
        node.removeMessageListener(this);
        future.cancel(true);
    }
}