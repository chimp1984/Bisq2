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

import lombok.extern.slf4j.Slf4j;
import network.misq.common.timer.TimerUtil;
import network.misq.network.p2p.message.Message;
import network.misq.network.p2p.node.Connection;
import network.misq.network.p2p.node.ConnectionListener;
import network.misq.network.p2p.node.Node;
import network.misq.network.p2p.services.mesh.peers.PeerGroup;

import java.util.Map;
import java.util.Timer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
public class KeepAliveService implements Node.MessageListener, ConnectionListener {
    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(30);

    public static record Config(long maxIdleTime, long interval) {
    }

    private final Node node;
    private final PeerGroup peerGroup;
    private final Config config;
    private final Map<String, KeepAliveHandler> requestHandlerMap = new ConcurrentHashMap<>();
    private Timer timer;

    public KeepAliveService(Node node, PeerGroup peerGroup, Config config) {
        this.node = node;
        this.peerGroup = peerGroup;
        this.config = config;
        this.node.addMessageListener(this);
        node.addConnectionListener(this);
    }

    public void initialize() {
        timer = TimerUtil.runPeriodically(this::sendPingIfRequired, config.interval(), TimeUnit.MILLISECONDS);
    }

    private void sendPingIfRequired() {
        peerGroup.getAllConnectionsAsStream()
                .filter(this::isRequired)
                .forEach(this::sendPing);
    }

    private void sendPing(Connection connection) {
        String key = connection.getId();
        if (requestHandlerMap.containsKey(key)) {
            log.warn("requestHandlerMap contains already {}. " +
                    "We dispose the existing handler and start a new one. Connection={}", connection.getPeerAddress().toString(), connection);
            requestHandlerMap.get(key).dispose();
        }
        KeepAliveHandler handler = new KeepAliveHandler(node, connection);
        requestHandlerMap.put(key, handler);
        handler.sendPing()
                .orTimeout(TIMEOUT, TimeUnit.SECONDS)
                .whenComplete((__, throwable) -> requestHandlerMap.remove(key));
    }

    public void shutdown() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        requestHandlerMap.values().forEach(KeepAliveHandler::dispose);
        requestHandlerMap.clear();
    }

    @Override
    public void onMessage(Message message, Connection connection, String nodeId) {
        if (message instanceof Ping ping) {
            String peerAddress = connection.getPeerAddress().toString();
            log.debug("Node {} received Ping with nonce {} from {}", node, ping.nonce(), peerAddress);
            node.send(new Pong(ping.nonce()), connection);
            log.debug("Node {} sent Pong with nonce {} to {}. Connection={}", node, ping.nonce(), peerAddress, connection.getId());
        }
    }

    @Override
    public void onConnection(Connection connection) {
    }

    @Override
    public void onDisconnect(Connection connection) {
        String key = connection.getId();
        if (requestHandlerMap.containsKey(key)) {
            requestHandlerMap.get(key).dispose();
            requestHandlerMap.remove(key);
        }
    }

    private boolean isRequired(Connection connection) {
        return System.currentTimeMillis() - connection.getMetrics().getLastUpdate().get() > config.maxIdleTime();
    }

}