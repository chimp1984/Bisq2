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

package network.misq.desktop.common.threading;

import lombok.extern.slf4j.Slf4j;
import network.misq.common.timer.TaskScheduler;
import network.misq.desktop.common.threading.reactfx.FxTimer;

import javax.annotation.Nullable;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of TaskScheduler using the JavaFX Application thread based AnimationTimer (used in FxTimer).
 * Tasks are by default executed on the JavaFX Application thread, in contrast to network.misq.common.timer.Scheduler
 * which uses the ForkJoinPool for execution. If an executor is provided it is used to execute the task. Caution need
 * to be taken to not call Java FX framework methods in such cases, and it is probably better to use the 
 * network.misq.common.timer.Scheduler class instead.
 */
@Slf4j
public class UIScheduler implements TaskScheduler {
    private Runnable task;
    private FxTimer timer;
    @Nullable
    private Executor executor;

    private UIScheduler() {
    }

    public static UIScheduler run(Runnable task) {
        UIScheduler scheduler = new UIScheduler();
        scheduler.task = task;
        return scheduler;
    }

    @Override
    public UIScheduler withExecutor(Executor executor) {
        this.executor = executor;
        return this;
    }

    @Override
    public UIScheduler after(long delay) {
        return after(delay, TimeUnit.MILLISECONDS);
    }

    @Override
    public UIScheduler after(long delay, TimeUnit timeUnit) {
        return repeated(delay, timeUnit, 1);
    }

    @Override
    public UIScheduler periodically(long delay) {
        return periodically(delay, TimeUnit.MILLISECONDS);
    }

    @Override
    public UIScheduler periodically(long delay, TimeUnit timeUnit) {
        return repeated(delay, timeUnit, Long.MAX_VALUE);
    }

    @Override
    public UIScheduler repeated(long delay, long cycles) {
        return repeated(delay, TimeUnit.MILLISECONDS, cycles);
    }

    @Override
    public UIScheduler repeated(long delay, TimeUnit timeUnit, long cycles) {
        UIThread.run(() -> {
            if (timer != null) {
                timer.stop();
            }
            long period = timeUnit.toMillis(delay);
            timer = new FxTimer(period, period, task, (int) cycles, executor);
            timer.restart();
        });
        return this;
    }

    @Override
    public void stop() {
        UIThread.run(() -> {
            if (timer != null) {
                timer.stop();
                timer = null;
            }
        });
    }

    @Override
    public long getCounter() {
        if (timer != null) {
            return timer.getCounter();
        } else {
            return 0;
        }
    }
}
