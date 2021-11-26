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

package network.misq.network.p2p.services.mesh.peers.exchange;

import lombok.extern.slf4j.Slf4j;
import network.misq.network.p2p.BaseNetworkTest;
import network.misq.network.p2p.node.Address;
import network.misq.network.p2p.node.Node;
import network.misq.network.p2p.services.mesh.peers.PeerGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
public abstract class BasePeerExchangeServiceTest extends BaseNetworkTest {
    void test_peerExchange(Node.Config nodeConfig) throws InterruptedException, ExecutionException {
        int numSeeds = 7;
        int numNodes = 3;

        List<Address> seedNodeAddresses = new ArrayList<>();
        for (int i = 0; i < numSeeds; i++) {
            int port = 1000 + i;
            Address address = Address.localHost(port);
            seedNodeAddresses.add(address);
        }

        CountDownLatch initSeedsLatch = new CountDownLatch(numNodes);
        List<Node> seeds = new ArrayList<>();
        for (int i = 0; i < numSeeds; i++) {
            int port = 1000 + i;
            Node seed = new Node(nodeConfig, "seed_" + i);
            seeds.add(seed);
            seed.initializeServer(port).whenComplete((r, t) -> {
                initSeedsLatch.countDown();
            });

            PeerExchangeStrategy peerExchangeStrategy = new PeerExchangeStrategy(new PeerGroup(seed, new PeerGroup.Config()), seedNodeAddresses, new PeerExchangeStrategy.Config());
            new PeerExchangeService(seed, peerExchangeStrategy);
        }
        assertTrue(initSeedsLatch.await(getTimeout(), TimeUnit.SECONDS));

        int numHandshakes = Math.min(seeds.size(), 2);
        CountDownLatch initNodesLatch = new CountDownLatch(numNodes);


        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < numNodes; i++) {
            int port = 3000 + i;
            Node node = new Node(nodeConfig, "node_" + i);
            nodes.add(node);
            node.initializeServer(port).whenComplete((r, t) -> {
                initNodesLatch.countDown();
            });
        }
        assertTrue(initNodesLatch.await(getTimeout(), TimeUnit.SECONDS));

        for (int i = 0; i < numNodes; i++) {
            Node node = nodes.get(i);
            PeerExchangeStrategy peerExchangeStrategy = new PeerExchangeStrategy(new PeerGroup(node, new PeerGroup.Config()), seedNodeAddresses, new PeerExchangeStrategy.Config());
            PeerExchangeService peerExchangeService = new PeerExchangeService(node, peerExchangeStrategy);
            peerExchangeService.startPeerExchange().whenComplete((result, throwable) -> {
                assertNull(throwable);
                assertTrue(result);
            }).join();
        }

        // close some seeds and check if we get the fault handler called
        int numSeedsClosed = Math.max(0, numSeeds - numHandshakes + 1);
        if (numSeedsClosed > 0) {
            for (int i = 0; i < numSeedsClosed; i++) {
                seeds.get(i).shutdown().get();
            }

            for (int i = 0; i < numNodes; i++) {
                Node node = nodes.get(i);
                PeerExchangeStrategy peerExchangeStrategy = new PeerExchangeStrategy(new PeerGroup(node, new PeerGroup.Config()), seedNodeAddresses, new PeerExchangeStrategy.Config());
                PeerExchangeService peerExchangeService = new PeerExchangeService(node, peerExchangeStrategy);
                peerExchangeService.startPeerExchange().whenComplete((result, throwable) -> {
                    assertNull(throwable);
                    assertTrue(result);
                }).join();
            }
        }
    }
}