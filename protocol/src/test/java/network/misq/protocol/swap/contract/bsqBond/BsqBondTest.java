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

package network.misq.protocol.swap.contract.bsqBond;


import lombok.extern.slf4j.Slf4j;
import network.misq.account.FiatTransferType;
import network.misq.contract.AssetTransfer;
import network.misq.contract.ProtocolType;
import network.misq.contract.SwapProtocolType;
import network.misq.contract.TwoPartyContract;
import network.misq.network.Address;
import network.misq.network.NetworkId;
import network.misq.network.NetworkService;
import network.misq.offer.Asset;
import network.misq.offer.Offer;
import network.misq.protocol.ContractMaker;
import network.misq.protocol.MockNetworkService;
import network.misq.protocol.ProtocolExecutor;
import network.misq.protocol.bsqBond.BsqBondProtocol;
import network.misq.protocol.bsqBond.maker.MakerBsqBondProtocol;
import network.misq.protocol.bsqBond.taker.TakerBsqBondProtocol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@Slf4j
public class BsqBondTest {

    private NetworkService networkService;

    @BeforeEach
    public void setup() {
        // We share a network mock to call MessageListeners when sending a msg (e.g. alice send a msg and
        // bob receives the event)
        networkService = new MockNetworkService();
    }

    @Test
    public void testBsqBond() {

        // create offer
        NetworkId makerNetworkId = new NetworkId(Address.localHost(3333), null, "default");

        Asset askAsset = new Asset("USD", 100, List.of(FiatTransferType.ZELLE), AssetTransfer.Type.MANUAL);
        Asset bidAsset = new Asset("EUR", 90, List.of(FiatTransferType.REVOLUT, FiatTransferType.SEPA), AssetTransfer.Type.MANUAL);
        Offer offer = new Offer(List.of(SwapProtocolType.MULTISIG, SwapProtocolType.REPUTATION),
                makerNetworkId, bidAsset, askAsset);

        // taker takes offer and selects first ProtocolType
        ProtocolType selectedProtocolType = offer.getProtocolTypes().get(0);
        TwoPartyContract takerTrade = ContractMaker.createTakerTrade(offer, selectedProtocolType);
        TakerBsqBondProtocol takerBsqBondProtocol = new TakerBsqBondProtocol(takerTrade, networkService);
        ProtocolExecutor takerSwapTradeProtocolExecutor = new ProtocolExecutor(takerBsqBondProtocol);

        // simulated take offer protocol: Taker sends to maker the selectedProtocolType
        NetworkId takerNetworkId = new NetworkId(Address.localHost(3333), null, "default");
        TwoPartyContract makerTrade = ContractMaker.createMakerTrade(takerNetworkId, selectedProtocolType);
        MakerBsqBondProtocol makerBsqBondProtocol = new MakerBsqBondProtocol(makerTrade, networkService);
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
