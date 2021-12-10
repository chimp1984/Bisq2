package network.misq.protocol.lightningEscrow.escrowAgent;

import network.misq.contract.ManyPartyContract;
import network.misq.network.p2p.ServiceNodesByTransport;
import network.misq.network.p2p.message.Message;
import network.misq.network.p2p.node.Connection;
import network.misq.protocol.lightningEscrow.LightningEscrow;
import network.misq.protocol.lightningEscrow.LightningEscrowProtocol;

import java.util.concurrent.CompletableFuture;

public class EscrowAgentLightningEscrowProtocol extends LightningEscrowProtocol {
    public EscrowAgentLightningEscrowProtocol(ManyPartyContract contract, ServiceNodesByTransport p2pService) {
        super(contract, p2pService, null, new LightningEscrow());
    }

    @Override
    public CompletableFuture<Boolean> start() {
        return null;
    }

    @Override
    public void onMessage(Message message, Connection connection, String nodeId) {
    }
}
