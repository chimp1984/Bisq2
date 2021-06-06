package network.misq.finance.contract;

import network.misq.finance.Role;
import network.misq.p2p.P2pService;
import network.misq.p2p.node.MessageListener;

import java.util.Map;

public abstract class ManyPartyProtocol extends Protocol implements MessageListener {
    protected final Map<Role, Party> partyMap;

    public ManyPartyProtocol(ManyPartyContract contract, P2pService p2pService) {
        super(contract, p2pService);
        partyMap = contract.getPartyMap();
    }
}
