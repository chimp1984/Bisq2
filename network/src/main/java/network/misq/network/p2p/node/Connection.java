/*
 * This file is part of Misq.
 *
 * Misq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Misq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Misq. If not, see <http://www.gnu.org/licenses/>.
 */

package network.misq.network.p2p.node;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import network.misq.common.util.ThreadingUtils;
import network.misq.network.p2p.message.Envelope;
import network.misq.network.p2p.message.Message;
import network.misq.network.p2p.message.Version;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Represents an inbound or outbound connection to a peer node.
 * Listens for messages from the peer.
 * Sends messages to the peer.
 * Notifies messageListeners on new received messages.
 * Notifies errorHandler on exceptions from the inputHandlerService executor.
 */
@Slf4j
public abstract class Connection {

    protected final String id = UUID.randomUUID().toString();
    private final Socket socket;
    @Getter
    private final Capability peersCapability;

    private final BiConsumer<Message, Connection> messageHandler;
    @Getter
    private final Metrics metrics;
    private ExecutorService writeExecutor;
    private ExecutorService readExecutor;
    private ObjectInputStream objectInputStream;
    private ObjectOutputStream objectOutputStream;

    private volatile boolean isStopped;

    protected Connection(Socket socket, Capability peersCapability, BiConsumer<Message, Connection> messageHandler) {
        this.socket = socket;
        this.peersCapability = peersCapability;
        this.messageHandler = messageHandler;
        metrics = new Metrics();
    }

    void startListen(Consumer<Exception> errorHandler) throws IOException {
        writeExecutor = ThreadingUtils.getSingleThreadExecutor("Connection.outputExecutor-" + getShortId());
        readExecutor = ThreadingUtils.getSingleThreadExecutor("Connection.inputHandler-" + getShortId());

        // TODO java serialisation is just for dev, will be replaced by custom serialisation
        // Type-Length-Value Format is considered to be used:
        // https://github.com/lightningnetwork/lightning-rfc/blob/master/01-messaging.md#type-length-value-format
        // ObjectOutputStream need to be set before objectInputStream otherwise we get blocked...
        // https://stackoverflow.com/questions/14110986/new-objectinputstream-blocks/14111047
        objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
        objectInputStream = new ObjectInputStream(socket.getInputStream());

        readExecutor.execute(() -> {
            try {
                while (isNotStopped()) {
                    Object msg = objectInputStream.readObject();
                    String simpleName = msg.getClass().getSimpleName();
                    if (!(msg instanceof Envelope envelope)) {
                        throw new ConnectionException("Received message not type of Envelope. " + simpleName);
                    }
                    if (envelope.getVersion() != Version.VERSION) {
                        throw new ConnectionException("Invalid network version. " + simpleName);
                    }

                    log.debug("Received message: {} at connection: {}", envelope, this);
                    if (isNotStopped()) {
                        messageHandler.accept(envelope.getPayload(), this);
                    }
                }
            } catch (Exception exception) {
                //todo StreamCorruptedException from i2p at shutdown. prob it send some text data at shut down
                if (!isStopped) {
                    shutdown();
                }
                errorHandler.accept(exception);
            }
        });
    }


    CompletableFuture<Connection> send(Message message) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Envelope envelope = new Envelope(message);
                objectOutputStream.writeObject(envelope);
                objectOutputStream.flush();
                log.debug("Message sent: {} at connection: {}", envelope, this);
                return this;
            } catch (IOException exception) {
                if (!isStopped) {
                    shutdown();
                }
                throw new CompletionException(exception);
            }
        }, writeExecutor);
    }

    CompletableFuture<Void> shutdown() {
        log.info("shutdown {}", this);
        if (isStopped) {
            return CompletableFuture.completedFuture(null);
        }
        isStopped = true;
        return CompletableFuture.runAsync(() -> {
            ThreadingUtils.shutdownAndAwaitTermination(readExecutor, 1, TimeUnit.SECONDS);
            ThreadingUtils.shutdownAndAwaitTermination(writeExecutor, 1, TimeUnit.SECONDS);
            try {
                socket.close();
            } catch (IOException ignore) {
            }
        });
    }

    public String getId() {
        return id;
    }

    private String getShortId() {
        return id.substring(0, 24);
    }

    public Address getPeerAddress() {
        return peersCapability.address();
    }

    // Only at outbound connections we can be sure that the peer address is correct.
    // The announced peer address in capability is not guaranteed to be valid.
    // For most cases that is sufficient as the peer would not gain anything if lying about their address
    // as it would make them unreachable for receiving messages from newly established connections. But there are
    // cases where we need to be sure that it is the real address, like if we might use the peer address for banning a
    // not correctly behaving peer.
    public boolean getPeerAddressVerified() {
        return isOutboundConnection();
    }

    public boolean isOutboundConnection() {
        return this instanceof OutboundConnection;
    }

    @Override
    public String toString() {
        return id;
    }

    private boolean isNotStopped() {
        return !isStopped && !Thread.currentThread().isInterrupted();
    }
}
