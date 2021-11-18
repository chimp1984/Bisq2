package network.misq.network.p2p.node.proxy;

import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;
import lombok.extern.slf4j.Slf4j;
import network.misq.common.util.FileUtils;
import network.misq.network.p2p.node.Address;
import network.misq.tor.Constants;
import network.misq.tor.Tor;
import network.misq.tor.TorServerSocket;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static java.io.File.separator;


@Slf4j
public class TorNetworkProxy implements NetworkProxy {
    public final static int DEFAULT_PORT = 9999;
    private static TorNetworkProxy INSTANCE;

    private final String torDirPath;
    private final Tor tor;

    public static TorNetworkProxy getInstance(NetworkProxyConfig config) {
        if (INSTANCE == null) {
            INSTANCE = new TorNetworkProxy(config);
        }
        return INSTANCE;
    }

    private TorNetworkProxy(NetworkProxyConfig config) {
        torDirPath = config.baseDirPath() + separator + "tor";

        // We get a singleton instance per application (torDirPath)
        tor = Tor.getTor(torDirPath);
    }

    @Override
    public CompletableFuture<Boolean> initialize() {
        if (tor.getState().get() == Tor.State.STARTED) {
            return CompletableFuture.completedFuture(true);
        } else if (tor.getState().get() == Tor.State.STARTING || tor.getState().get() == Tor.State.SHUTDOWN_STARTED) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignore) {
            }
            initialize();
        }
        
        log.info("Initialize Tor");
        long ts = System.currentTimeMillis();
        return tor.startAsync()
                .thenApply(result -> {
                    log.info("Tor initialized after {} ms", System.currentTimeMillis() - ts);
                    return true;
                });
    }

    @Override
    public CompletableFuture<ServerSocketResult> getServerSocket(int port, String nodeId) {
        log.info("Start hidden service");
        long ts = System.currentTimeMillis();
        try {
            TorServerSocket torServerSocket = tor.getTorServerSocket();
            return torServerSocket.bindAsync(port, nodeId)
                    .thenApply(onionAddress -> {
                        log.info("Tor hidden service Ready. Took {} ms. Onion address={}", System.currentTimeMillis() - ts, onionAddress);
                        return new ServerSocketResult(nodeId, torServerSocket, new Address(onionAddress.getHost(), onionAddress.getPort()));
                    });
        } catch (IOException e) {
            log.error(e.toString(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public Socket getSocket(Address address) throws IOException {
        long ts = System.currentTimeMillis();
        Socket socket = tor.getSocket(null);
        socket.connect(new InetSocketAddress(address.getHost(), address.getPort()));
        log.info("Tor socket to {} created. Took {} ms", address, System.currentTimeMillis() - ts);
        return socket;
    }

    public Optional<Socks5Proxy> getSocksProxy() throws IOException {
        return Optional.of(tor.getSocks5Proxy(null));
    }

    @Override
    public void close() {
        if (tor != null) {
            tor.shutdown();
        }
    }

    //todo move to torify lib
    @Override
    public Optional<Address> getServerAddress(String serverId) {
        String fileName = torDirPath + separator + Constants.HS_DIR + separator + serverId + separator + "hostname";
        if (new File(fileName).exists()) {
            try {
                String host = FileUtils.readAsString(fileName);
                return Optional.of(new Address(host, TorNetworkProxy.DEFAULT_PORT));
            } catch (IOException e) {
                log.error(e.toString(), e);
            }
        }

        return Optional.empty();
    }
}
