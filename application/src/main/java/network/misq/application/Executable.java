package network.misq.application;

import lombok.extern.slf4j.Slf4j;
import network.misq.application.options.ApplicationOptions;
import network.misq.application.options.ApplicationOptionsParser;
import network.misq.common.timer.UserThread;
import network.misq.common.util.ThreadingUtils;

@Slf4j
public abstract class Executable<T extends ApplicationFactory> {
    protected final T applicationFactory;

    public Executable(String[] args) {
        ApplicationOptions applicationOptions = ApplicationOptionsParser.parse(args);
        applicationFactory = createApplicationFactory(applicationOptions, args);
        createApi();
        launchApplication();
    }

    abstract protected T createApplicationFactory(ApplicationOptions applicationOptions, String[] args);

    abstract protected void createApi();

    protected void launchApplication() {
        applicationLaunched();
    }

    protected void applicationLaunched() {
        setupUserThread();
        initializeDomain();
    }

    protected void setupUserThread() {
        UserThread.setExecutor(ThreadingUtils.getSingleThreadExecutor(getClass().getSimpleName()));
    }

    protected void initializeDomain() {
        applicationFactory.initialize()
                .whenComplete((success, throwable) -> {
                    if (success) {
                        onInitializeDomainCompleted();
                    } else {
                        onInitializeDomainFailed(throwable);
                    }
                });
    }

    protected void onInitializeDomainFailed(Throwable throwable) {
        throwable.printStackTrace();
    }

    abstract protected void onInitializeDomainCompleted();

    public void shutdown() {
        applicationFactory.shutdown();
        System.exit(0);
    }
}
