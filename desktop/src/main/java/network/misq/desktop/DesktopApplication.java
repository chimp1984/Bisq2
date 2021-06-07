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

package network.misq.desktop;

import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import network.misq.api.StandardApi;
import network.misq.application.Executable;
import network.misq.common.util.OsUtils;
import network.misq.network.NetworkService;
import network.misq.network.http.MarketPriceService;
import network.misq.network.p2p.NetworkConfig;
import network.misq.network.p2p.NetworkType;
import network.misq.network.p2p.NodeId;
import network.misq.network.p2p.P2pService;
import network.misq.security.KeyPairRepository;

import java.util.HashSet;
import java.util.Set;

@Slf4j
public class DesktopApplication extends Executable {
    private StageController stageController;
    protected StandardApi api;

    public DesktopApplication() {
        super();
    }

    @Override
    protected void setupApi() {
        String appDirPath = OsUtils.getUserDataDir() + appName;
        NodeId nodeId = new NodeId("default", 8888, Sets.newHashSet(NetworkType.CLEAR));
        Set<NetworkConfig> networkConfigs = new HashSet<>();
        networkConfigs.add(new NetworkConfig(appDirPath, nodeId, NetworkType.CLEAR));

        KeyPairRepository.Option keyPairRepositoryOptions = new KeyPairRepository.Option(appDirPath);
        P2pService.Option p2pServiceOption = new P2pService.Option(appDirPath, networkConfigs);
        MarketPriceService.Option marketPriceServiceOption = new MarketPriceService.Option("TODO MarketPriceService URL");
        NetworkService.Option networkServiceOptions = new NetworkService.Option(p2pServiceOption, marketPriceServiceOption);
        api = new StandardApi(keyPairRepositoryOptions, networkServiceOptions);
    }

    @Override
    protected void launchApplication() {
        stageController = new StageController(api);
        stageController.launchApplication().whenComplete((success, throwable) -> {
            log.info("Java FX Application initialized");
            applicationLaunched();
        });
    }

    @Override
    protected void applicationLaunched() {
        api.initialize();
    }
}
