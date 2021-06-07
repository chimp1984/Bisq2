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

package network.misq.api.options;

import com.google.common.collect.Sets;
import lombok.Getter;
import network.misq.application.options.ApplicationOptions;
import network.misq.network.NetworkService;
import network.misq.network.http.MarketPriceService;
import network.misq.network.p2p.NetworkConfig;
import network.misq.network.p2p.NetworkType;
import network.misq.network.p2p.NodeId;
import network.misq.network.p2p.P2pService;

import java.util.HashSet;
import java.util.Set;

/**
 * Parses the program arguments which are relevant for that domain and stores it in the options field.
 */
public class NetworkServiceOptionsParser {
    @Getter
    private final NetworkService.Options options;

    public NetworkServiceOptionsParser(ApplicationOptions applicationOptions, String[] args) {
        //todo NetworkService options structure is preliminary
        NodeId nodeId = new NodeId("default", 8888, Sets.newHashSet(NetworkType.CLEAR));
        Set<NetworkConfig> networkConfigs = new HashSet<>();
        networkConfigs.add(new NetworkConfig(applicationOptions.appDir(), nodeId, NetworkType.CLEAR));

        P2pService.Option p2pServiceOption = new P2pService.Option(applicationOptions.appDir(), networkConfigs);
        MarketPriceService.Option marketPriceServiceOption = new MarketPriceService.Option("todo url");
        options = new NetworkService.Options(p2pServiceOption, marketPriceServiceOption);
    }
}