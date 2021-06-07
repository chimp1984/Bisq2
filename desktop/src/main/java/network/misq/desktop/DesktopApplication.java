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

import lombok.extern.slf4j.Slf4j;
import network.misq.api.Api;
import network.misq.api.Domain;
import network.misq.application.Executable;
import network.misq.application.options.ApplicationOptions;

@Slf4j
public class DesktopApplication extends Executable {
    private StageController stageController;
    protected Api api;
    private Domain domain;

    public DesktopApplication(String[] args) {
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
        stageController = new StageController(api);
        stageController.launchApplication().whenComplete((success, throwable) -> {
            log.info("Java FX Application initialized");
            initializeDomain();
        });
    }

    @Override
    protected void initializeDomain() {
        domain.initialize().whenComplete((success, throwable) -> {
            if (!success) {
                log.error("API Initialisation failed", throwable);
                //todo handle error
            }
        });
    }
}
