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

package network.misq.network.http.common;

import network.misq.common.data.Couple;

import java.io.IOException;
import java.util.Optional;

public interface HttpClient {
    String get(String param, Optional<Couple<String, String>> optionalHeader) throws IOException;

    String post(String param, Optional<Couple<String, String>> optionalHeader) throws IOException;

    void shutDown();
 /*   String getUid();

    String getBaseUrl();

    boolean hasPendingRequest();
    void setBaseUrl(String baseUrl);

    void setIgnoreSocks5Proxy(boolean ignoreSocks5Proxy);*/


}
