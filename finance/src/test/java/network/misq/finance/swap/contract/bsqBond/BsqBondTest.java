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

package network.misq.finance.swap.contract.bsqBond;


import lombok.extern.slf4j.Slf4j;
import network.misq.MockP2pService;
import network.misq.account.FiatTransferType;
import network.misq.finance.Asset;
import network.misq.finance.ContractMaker;
import network.misq.finance.ProtocolType;
import network.misq.finance.contract.ProtocolExecutor;
import network.misq.finance.contract.TwoPartyContract;
import network.misq.finance.swap.SwapProtocolType;
import network.misq.finance.swap.contract.bsqBond.maker.MakerBsqBondProtocol;
import network.misq.finance.swap.contract.bsqBond.taker.TakerBsqBondProtocol;
import network.misq.finance.swap.offer.SwapOffer;
import network.misq.p2p.Address;
import network.misq.p2p.NetworkId;
import network.misq.p2p.P2pService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@Slf4j
public class BsqBondTest {

    private P2pService p2pService;

    @BeforeEach
    public void setup() {
        // We share a network mock to call MessageListeners when sending a msg (e.g. alice send a msg and
        // bob receives the event)
        p2pService = new MockP2pService();
    }

    @Test
    public void testBsqBond() {

        // create offer
        NetworkId makerNetworkId = new NetworkId(Address.localHost(3333), null, "default");

        Asset askAsset = new Asset("USD", 100, List.of(FiatTransferType.ZELLE));
        Asset bidAsset = new Asset("EUR", 90, List.of(FiatTransferType.REVOLUT, FiatTransferType.SEPA));
        SwapOffer offer = new SwapOffer(List.of(SwapProtocolType.MULTISIG, SwapProtocolType.REPUTATION),
                makerNetworkId, bidAsset, askAsset);

        // taker takes offer and selects first ProtocolType
        ProtocolType selectedProtocolType = offer.getProtocolTypes().get(0);
        TwoPartyContract takerTrade = ContractMaker.createTakerTrade(offer, selectedProtocolType);
        TakerBsqBondProtocol takerBsqBondProtocol = new TakerBsqBondProtocol(takerTrade, p2pService);
        ProtocolExecutor takerSwapTradeProtocolExecutor = new ProtocolExecutor(takerBsqBondProtocol);

        // simulated take offer protocol: Taker sends to maker the selectedProtocolType
        NetworkId takerNetworkId = new NetworkId(Address.localHost(3333), null, "default");
        TwoPartyContract makerTrade = ContractMaker.createMakerTrade(takerNetworkId, selectedProtocolType);
        MakerBsqBondProtocol makerBsqBondProtocol = new MakerBsqBondProtocol(makerTrade, p2pService);
        ProtocolExecutor makerSwapTradeProtocolExecutor = new ProtocolExecutor(makerBsqBondProtocol);

        CountDownLatch completedLatch = new CountDownLatch(2);
        makerSwapTradeProtocolExecutor.getProtocol().addListener(state -> {
            if (state instanceof BsqBondProtocol.State) {
                var completedState = (BsqBondProtocol.State) state;
                if (completedState == BsqBondProtocol.State.FUNDS_RECEIVED) {
                    completedLatch.countDown();
                }
            }
        });
        takerSwapTradeProtocolExecutor.getProtocol().addListener(state -> {
            if (state instanceof BsqBondProtocol.State) {
                var completedState = (BsqBondProtocol.State) state;
                if (completedState == BsqBondProtocol.State.FUNDS_RECEIVED) {
                    completedLatch.countDown();
                }
            }
        });

        makerSwapTradeProtocolExecutor.start();
        takerSwapTradeProtocolExecutor.start();

        try {
            boolean completed = completedLatch.await(10, TimeUnit.SECONDS);
            assertTrue(completed);
        } catch (Throwable e) {
            fail(e.toString());
        }
    }
}
