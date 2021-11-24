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

package network.misq.network.p2p.services.confidential;

import lombok.extern.slf4j.Slf4j;
import network.misq.common.ObjectSerializer;
import network.misq.network.p2p.NetworkId;
import network.misq.network.p2p.message.Message;
import network.misq.network.p2p.node.Address;
import network.misq.network.p2p.node.Connection;
import network.misq.network.p2p.node.MessageListener;
import network.misq.network.p2p.node.NodesById;
import network.misq.security.ConfidentialData;
import network.misq.security.HybridEncryption;
import network.misq.security.KeyPairRepository;
import network.misq.security.PubKey;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;

@Slf4j
public class ConfMsgService implements MessageListener {

    public static record Config(KeyPairRepository keyPairRepository) {
    }

    private final Set<MessageListener> messageListeners = new CopyOnWriteArraySet<>();
    private final NodesById nodesById;
    private final KeyPairRepository keyPairRepository;

    public ConfMsgService(NodesById nodesById, ConfMsgService.Config config) {
        this.nodesById = nodesById;
        this.keyPairRepository = config.keyPairRepository();

        nodesById.addMessageListener(this);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener
    ///////////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMessage(Message message, Connection connection, String nodeId) {
        if (message instanceof ConfidentialMessage confidentialMessage) {
            if (confidentialMessage instanceof RelayMessage) {
                //todo
                RelayMessage relayMessage = (RelayMessage) message;
                Address targetAddress = relayMessage.getTargetAddress();
                // send(message, targetAddress);
            } else {
                ConfidentialData confidentialData = confidentialMessage.getConfidentialData();
                keyPairRepository.findKeyPair(confidentialMessage.getKeyId()).ifPresent(receiversKeyPair -> {
                    try {
                        byte[] decrypted = HybridEncryption.decryptAndVerify(confidentialData, receiversKeyPair);
                        Message decryptedMessage = (Message) ObjectSerializer.deserialize(decrypted);
                        messageListeners.forEach(listener -> listener.onMessage(decryptedMessage, connection, nodeId));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
        }
    }

    public CompletableFuture<Connection> send(Message message,
                                              Address address,
                                              PubKey pubKey,
                                              KeyPair myKeyPair,
                                              String nodeId)
            throws GeneralSecurityException {
        return nodesById.send(nodeId, getConfidentialMessage(message, pubKey, myKeyPair), address);
    }

    public CompletableFuture<Connection> send(Message message,
                                              Connection connection,
                                              PubKey pubKey,
                                              KeyPair myKeyPair,
                                              String nodeId)
            throws GeneralSecurityException {
        return nodesById.send(nodeId, getConfidentialMessage(message, pubKey, myKeyPair), connection);
    }

    public CompletableFuture<Connection> relay(Message message, NetworkId networkId, KeyPair myKeyPair) {
       /*   Set<Connection> connections = getConnectionsWithSupportedNetwork(peerAddress.getNetworkType());
      Connection outboundConnection = CollectionUtil.getRandomElement(connections);
        if (outboundConnection != null) {
            //todo we need 2 diff. pub keys for encryption here
            // ConfidentialMessage inner = seal(message);
            // RelayMessage relayMessage = new RelayMessage(inner, peerAddress);
            // ConfidentialMessage confidentialMessage = seal(relayMessage);
            // return node.send(confidentialMessage, outboundConnection);
        }*/
        return CompletableFuture.failedFuture(new Exception("No connection supporting that network type found."));
    }

    public void shutdown() {
        nodesById.removeMessageListener(this);
        messageListeners.clear();
    }


    public void addMessageListener(MessageListener messageListener) {
        messageListeners.add(messageListener);
    }

    public void removeMessageListener(MessageListener messageListener) {
        messageListeners.remove(messageListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////////////

    private ConfidentialMessage getConfidentialMessage(Message message, PubKey pubKey, KeyPair myKeyPair)
            throws GeneralSecurityException {
        ConfidentialData confidentialData = HybridEncryption.encryptAndSign(message.serialize(), pubKey.publicKey(), myKeyPair);
        return new ConfidentialMessage(confidentialData, pubKey.id());
    }

/*
    private Set<Connection> getConnectionsWithSupportedNetwork(NetworkType networkType) {
        return peerGroup.getConnectedPeerByAddress().stream()
                .filter(peer -> peer.getCapability().supportedNetworkTypes().contains(networkType))
                .flatMap(peer -> node.findConnection(peer.getAddress()).stream())
                .collect(Collectors.toSet());
    }*/
}
