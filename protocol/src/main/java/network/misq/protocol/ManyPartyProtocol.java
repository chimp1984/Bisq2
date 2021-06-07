package network.misq.protocol;

import network.misq.contract.ManyPartyContract;
import network.misq.contract.Party;
import network.misq.contract.Role;
import network.misq.network.p2p.P2pService;
import network.misq.network.p2p.node.MessageListener;

import java.util.Map;

public abstract class ManyPartyProtocol extends Protocol implements MessageListener {
    protected final Map<Role, Party> partyMap;

    public ManyPartyProtocol(ManyPartyContract contract, P2pService p2pService) {
        super(contract, p2pService);
        partyMap = contract.getPartyMap();
    }
}
