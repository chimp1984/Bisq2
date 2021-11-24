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

package network.misq.protocol;

import network.misq.contract.*;
import network.misq.network.p2p.MultiAddress;
import network.misq.network.p2p.node.Address;
import network.misq.network.p2p.node.transport.TransportType;
import network.misq.offer.Listing;
import network.misq.security.PubKey;

import java.util.Map;

public class ContractMaker {
    public static TwoPartyContract createMakerTrade(MultiAddress takerMultiAddress, ProtocolType protocolType) {
        Party counterParty = new Party(takerMultiAddress);
        return new TwoPartyContract(protocolType, Role.MAKER, counterParty);
    }

    public static TwoPartyContract createTakerTrade(Listing offer, ProtocolType protocolType) {
        MultiAddress makerMultiAddress = offer.getMakerMultiAddress();
        Party counterParty = new Party(makerMultiAddress);
        return new TwoPartyContract(protocolType, Role.TAKER, counterParty);
    }

    public static ManyPartyContract createMakerTrade(MultiAddress takerMultiAddress, MultiAddress escrowAgentMultiAddress, ProtocolType protocolType) {
        Party taker = new Party(takerMultiAddress);
        Party escrowAgent = new Party(escrowAgentMultiAddress);
        return new ManyPartyContract(protocolType, Role.MAKER, Map.of(Role.MAKER, self(), Role.TAKER, taker, Role.ESCROW_AGENT, escrowAgent));
    }

    public static ManyPartyContract createTakerTrade(Listing offer, MultiAddress escrowAgentMultiAddress, ProtocolType protocolType) {
        MultiAddress makerMultiAddress = offer.getMakerMultiAddress();
        Party maker = new Party(makerMultiAddress);
        Party escrowAgent = new Party(escrowAgentMultiAddress);
        return new ManyPartyContract(protocolType, Role.TAKER, Map.of(Role.MAKER, maker, Role.TAKER, self(), Role.ESCROW_AGENT, escrowAgent));
    }

    private static Party self() {
        return new Party(new MultiAddress(Map.of(TransportType.CLEAR_NET, Address.localHost(1000)), new PubKey(null, "default")));
    }
}
