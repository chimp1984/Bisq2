package network.misq.web;


import network.misq.api.Api;
import network.misq.api.Domain;
import network.misq.application.Executable;
import network.misq.application.options.ApplicationOptions;
import network.misq.web.server.WebServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebServerMain extends Executable {
    private static final Logger log = LoggerFactory.getLogger(WebServerMain.class);

    public static void main(String[] args) {
        new WebServerMain(args);
    }

    protected Api api;
    private Domain domain;
    private WebServer webServer;

    public WebServerMain(String[] args) {
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
                webServer = new WebServer(api);
                webServer.start();
            }
        });
    }

    public void shutdown() {
        if (webServer != null) {
            try {
                log.info("Shutting down grpc server...");
                webServer.shutdown();
            } catch (Exception ex) {
                log.error("", ex);
            }
        }
    }
}