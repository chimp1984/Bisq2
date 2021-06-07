package network.misq.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public abstract class Executable {
    private static final Logger log = LoggerFactory.getLogger(Executable.class);

    protected String appName = "Misq";
    private Options options;

    public Executable() {
    }

    public void execute(String[] args) {
        options = Parser.parse(args);
        doExecute();
    }

    protected void doExecute() {
        setupApi(options);

        launchApplication();
    }

    protected abstract void setupApi(Options options);

    abstract protected void launchApplication();

    protected void applicationLaunched() {

    }
}
