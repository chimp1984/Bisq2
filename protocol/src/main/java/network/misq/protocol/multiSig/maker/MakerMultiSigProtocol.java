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

package network.misq.protocol.multiSig.maker;

import lombok.extern.slf4j.Slf4j;
import network.misq.contract.AssetTransfer;
import network.misq.contract.TwoPartyContract;
import network.misq.network.p2p.P2pServiceNodesByNetworkType;
import network.misq.network.p2p.message.Message;
import network.misq.network.p2p.node.Connection;
import network.misq.protocol.SecurityProvider;
import network.misq.protocol.multiSig.MultiSig;
import network.misq.protocol.multiSig.MultiSigProtocol;
import network.misq.protocol.multiSig.taker.DepositTxBroadcastMessage;
import network.misq.protocol.multiSig.taker.PayoutTxBroadcastMessage;

import java.util.concurrent.CompletableFuture;

@Slf4j
public class MakerMultiSigProtocol extends MultiSigProtocol implements MultiSig.Listener {
    public MakerMultiSigProtocol(TwoPartyContract contract, P2pServiceNodesByNetworkType p2pService, SecurityProvider securityProvider) {
        super(contract, p2pService, new AssetTransfer.Manual(), securityProvider);
    }

    @Override
    public void onMessage(Message message, Connection connection, String nodeId) {
        if (message instanceof DepositTxBroadcastMessage) {
            DepositTxBroadcastMessage depositTxBroadcastMessage = (DepositTxBroadcastMessage) message;
            multiSig.verifyDepositTxBroadcastMessage(depositTxBroadcastMessage)
                    .whenComplete((depositTx, t) -> {
                        multiSig.setDepositTx(depositTx);
                        setState(State.DEPOSIT_TX_BROADCAST_MSG_RECEIVED);
                    });
        } else if (message instanceof PayoutTxBroadcastMessage) {
            PayoutTxBroadcastMessage payoutTxBroadcastMessage = (PayoutTxBroadcastMessage) message;
            multiSig.verifyPayoutTxBroadcastMessage(payoutTxBroadcastMessage)
                    .whenComplete((payoutTx, t) -> setState(State.PAYOUT_TX_BROADCAST_MSG_RECEIVED))
                    .thenCompose(multiSig::isPayoutTxInMemPool)
                    .whenComplete((isInMemPool, t) -> setState(State.PAYOUT_TX_VISIBLE_IN_MEM_POOL));
        }
    }

    @Override
    public void onDepositTxConfirmed() {
        setState(State.DEPOSIT_TX_CONFIRMED);
        assetTransfer.sendFunds(contract)
                .thenCompose(isSent -> onFundsSent());
    }

    public CompletableFuture<Boolean> start() {
        p2pService.addMessageListener(this);
        multiSig.addListener(this);
        setState(State.START);
        multiSig.getTxInputs()
                .thenCompose(txInputs -> p2pService.confidentialSend(new TxInputsMessage(txInputs),
                        counterParty.getMakerMultiAddress(),
                        null, null))
                .whenComplete((success, t) -> setState(State.TX_INPUTS_SENT));
        return CompletableFuture.completedFuture(true);
    }

    private CompletableFuture<Connection> onFundsSent() {
        setState(State.FUNDS_SENT);
        return multiSig.createPartialPayoutTx()
                .thenCompose(multiSig::getPayoutTxSignature)
                .thenCompose(sig -> p2pService.confidentialSend(new FundsSentMessage(sig),
                        counterParty.getMakerMultiAddress(),
                        null, null))
                .whenComplete((isValid, t) -> setState(State.FUNDS_SENT_MSG_SENT));
    }
}
