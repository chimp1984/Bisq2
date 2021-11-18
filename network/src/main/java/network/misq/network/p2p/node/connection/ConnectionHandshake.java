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

package network.misq.network.p2p.node.connection;

import lombok.extern.slf4j.Slf4j;
import network.misq.common.util.ThreadingUtils;
import network.misq.network.p2p.message.Envelope;
import network.misq.network.p2p.message.Message;
import network.misq.network.p2p.node.Capability;
import network.misq.network.p2p.node.connection.authorization.AuthorizationService;
import network.misq.network.p2p.node.connection.authorization.AuthorizationToken;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * At initial connection we exchange capabilities and require a valid AuthorizationToken (e.g. PoW).
 * The Client sends a Request and awaits for the servers Response.
 * The server awaits the Request and sends the Response.
 */
@Slf4j
public class ConnectionHandshake {
    private final Socket socket;
    private final Capability capability;
    private final AuthorizationService authorizationService;
    public static record Request(AuthorizationToken token, Capability capability) implements Message {
    }

    public static record Response(AuthorizationToken token, Capability capability) implements Message {
    }

    public static class HandShakeException extends CompletionException {
        public HandShakeException(String message) {
            super(message);
        }

        public HandShakeException(Exception exception) {
            super(exception);
        }
    }

    public ConnectionHandshake(Socket socket, Capability capability, AuthorizationService authorizationService) {
        this.socket = socket;
        this.capability = capability;
        this.authorizationService = authorizationService;
    }

    // Client side protocol
    public CompletableFuture<Capability> start() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Start using {}", socket);
                ObjectOutputStream objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
                AuthorizationToken token = authorizationService.createToken(Request.class).get();
                Envelope message = new Envelope(new Request(token, capability));
                log.info("Client sends {}", message);
                objectOutputStream.writeObject(message);
                objectOutputStream.flush();

                ObjectInputStream objectInputStream = new ObjectInputStream(socket.getInputStream());
                Object msg = objectInputStream.readObject();
                log.info("Client received {}", msg);
                if (!(msg instanceof Envelope envelope)) {
                    throw new HandShakeException("Received message not type of Envelope. " +
                            msg.getClass().getSimpleName());
                }
                if (!(envelope.payload() instanceof Response response)) {
                    throw new HandShakeException("Received envelope.payload not type of Response. " +
                            msg.getClass().getSimpleName());
                }
                if (!authorizationService.isAuthorized(response.token())) {
                    throw new HandShakeException("authorizationService.isAuthorized failed");
                }

                Capability serversCapability = response.capability();
                log.info("Servers capability {}", serversCapability);
                return serversCapability;
            } catch (Exception e) {
                try {
                    socket.close();
                } catch (IOException ignore) {
                }
                if (e instanceof HandShakeException handShakeException) {
                    throw handShakeException;
                } else {
                    throw new HandShakeException(e);
                }
            }
        }, ThreadingUtils.getSingleThreadExecutor("ConnectionHandshake-client"));
    }

    // Server side protocol
    public CompletableFuture<Capability> onSocket() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ObjectInputStream objectInputStream = new ObjectInputStream(socket.getInputStream());
                Object msg = objectInputStream.readObject();
                log.info("Server received {}", msg);
                if (!(msg instanceof Envelope envelope)) {
                    throw new HandShakeException("Received message not type of Envelope. " +
                            msg.getClass().getSimpleName());
                }
                if (!(envelope.payload() instanceof Request request)) {
                    throw new HandShakeException("Received envelope.payload not type of Request. " +
                            msg.getClass().getSimpleName());
                }
                if (!authorizationService.isAuthorized(request.token())) {
                    throw new HandShakeException("authorizationService.isAuthorized failed");
                }

                Capability clientCapability = request.capability();
                log.info("Clients capability {}", clientCapability);
                ObjectOutputStream objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
                AuthorizationToken token = authorizationService.createToken(Response.class).get();
                objectOutputStream.writeObject(new Envelope(new Response(token, capability)));
                objectOutputStream.flush();
                return clientCapability;
            } catch (Exception e) {
                try {
                    socket.close();
                } catch (IOException ignore) {
                }
                if (e instanceof HandShakeException handShakeException) {
                    throw handShakeException;
                } else {
                    throw new HandShakeException(e);
                }
            }
        }, ThreadingUtils.getSingleThreadExecutor("ConnectionHandshake-server"));
    }
}