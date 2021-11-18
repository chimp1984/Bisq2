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

import lombok.extern.slf4j.Slf4j;
import network.misq.common.util.OsUtils;
import network.misq.network.p2p.node.connection.authorization.UnrestrictedAuthorizationService;
import network.misq.network.p2p.node.proxy.NetworkProxyConfig;
import network.misq.network.p2p.node.proxy.NetworkType;
import org.junit.jupiter.api.Test;

import java.util.Set;

@Slf4j
public class ClearNetNodeRepositoryTest extends BaseNodeRepositoryTest {
    private static String baseDirName = OsUtils.getUserDataDir().getAbsolutePath() + "/misq_test_testServerSetup";
    private static NetworkProxyConfig networkProxyConfig = new NetworkProxyConfig(baseDirName);
    private static NodeConfig nodeConfig = new NodeConfig(NetworkType.CLEAR,
            Set.of(NetworkType.CLEAR),
            new UnrestrictedAuthorizationService(),
            networkProxyConfig);

    @Test
    void test_messageRoundTrip() throws InterruptedException {
        super.test_messageRoundTrip(nodeConfig);
    }

    @Test
    void test_initializeServer() throws InterruptedException {
        super.test_initializeServer(nodeConfig);
    }

    @Override
    protected long getTimeout() {
        return 2;
    }
}