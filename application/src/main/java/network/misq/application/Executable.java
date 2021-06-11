package network.misq.application;

import network.misq.application.options.ApplicationOptions;
import network.misq.application.options.ApplicationOptionsParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public abstract class Executable {
    private static final Logger log = LoggerFactory.getLogger(Executable.class);

    public Executable(String[] args) {
        ApplicationOptions applicationOptions = ApplicationOptionsParser.parse(args);
        setupDomain(applicationOptions, args);
        createApi();
        launchApplication();
    }

    abstract protected void setupDomain(ApplicationOptions applicationOptions, String[] args);

    abstract protected void createApi();

    abstract protected void launchApplication();

    abstract protected void setupUserThread();

    abstract protected void initializeDomain();

    abstract protected void initializeApplication();
}
