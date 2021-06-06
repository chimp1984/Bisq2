package network.misq.trade;

import network.misq.contract.ManyPartyContract;
import network.misq.contract.Party;
import network.misq.contract.Role;
import network.misq.network.NetworkService;
import network.misq.network.node.MessageListener;

import java.util.Map;

public abstract class ManyPartyProtocol extends Protocol implements MessageListener {
    protected final Map<Role, Party> partyMap;

    public ManyPartyProtocol(ManyPartyContract contract, NetworkService p2pService) {
        super(contract, p2pService);
        partyMap = contract.getPartyMap();
    }
}
