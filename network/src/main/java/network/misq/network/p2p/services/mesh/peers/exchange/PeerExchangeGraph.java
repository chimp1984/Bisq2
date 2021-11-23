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

package network.misq.network.p2p.services.mesh.peers.exchange;

import network.misq.common.data.Pair;
import network.misq.network.p2p.node.Address;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class PeerExchangeGraph {

    private final List<Pair<Address, Address>> vectors = new CopyOnWriteArrayList<>();

    public void add(Address source, Address target) {
        vectors.add(new Pair<>(source, target));
    }
}
