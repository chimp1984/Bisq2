package network.misq.trade.lightningEscrow.escrowAgent;

import network.misq.contract.ManyPartyContract;
import network.misq.network.NetworkService;
import network.misq.network.message.Message;
import network.misq.network.node.Connection;
import network.misq.trade.lightningEscrow.LightningEscrow;
import network.misq.trade.lightningEscrow.LightningEscrowProtocol;

import java.util.concurrent.CompletableFuture;

public class EscrowAgentLightningEscrowProtocol extends LightningEscrowProtocol {
    public EscrowAgentLightningEscrowProtocol(ManyPartyContract contract, NetworkService p2pService) {
        super(contract, p2pService, null, new LightningEscrow());
    }

    @Override
    public CompletableFuture<Boolean> start() {
        return null;
    }

    @Override
    public void onMessage(Message message, Connection connection) {
    }
}
