/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package network.misq.desktop;

import javafx.application.Application;
import lombok.extern.slf4j.Slf4j;
import network.misq.api.Api;
import network.misq.common.timer.UserThread;
import network.misq.desktop.common.Controller;
import network.misq.desktop.common.View;
import network.misq.desktop.main.MainViewController;
import network.misq.desktop.overlay.OverlayController;
import network.misq.desktop.utils.UncaughtExceptionHandler;

import java.util.concurrent.CompletableFuture;

@Slf4j
public class StageController implements Controller {
    private final Api api;
    private final StageModel model;
    private StageView stageView;
    private MainViewController mainViewController;
    private OverlayController overlayController;

    public StageController(Api api) {
        this.api = api;
        this.model = new StageModel();
    }

    public CompletableFuture<Boolean> launchApplication() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        StageView.LAUNCH_APP_FUTURE.whenComplete((stageView, throwable) -> {
            if (stageView != null) {
                this.stageView = stageView;
                initialize();
                future.complete(true);
            } else {
                throwable.printStackTrace();
            }
        });
        new Thread(() -> {
            Thread.currentThread().setName("Java FX Application Launcher");
            Application.launch(StageView.class); //blocks until app closed
        }).start();
        return future;
    }

    @Override
    public void initialize() {
        try {
            overlayController = new OverlayController();
            mainViewController = new MainViewController(api, overlayController);
            mainViewController.initialize();
            stageView.initialize(model, this, mainViewController.getView());
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Override
    public void onViewAdded() {
        try {
            overlayController.initialize(stageView.getScene());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onViewRemoved() {
    }

    @Override
    public View getView() {
        return null;
    }

    public void onQuit() {
        System.exit(0);
    }

    public static void setupUncaughtExceptionHandler(UncaughtExceptionHandler uncaughtExceptionHandler) {
        Thread.UncaughtExceptionHandler handler = (thread, throwable) -> {
            // Might come from another thread
            if (throwable.getCause() != null && throwable.getCause().getCause() != null) {
                log.error(throwable.getMessage());
            } else if (throwable instanceof ClassCastException &&
                    "sun.awt.image.BufImgSurfaceData cannot be cast to sun.java2d.xr.XRSurfaceData".equals(throwable.getMessage())) {
                log.warn(throwable.getMessage());
            } else if (throwable instanceof UnsupportedOperationException &&
                    "The system tray is not supported on the current platform.".equals(throwable.getMessage())) {
                log.warn(throwable.getMessage());
            } else {
                log.error("Uncaught Exception from thread " + Thread.currentThread().getName());
                log.error("throwableMessage= " + throwable.getMessage());
                log.error("throwableClass= " + throwable.getClass());
                // log.error("Stack trace:\n" + ExceptionUtils.getStackTrace(throwable));
                throwable.printStackTrace();
                UserThread.execute(() -> uncaughtExceptionHandler.handleUncaughtException(throwable, false));
            }
        };
        Thread.setDefaultUncaughtExceptionHandler(handler);
        Thread.currentThread().setUncaughtExceptionHandler(handler);
    }
}
