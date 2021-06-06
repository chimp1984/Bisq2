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

package network.misq.protocol;


import network.misq.network.Address;
import network.misq.network.NetworkId;
import network.misq.network.NetworkService;
import network.misq.network.NetworkType;
import network.misq.network.data.filter.DataFilter;
import network.misq.network.data.inventory.RequestInventoryResult;
import network.misq.network.message.Message;
import network.misq.network.node.Connection;
import network.misq.network.node.MessageListener;
import network.misq.network.node.proxy.GetServerSocketResult;
import network.misq.network.router.gossip.GossipResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.KeyPair;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class MockNetworkService extends NetworkService {
    private static final Logger log = LoggerFactory.getLogger(MockNetworkService.class);
    private final Set<MessageListener> messageListeners = ConcurrentHashMap.newKeySet();

    public MockNetworkService() {
        super();
    }

    @Override
    public CompletableFuture<Boolean> initializeServer(BiConsumer<GetServerSocketResult, Throwable> resultHandler) {
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public CompletableFuture<Boolean> bootstrap() {
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public CompletableFuture<Connection> confidentialSend(Message message, NetworkId networkId, KeyPair myKeyPair) {
        CompletableFuture<Connection> future = new CompletableFuture<>();
        new Thread(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignore) {
            }
            future.complete(null);

            try {
                Thread.sleep(100);
            } catch (InterruptedException ignore) {
            }
            messageListeners.forEach(e -> e.onMessage(message, null));
        }).start();

        return future;
    }

    @Override
    public void requestAddData(Message message,
                               Consumer<GossipResult> resultHandler) {
    }

    @Override
    public void requestRemoveData(Message message,
                                  Consumer<GossipResult> resultHandler) {
    }

    @Override
    public void requestInventory(DataFilter dataFilter,
                                 Consumer<RequestInventoryResult> resultHandler) {
    }

    @Override
    public void addMessageListener(MessageListener messageListener) {
        messageListeners.add(messageListener);
    }

    @Override
    public void removeMessageListener(MessageListener messageListener) {
    }

    @Override
    public void shutdown() {
    }

    @Override
    public Optional<Address> findMyAddress(NetworkType networkType) {
        return Optional.empty();
    }
}
