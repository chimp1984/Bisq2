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

package network.misq.trade.bsqBond.taker;


import lombok.extern.slf4j.Slf4j;
import network.misq.contract.AssetTransfer;
import network.misq.contract.TwoPartyContract;
import network.misq.network.NetworkService;
import network.misq.network.message.Message;
import network.misq.network.node.Connection;
import network.misq.trade.bsqBond.BsqBond;
import network.misq.trade.bsqBond.BsqBondProtocol;
import network.misq.trade.bsqBond.maker.MakerCommitmentMessage;
import network.misq.trade.bsqBond.maker.MakerFundsSentMessage;

import java.util.concurrent.CompletableFuture;

@Slf4j
public class TakerBsqBondProtocol extends BsqBondProtocol {
    public TakerBsqBondProtocol(TwoPartyContract contract, NetworkService p2pService) {
        super(contract, p2pService, new AssetTransfer.Automatic(), new BsqBond());
    }

    @Override
    public void onMessage(Message message, Connection connection) {
        if (message instanceof MakerCommitmentMessage) {
            MakerCommitmentMessage bondCommitmentMessage = (MakerCommitmentMessage) message;
            security.verifyBondCommitmentMessage(bondCommitmentMessage)
                    .whenComplete((success, t) -> setState(State.COMMITMENT_RECEIVED))
                    .thenCompose(isValid -> security.getCommitment(contract))
                    .thenCompose(commitment -> p2pService.confidentialSend(new TakerCommitmentMessage(commitment),
                            counterParty.getMakerNetworkId(),
                            null))
                    .whenComplete((success, t) -> setState(State.COMMITMENT_SENT));
        }
        if (message instanceof MakerFundsSentMessage) {
            MakerFundsSentMessage fundsSentMessage = (MakerFundsSentMessage) message;
            security.verifyFundsSentMessage(fundsSentMessage)
                    .whenComplete((success, t) -> setState(State.FUNDS_RECEIVED))
                    .thenCompose(isValid -> transport.sendFunds(contract))
                    .thenCompose(isSent -> p2pService.confidentialSend(new TakerFundsSentMessage(),
                            counterParty.getMakerNetworkId(),
                            null))
                    .whenComplete((success, t) -> setState(State.FUNDS_SENT));
        }
    }

    public CompletableFuture<Boolean> start() {
        p2pService.addMessageListener(this);
        setState(State.START);
        return CompletableFuture.completedFuture(true);
    }
}
