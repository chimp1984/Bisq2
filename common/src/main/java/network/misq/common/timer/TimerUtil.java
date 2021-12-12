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

package network.misq.common.timer;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

/**
 * Util for timer based execution. We use the same API as in UserThread for convenience.
 * This class is used when we do not want to execute tasks on the UserThread to avoid that the single threaded
 * UserThread timer gets overloaded with tasks.
 * Though the Scheduler creates a Timer for each call, so should not be used in case of
 */
public class TimerUtil {
    public static Timer runAfter(Runnable runnable, long delayInSec) {
        return runAfter(runnable, delayInSec, TimeUnit.SECONDS);
    }

    public static Timer runAfter(Runnable runnable, long delay, TimeUnit timeUnit) {
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                runnable.run();
            }

        }, timeUnit.toMillis(delay));
        return timer;
    }

    public static Timer runPeriodically(Runnable runnable, long delayInSec) {
        return runPeriodically(runnable, delayInSec, TimeUnit.SECONDS);
    }

    public static Timer runPeriodically(Runnable runnable, long delay, TimeUnit timeUnit) {
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                runnable.run();
            }

        }, timeUnit.toMillis(delay), timeUnit.toMillis(delay));
        return timer;
    }
}