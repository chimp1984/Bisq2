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

import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import network.misq.network.p2p.message.Message;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@Slf4j
public abstract class BaseNodeRepositoryTest {
    void test_messageRoundTrip(NodeConfig nodeConfig) throws InterruptedException {
        NodeRepository nodeRepository = new NodeRepository(nodeConfig);
        long ts = System.currentTimeMillis();
        // Thread.sleep(6000);
        int numNodes = 100;
        int numRepeats = 1;
        for (int i = 0; i < numRepeats; i++) {
            log.error("Iteration {}", i);
            messageRoundTrip(numNodes, nodeRepository);
        }
        log.error("MessageRoundTrip for {} nodes repeated {} times took {} ms", numNodes, numRepeats, System.currentTimeMillis() - ts);
        // Thread.sleep(6000000);
    }

    private void messageRoundTrip(int numNodes, NodeRepository nodeRepository) throws InterruptedException {
        long ts = System.currentTimeMillis();
        CountDownLatch initializeServerLatch = new CountDownLatch(numNodes);
        CountDownLatch sendPongLatch = new CountDownLatch(numNodes);
        CountDownLatch receivedPongLatch = new CountDownLatch(numNodes);
        for (int i = 0; i < numNodes; i++) {
            Node node = nodeRepository.getOrCreateNode("node_" + i);
            int finalI = i;
            node.initializeServer(1000 + i).whenComplete((serverSocketResult, t) -> {
                if (t != null) {
                    fail(t);
                }
                initializeServerLatch.countDown();
                node.addMessageListener((message, connection) -> {
                    log.info("Received " + message.toString());
                    if (message instanceof ClearNetNodeRepositoryTest.Ping) {
                        ClearNetNodeRepositoryTest.Pong pong = new ClearNetNodeRepositoryTest.Pong("Pong from " + finalI + " to " + connection.getPeerAddress().getPort());
                        log.info("Send pong " + pong);
                        node.send(pong, connection).whenComplete((r2, t2) -> {
                            if (t2 != null) {
                                fail(t2);
                            }
                            log.info("Send pong completed " + pong);
                            sendPongLatch.countDown();
                        });
                    } else if (message instanceof ClearNetNodeRepositoryTest.Pong) {
                        receivedPongLatch.countDown();
                    }
                });
            });
        }
        log.error("init started {} ms", System.currentTimeMillis() - ts);

        ts = System.currentTimeMillis();
        assertTrue(initializeServerLatch.await(getTimeout(), TimeUnit.SECONDS));
        log.error("init completed after {} ms", System.currentTimeMillis() - ts);

        ts = System.currentTimeMillis();
        CountDownLatch sendPingLatch = new CountDownLatch(numNodes);
        for (int i = 0; i < numNodes; i++) {
            Node node = nodeRepository.getOrCreateNode("node_" + i);
            int receiverIndex = (i + 1) % numNodes;
            Node receiverNode = nodeRepository.getOrCreateNode("node_" + receiverIndex);
            Address receiverNodeAddress = receiverNode.getMyAddress().get();
            ClearNetNodeRepositoryTest.Ping ping = new ClearNetNodeRepositoryTest.Ping("Ping from " + node.getMyAddress().get() + " to " + receiverNodeAddress);
            log.info("Send ping " + ping);
            node.send(ping, receiverNodeAddress).whenComplete((r, t) -> {
                if (t != null) {
                    fail(t);
                }
                log.info("Send ping completed " + ping);
                sendPingLatch.countDown();
            });
        }
        log.error("Send ping took {} ms", System.currentTimeMillis() - ts);

        ts = System.currentTimeMillis();
        assertTrue(sendPingLatch.await(getTimeout(), TimeUnit.SECONDS));
        log.error("Send ping completed after {} ms", System.currentTimeMillis() - ts);


        ts = System.currentTimeMillis();
        assertTrue(sendPongLatch.await(getTimeout(), TimeUnit.SECONDS));
        log.error("Send pong completed after {} ms", System.currentTimeMillis() - ts);

        ts = System.currentTimeMillis();
        assertTrue(receivedPongLatch.await(getTimeout(), TimeUnit.SECONDS));
        log.error("Receive pong completed after {} ms", System.currentTimeMillis() - ts);


        ts = System.currentTimeMillis();
        nodeRepository.shutdown();
        log.error("shutdown took {} ms", System.currentTimeMillis() - ts);
    }

    void test_initializeServer(NodeConfig nodeConfig) throws InterruptedException {
        NodeRepository nodeRepository = new NodeRepository(nodeConfig);
        //Thread.sleep(6000);
        for (int i = 0; i < 2; i++) {
            initializeNodes(2, nodeRepository);
        }
        //Thread.sleep(6000000);
    }

    private void initializeNodes(int numNodes, NodeRepository nodeRepository) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(numNodes);
        for (int i = 0; i < numNodes; i++) {
            Node node = nodeRepository.getOrCreateNode("node_" + i);
            node.initializeServer(1000 + i).whenComplete((r, t) -> {
                if (t != null) {
                    fail(t);
                }
                latch.countDown();
            });
        }
        boolean result = latch.await(getTimeout(), TimeUnit.SECONDS);
        nodeRepository.shutdown();
        assertTrue(result);
    }

    protected abstract long getTimeout();

    @ToString
    public static class Ping implements Message {
        public final String msg;

        public Ping(String msg) {
            this.msg = msg;
        }
    }

    @ToString
    public static class Pong implements Message {
        public final String msg;

        public Pong(String msg) {
            this.msg = msg;
        }
    }
}