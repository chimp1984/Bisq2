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

/**
 * At initial connection we exchange capabilities and require a valid AuthorizationToken (e.g. PoW).
 * The Client sends a Request and awaits for the servers Response.
 * The server awaits the Request and sends the Response.
 */
@Slf4j
public class ConnectionHandshake {
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

    // Client side protocol
    public static CompletableFuture<Capability> connect(Socket socket, Capability capability, AuthorizationService authorizationService) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Connect using {}", socket);
                ObjectOutputStream objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
                AuthorizationToken token = authorizationService.createToken(Request.class).get();
                Request message = new Request(token, capability);
                log.info("Client sends {}", message);
                objectOutputStream.writeObject(message);
                objectOutputStream.flush();

                ObjectInputStream objectInputStream = new ObjectInputStream(socket.getInputStream());
                Object msg = objectInputStream.readObject();
                log.info("Client received {}", msg);
                if (msg instanceof Response response) {
                    if (authorizationService.isAuthorized(response.token())) {
                        Capability serversCapability = response.capability();
                        log.info("Servers capability {}", serversCapability);
                        return serversCapability;
                    } else {
                        socket.close();
                        throw new HandShakeException("authorizationService.isAuthorized failed");
                    }
                } else {
                    socket.close();
                    throw new HandShakeException("Received message not type of AcceptMessage. " +
                            msg.getClass().getSimpleName());
                }
            } catch (Exception e) {
                try {
                    socket.close();
                } catch (IOException ignore) {
                }
                throw new HandShakeException(e);
            }
        }, ThreadingUtils.getSingleThreadExecutor("Client-ConnectionHandshake"));
    }

    // Server side protocol
    public static CompletableFuture<Capability> onSocket(Socket socket, Capability capability, AuthorizationService authorizationService) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ObjectInputStream objectInputStream = new ObjectInputStream(socket.getInputStream());
                Object msg = objectInputStream.readObject();
                log.info("Server received {}", msg);
                if (msg instanceof Request request) {
                    if (authorizationService.isAuthorized(request.token())) {
                        Capability clientCapability = request.capability();
                        log.info("Clients capability {}", clientCapability);
                        ObjectOutputStream objectOutputStream = new ObjectOutputStream(socket.getOutputStream());
                        AuthorizationToken token = authorizationService.createToken(Response.class).get();
                        objectOutputStream.writeObject(new Response(token, capability));
                        objectOutputStream.flush();

                        return clientCapability;
                    } else {
                        socket.close();
                        throw new HandShakeException("authorizationService.isAuthorized failed");
                    }
                } else {
                    socket.close();
                    throw new HandShakeException("Received message not type of ConnectMessage. " +
                            msg.getClass().getSimpleName());
                }
            } catch (Exception e) {
                try {
                    socket.close();
                } catch (IOException ignore) {
                }
                throw new HandShakeException(e);
            }
        }, ThreadingUtils.getSingleThreadExecutor("Server-ConnectionHandshake"));
    }
}