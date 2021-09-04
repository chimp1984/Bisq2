package network.misq.network.p2p.node.socket;

import lombok.extern.slf4j.Slf4j;
import network.misq.network.p2p.NetworkConfig;
import network.misq.network.p2p.node.connection.Address;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;


@Slf4j
public class ClearNetSocketFactory implements SocketFactory {

    public ClearNetSocketFactory(NetworkConfig networkConfig) {
    }

    public CompletableFuture<Boolean> initialize() {
        log.debug("Initialize");
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        new Thread(() -> {
            try {
                Thread.sleep(5); // simulate tor delay
            } catch (InterruptedException ignore) {
            }
            future.complete(true);
        }).start();
        return future;
    }

    @Override
    public CompletableFuture<GetServerSocketResult> getServerSocket(String serverId, int serverPort) {
        CompletableFuture<GetServerSocketResult> future = new CompletableFuture<>();
        log.debug("Create serverSocket");
        try {
            Thread.sleep(5); // simulate tor delay
        } catch (InterruptedException ignore) {
        }

        try {
            ServerSocket serverSocket = new ServerSocket(serverPort);
            Address address = Address.localHost(serverPort);
            log.debug("ServerSocket created");
            future.complete(new GetServerSocketResult(serverId, serverSocket, address));
        } catch (IOException e) {
            log.error(e.toString(), e);
            future.completeExceptionally(e);
        }
        return future;
    }

    @Override
    public Socket getSocket(Address address) throws IOException {
        log.debug("Create new Socket");
        return new Socket(address.getHost(), address.getPort());
    }

    @Override
    public void close() {
    }

    @Override
    public Optional<Address> getServerAddress(String serverId) {
        return Optional.empty();
    }
}
