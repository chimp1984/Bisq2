package network.misq.trade.lightningEscrow.taker;

import network.misq.contract.AssetTransfer;
import network.misq.contract.ManyPartyContract;
import network.misq.network.NetworkService;
import network.misq.network.message.Message;
import network.misq.network.node.Connection;
import network.misq.trade.lightningEscrow.LightningEscrow;
import network.misq.trade.lightningEscrow.LightningEscrowProtocol;

import java.util.concurrent.CompletableFuture;

public class TakerLightningEscrowProtocol extends LightningEscrowProtocol {
    public TakerLightningEscrowProtocol(ManyPartyContract contract, NetworkService p2pService) {
        super(contract, p2pService, new AssetTransfer.Automatic(), new LightningEscrow());
    }

    @Override
    public CompletableFuture<Boolean> start() {
        return null;
    }

    @Override
    public void onMessage(Message message, Connection connection) {
    }
}
