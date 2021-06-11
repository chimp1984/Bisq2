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
import network.misq.application.options.ApplicationOptions;
import network.misq.id.IdentityRepository;
import network.misq.network.NetworkService;
import network.misq.offer.OfferRepository;
import network.misq.offer.OpenOfferRepository;
import network.misq.presentation.offer.OfferEntity;
import network.misq.presentation.offer.OfferEntityRepository;
import network.misq.security.KeyPairRepository;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Api for fully featured nodes, like a desktop app.
 */
@Getter
public class Api {
    private final ApplicationOptions applicationOptions;
    private final KeyPairRepository keyPairRepository;
    private final NetworkService networkService;
    private final OfferRepository offerRepository;
    private final OpenOfferRepository openOfferRepository;
    private final OfferEntityRepository offerEntityRepository;
    private final IdentityRepository identityRepository;

    public Api(Domain domain) {
        applicationOptions = domain.getApplicationOptions();
        keyPairRepository = domain.getKeyPairRepository();
        networkService = domain.getNetworkService();
        identityRepository = domain.getIdentityRepository();
        offerRepository = domain.getOfferRepository();
        openOfferRepository = domain.getOpenOfferRepository();
        offerEntityRepository = domain.getOfferEntityRepository();
    }

    /**
     * Activates the offerbookEntity. To be called before it is used by a client.
     */
    public void activateOfferbookEntity() {
        offerEntityRepository.activate();
    }

    /**
     * Deactivates the offerbookEntity. To be called before once not anymore used by a client.
     * Stops event processing, etc.
     */
    public void deactivateOfferbookEntity() {
        offerEntityRepository.deactivate();
    }

    /**
     * @return Provides the list of OfferEntity of the offerbookEntity.
     * <p>
     * An OfferEntity wraps the Offer domain object and augment it with presentation fields and dynamically
     * updated fields like market based prices and amounts.
     */
    public List<OfferEntity> getOfferEntities() {
        return offerEntityRepository.getOfferEntities();
    }

    /**
     * @return The PublishSubject for subscribing on OfferEntity added events.
     * The subscriber need to take care of dispose calls once inactive.
     */
    public PublishSubject<OfferEntity> getOfferEntityAddedSubject() {
        return offerEntityRepository.getOfferEntityAddedSubject();
    }

    /**
     * @return The PublishSubject for subscribing on OfferEntity removed events.
     * The subscriber need to take care of dispose calls once inactive.
     */
    public PublishSubject<OfferEntity> getOfferEntityRemovedSubject() {
        return offerEntityRepository.getOfferEntityRemovedSubject();
    }

    /**
     * @return The BehaviorSubject for subscribing on market price change events.
     */
    public BehaviorSubject<Double> getMarketPriceSubject() {
        return networkService.getMarketPriceSubject();
    }

    public double getMarketPrice() {
        return networkService.getMarketPrice();
    }

    public CompletableFuture<Integer> requestPriceUpdate() {
        return networkService.requestPriceUpdate();
    }

    public String getVersion() {
        return "0.1.0";
    }

    public String getHelp() {
        return "help";
    }

    public String getAppName() {
        return applicationOptions.appName();
    }
}
