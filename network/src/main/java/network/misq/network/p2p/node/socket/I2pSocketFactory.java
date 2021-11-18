package network.misq.network.p2p.node.socket;

import lombok.extern.slf4j.Slf4j;
import network.misq.common.util.NetworkUtils;
import network.misq.common.util.ThreadingUtils;
import network.misq.i2p.SamClient;
import network.misq.network.p2p.NetworkConfig;
import network.misq.network.p2p.node.Address;
import network.misq.network.p2p.node.Node;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import static java.io.File.separator;

// Start I2P
// Enable SAM at http://127.0.0.1:7657/configclients
// Takes about 1-2 minutes until its ready
@Slf4j
public class I2pSocketFactory implements SocketFactory {
    private final String i2pDirPath;
    private SamClient samClient;
    private final ExecutorService getServerSocketExecutor = ThreadingUtils.getSingleThreadExecutor("I2pNetworkProxy.ServerSocket");

    public I2pSocketFactory(NetworkConfig networkConfig) {
        i2pDirPath = networkConfig.getBaseDirPath() + separator + "i2p";
    }

    public CompletableFuture<Boolean> initialize() {
        log.debug("Initialize");
        try {
            samClient = SamClient.getSamClient(i2pDirPath);
            return CompletableFuture.completedFuture(true);
        } catch (Exception exception) {
            log.error(exception.toString(), exception);
            return CompletableFuture.failedFuture(exception);
        }
    }

    @Override
    public CompletableFuture<GetServerSocketResult> getServerSocket(String serverId, int serverPort) {
        CompletableFuture<GetServerSocketResult> future = new CompletableFuture<>();
        log.debug("Create serverSocket");
        getServerSocketExecutor.execute(() -> {
            try {
                ServerSocket serverSocket = samClient.getServerSocket(serverId, NetworkUtils.findFreeSystemPort());
                String destination = samClient.getMyDestination(serverId);
                Address address = new Address(destination, -1);
                log.debug("Create new Socket to {}", address);
                log.debug("ServerSocket created for address {}", address);
                future.complete(new GetServerSocketResult(serverId, serverSocket, address));
            } catch (Exception exception) {
                log.error(exception.toString(), exception);
                future.completeExceptionally(exception);
            }
        });
        return future;
    }

    @Override
    public Socket getSocket(Address address) throws IOException {
        try {
            log.debug("Create new Socket to {}", address);
            //todo pass session id
            Socket socket = samClient.connect(address.getHost(), Node.DEFAULT_SERVER_ID + "Alice");
            log.debug("Created new Socket");
            return socket;
        } catch (IOException exception) {
            log.error(exception.toString(), exception);
            throw exception;
        }
    }

    @Override
    public void close() {
        if (samClient != null) {
            samClient.shutdown();
        }
    }

    @Override
    public Optional<Address> getServerAddress(String serverId) {
        try {
            String myDestination = samClient.getMyDestination(serverId);
            return Optional.of(new Address(myDestination, -1));
        } catch (IOException exception) {
            log.error(exception.toString(), exception);
            return Optional.empty();
        }
    }
}