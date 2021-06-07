package network.misq.application;

import network.misq.application.options.ApplicationOptions;
import network.misq.application.options.ApplicationOptionsParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public abstract class Executable {
    private static final Logger log = LoggerFactory.getLogger(Executable.class);

    protected String appName = "Misq";
    private final ApplicationOptions applicationOptions;

    public Executable(String[] args) {
        applicationOptions = ApplicationOptionsParser.parse(args);
        setupDomain(applicationOptions, args);
        createApi();
        launchApplication();
    }

    abstract protected void setupDomain(ApplicationOptions applicationOptions, String[] args);

    abstract protected void createApi();

    abstract protected void launchApplication();

    abstract protected void initializeDomain();
}
