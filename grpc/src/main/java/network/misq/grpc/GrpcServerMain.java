package network.misq.grpc;


import network.misq.api.Api;
import network.misq.api.Domain;
import network.misq.application.Executable;
import network.misq.application.options.ApplicationOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class GrpcServerMain extends Executable {
    private static final Logger log = LoggerFactory.getLogger(GrpcServerMain.class);

    public static void main(String[] args) {
        new GrpcServerMain(args);
    }

    protected Api api;
    private Domain domain;
    private GrpcServer grpcServer;

    public GrpcServerMain(String[] args) {
        super(args);
    }

    @Override
    protected void setupDomain(ApplicationOptions applicationOptions, String[] args) {
        domain = new Domain(applicationOptions, args);
    }

    @Override
    protected void createApi() {
        api = new Api(domain);
    }

    @Override
    protected void launchApplication() {
        initializeDomain();
    }

    @Override
    protected void initializeDomain() {
        domain.initialize().whenComplete((success, throwable) -> {
            if (success) {
                grpcServer = new GrpcServer(api);
                grpcServer.start();
            }
        });
    }

    public void shutdown() {
        if (grpcServer != null) {
            try {
                log.info("Shutting down grpc server...");
                grpcServer.shutdown();
            } catch (Exception ex) {
                log.error("", ex);
            }
        }
    }
}