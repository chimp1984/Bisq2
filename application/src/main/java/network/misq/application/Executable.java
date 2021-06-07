package network.misq.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public abstract class Executable {
    private static final Logger log = LoggerFactory.getLogger(Executable.class);

    protected String appName = "Misq";

    public Executable() {
    }

    public void execute(String[] args) {
        // process options
        doExecute();
    }

    protected void doExecute() {
        setupApi();

        launchApplication();
    }

    protected abstract void setupApi();

    abstract protected void launchApplication();

    protected void applicationLaunched() {

    }
}
