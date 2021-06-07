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

package network.misq.api;

import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.PublishSubject;
import lombok.Getter;
import network.misq.common.util.CollectionUtil;
import network.misq.id.IdentityRepository;
import network.misq.network.NetworkService;
import network.misq.network.p2p.MockNetworkService;
import network.misq.offer.OfferRepository;
import network.misq.offer.OpenOfferRepository;
import network.misq.presentation.offer.OfferEntity;
import network.misq.presentation.offer.OfferbookEntity;
import network.misq.security.KeyPairRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Api for fully featured nodes, like a desktop app.
 */
@Getter
public class StandardApi implements Api {
    private final KeyPairRepository keyPairRepository;
    private final NetworkService networkService;
    private final OfferRepository offerRepository;
    private final OpenOfferRepository openOfferRepository;
    private final OfferbookEntity offerbookEntity;
    private final IdentityRepository identityRepository;

    public StandardApi(KeyPairRepository.Options keyPairRepositoryOptions,
                       NetworkService.Option networkServiceOptions) {
        keyPairRepository = new KeyPairRepository(keyPairRepositoryOptions);
        networkService = new NetworkService(networkServiceOptions, keyPairRepository);
        identityRepository = new IdentityRepository(networkService);

        // add data use case is not available yet at networkService
        MockNetworkService mockNetworkService = new MockNetworkService();
        offerRepository = new OfferRepository(mockNetworkService);
        openOfferRepository = new OpenOfferRepository(mockNetworkService);

        offerbookEntity = new OfferbookEntity(offerRepository, networkService);
    }

    /**
     * Initializes all domain objects.
     * We do in parallel as far as possible. If there are dependencies we chain those as sequence.
     */
    public CompletableFuture<Boolean> initialize() {
        List<CompletableFuture<Boolean>> allFutures = new ArrayList<>();
        // Assuming identityRepository depends on keyPairRepository being initialized... 
        allFutures.add(keyPairRepository.initialize().thenCompose(success -> identityRepository.initialize()));
        allFutures.add(networkService.initialize());
        allFutures.add(offerRepository.initialize());
        allFutures.add(openOfferRepository.initialize());
        allFutures.add(offerbookEntity.initialize());
        // Once all have successfully completed our initialize is complete as well
        return CollectionUtil.allOf(allFutures)
                .thenApply(success -> success.stream().allMatch(e -> e))
                .orTimeout(10, TimeUnit.SECONDS)
                .thenCompose(CompletableFuture::completedFuture);
    }

    /**
     * Activates the offerbookEntity. To be called before it is used by a client.
     */
    public void activateOfferbookEntity() {
        offerbookEntity.activate();
    }

    /**
     * Deactivates the offerbookEntity. To be called before once not anymore used by a client.
     * Stops event processing, etc.
     */
    public void deactivateOfferbookEntity() {
        offerbookEntity.deactivate();
    }

    /**
     * @return Provides the list of OfferEntity of the offerbookEntity.
     * <p>
     * An OfferEntity wraps the Offer domain object and augment it with presentation fields and dynamically
     * updated fields like market based prices and amounts.
     */
    public List<OfferEntity> getOfferEntities() {
        return offerbookEntity.getOfferEntities();
    }

    /**
     * @return The PublishSubject for subscribing on OfferEntity added events.
     * The subscriber need to take care of dispose calls once inactive.
     */
    public PublishSubject<OfferEntity> getOfferEntityAddedSubject() {
        return offerbookEntity.getOfferEntityAddedSubject();
    }

    /**
     * @return The PublishSubject for subscribing on OfferEntity removed events.
     * The subscriber need to take care of dispose calls once inactive.
     */
    public PublishSubject<OfferEntity> getOfferEntityRemovedSubject() {
        return offerbookEntity.getOfferEntityRemovedSubject();
    }

    /**
     * @return The BehaviorSubject for subscribing on market price change events.
     */
    public BehaviorSubject<Double> getMarketPriceSubject() {
        return networkService.getMarketPriceSubject();
    }

    public CompletableFuture<Integer> requestPriceUpdate() {
        return networkService.requestPriceUpdate();
    }
}
