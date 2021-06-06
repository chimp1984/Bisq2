package network.misq.finance.swap.contract.lightningEscrow.maker;

import network.misq.finance.contract.AssetTransfer;
import network.misq.finance.contract.ManyPartyContract;
import network.misq.finance.swap.contract.lightningEscrow.LightningEscrow;
import network.misq.finance.swap.contract.lightningEscrow.LightningEscrowProtocol;
import network.misq.p2p.P2pService;
import network.misq.p2p.message.Message;
import network.misq.p2p.node.Connection;

import java.util.concurrent.CompletableFuture;

public class MakerLightningEscrowProtocol extends LightningEscrowProtocol {
    public MakerLightningEscrowProtocol(ManyPartyContract contract, P2pService p2pService) {
        super(contract, p2pService, new AssetTransfer.Manual(), new LightningEscrow());
    }

    @Override
    public CompletableFuture<Boolean> start() {
        return null;
    }

    @Override
    public void onMessage(Message message, Connection connection) {
    }
}
