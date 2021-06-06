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

package network.misq.trade.bsqBond.maker;


import lombok.extern.slf4j.Slf4j;
import network.misq.contract.AssetTransfer;
import network.misq.contract.TwoPartyContract;
import network.misq.network.NetworkService;
import network.misq.network.message.Message;
import network.misq.network.node.Connection;
import network.misq.trade.bsqBond.BsqBond;
import network.misq.trade.bsqBond.BsqBondProtocol;
import network.misq.trade.bsqBond.taker.TakerCommitmentMessage;
import network.misq.trade.bsqBond.taker.TakerFundsSentMessage;

import java.util.concurrent.CompletableFuture;

@Slf4j
public class MakerBsqBondProtocol extends BsqBondProtocol {
    public MakerBsqBondProtocol(TwoPartyContract contract, NetworkService p2pService) {
        super(contract, p2pService, new AssetTransfer.Automatic(), new BsqBond());
    }

    @Override
    public void onMessage(Message message, Connection connection) {
        if (message instanceof TakerCommitmentMessage) {
            TakerCommitmentMessage bondCommitmentMessage = (TakerCommitmentMessage) message;
            security.verifyBondCommitmentMessage(bondCommitmentMessage)
                    .whenComplete((success, t) -> setState(State.COMMITMENT_RECEIVED))
                    .thenCompose(isValid -> transport.sendFunds(contract))
                    .thenCompose(isSent -> p2pService.confidentialSend(new MakerFundsSentMessage(),
                            counterParty.getMakerNetworkId(),
                            null))
                    .whenComplete((connection1, t) -> setState(State.FUNDS_SENT));
        }
        if (message instanceof TakerFundsSentMessage) {
            TakerFundsSentMessage fundsSentMessage = (TakerFundsSentMessage) message;
            security.verifyFundsSentMessage(fundsSentMessage)
                    .whenComplete((isValid, t) -> {
                        if (isValid) {
                            setState(State.FUNDS_RECEIVED);
                        }
                    });
        }
    }

    public CompletableFuture<Boolean> start() {
        p2pService.addMessageListener(this);
        setState(State.START);
        security.getCommitment(contract)
                .thenCompose(commitment -> p2pService.confidentialSend(new MakerCommitmentMessage(commitment),
                        counterParty.getMakerNetworkId(),
                        null))
                .whenComplete((success, t) -> setState(State.COMMITMENT_SENT));
        return CompletableFuture.completedFuture(true);
    }
}
