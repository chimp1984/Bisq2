package network.misq.protocol.lightningEscrow.maker;

import network.misq.contract.AssetTransfer;
import network.misq.contract.ManyPartyContract;
import network.misq.network.p2p.ServiceNodesByTransport;
import network.misq.network.p2p.message.Message;
import network.misq.network.p2p.node.Connection;
import network.misq.protocol.lightningEscrow.LightningEscrow;
import network.misq.protocol.lightningEscrow.LightningEscrowProtocol;

import java.util.concurrent.CompletableFuture;

public class MakerLightningEscrowProtocol extends LightningEscrowProtocol {
    public MakerLightningEscrowProtocol(ManyPartyContract contract, ServiceNodesByTransport p2pService) {
        super(contract, p2pService, new AssetTransfer.Manual(), new LightningEscrow());
    }

    @Override
    public CompletableFuture<Boolean> start() {
        return null;
    }

    @Override
    public void onMessage(Message message, Connection connection, String nodeId) {
    }
}
