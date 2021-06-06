package network.misq.trade.lightningEscrow;

import network.misq.contract.AssetTransfer;
import network.misq.contract.ManyPartyContract;
import network.misq.contract.SecurityProvider;
import network.misq.network.NetworkService;
import network.misq.trade.ManyPartyProtocol;
import network.misq.trade.Protocol;

public abstract class LightningEscrowProtocol extends ManyPartyProtocol {
    public enum State implements Protocol.State {
        START,
        START_MANUAL_PAYMENT,
        MANUAL_PAYMENT_STARTED;
    }

    private final AssetTransfer transport;
    private final LightningEscrow security;

    public LightningEscrowProtocol(ManyPartyContract contract, NetworkService p2pService, AssetTransfer assetTransfer, SecurityProvider securityProvider) {
        super(contract, p2pService);
        transport = assetTransfer;
        security = (LightningEscrow) securityProvider;

        if (assetTransfer instanceof AssetTransfer.Manual) {
            ((AssetTransfer.Manual) assetTransfer).addListener(this::onStartManualPayment);
            addListener(state -> {
                if (state == State.MANUAL_PAYMENT_STARTED) {
                    ((AssetTransfer.Manual) assetTransfer).onManualPaymentStarted();
                }
            });
        }
    }

    private void onStartManualPayment() {
    }

    public void onManualPaymentStarted() {
    }
}
