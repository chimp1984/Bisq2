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

package network.misq.offer;

import io.reactivex.rxjava3.subjects.PublishSubject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import network.misq.account.FiatTransferType;
import network.misq.account.TransferType;
import network.misq.contract.AssetTransfer;
import network.misq.contract.SwapProtocolType;
import network.misq.network.Address;
import network.misq.network.INetworkService;
import network.misq.network.MockNetworkService;
import network.misq.network.NetworkId;
import network.misq.offer.options.*;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
public class OfferRepository {
    private final List<Listing> offers = new CopyOnWriteArrayList<>();
    protected final PublishSubject<Listing> offerAddedSubject;
    protected final PublishSubject<Listing> offerRemovedSubject;
    private INetworkService networkService;

    public OfferRepository(INetworkService networkService) {
        this.networkService = networkService;
        offerAddedSubject = PublishSubject.create();
        offerRemovedSubject = PublishSubject.create();

        offers.addAll(MockOfferBuilder.makeOffers().values());

        networkService.addListener(new MockNetworkService.Listener() {
            @Override
            public void onDataAdded(Serializable serializable) {
                if (serializable instanceof Listing) {
                    Listing offer = (Listing) serializable;
                    offers.add(offer);
                    offerAddedSubject.onNext(offer);
                }
            }

            @Override
            public void onDataRemoved(Serializable serializable) {
                if (serializable instanceof Listing) {
                    Listing offer = (Listing) serializable;
                    offers.remove(offer);
                    offerRemovedSubject.onNext(offer);
                }

            }
        });
    }

    public void initialize() {
    }

    public List<Listing> getOffers() {
        return offers;
    }

    public PublishSubject<Listing> getOfferAddedSubject() {
        return offerAddedSubject;
    }

    public PublishSubject<Listing> getOfferRemovedSubject() {
        return offerRemovedSubject;
    }

    public Offer createOffer(long askAmount) {
        NetworkId makerNetworkId = new NetworkId(Address.localHost(3333), null, "default");
        Asset askAsset = new Asset("BTC", askAmount, List.of());
        Asset bidAsset = new Asset("USD", 5000, List.of(FiatTransferType.ZELLE));
        return new Offer(List.of(SwapProtocolType.REPUTATION, SwapProtocolType.MULTISIG),
                makerNetworkId, bidAsset, askAsset);
    }

    public void publishOffer(Offer offer) {
        networkService.addData(offer);
    }

    public static class MockOfferBuilder {

        @Getter
        private final static Map<String, Listing> data = new HashMap<>();

        public static Map<String, Listing> makeOffers() {
            for (int i = 0; i < 10; i++) {
                Offer offer = getRandomOffer();
                data.put(offer.getId(), offer);
            }
    
           /* new Timer().scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    int toggle = new Random().nextInt(2);
                    if (toggle == 0) {
                        int iter = new Random().nextInt(3);
                        for (int i = 0; i < iter; i++) {
                            Serializable offer = getRandomOffer();
                            data.put(offer.getId(), offer);
                            listeners.forEach(l -> l.onOfferAdded(offer));
                        }
                    } else {
                        int iter2 = new Random().nextInt(2);
                        for (int i = 0; i < iter2; i++) {
                            if (!data.isEmpty()) {
                                Serializable offerToRemove = getOfferToRemove();
                                data.remove(offerToRemove.getId());
                                listeners.forEach(l -> l.onOfferRemoved(offerToRemove));
                            }
                        }
                    }
                }
            }, 0, 500);*/
            return data;
        }

        private static Offer getRandomOffer() {
            Asset askAsset;
            Asset bidAsset;
            Optional<Double> marketBasedPrice = Optional.empty();
            Optional<Double> minAmountAsPercentage = Optional.empty();
            String baseCurrency;
            //  int rand = new Random().nextInt(3);
            int rand = new Random().nextInt(2);
            //  rand = 0;
            if (rand == 0) {
                long usdAmount = new Random().nextInt(1000) + 500000000; // precision 4 / 50k usd
                long btcAmount = new Random().nextInt(100000000) + 100000000; // precision 8 / 1 btc
                //  usdAmount = 500000000; // precision 4 / 50k usd
                //   btcAmount = 100000000; // precision 8 / 1 btc
                askAsset = getRandomAsset("USD", usdAmount);
                bidAsset = getRandomAsset("BTC", btcAmount);
                baseCurrency = "BTC";
                marketBasedPrice = Optional.of(0.3d + new Random().nextInt(100) / 100d);
                minAmountAsPercentage = new Random().nextBoolean() ? Optional.empty() : Optional.of(0.1);
                // minAmountAsPercentage = Optional.empty();
            } else if (rand == 1) {
                long usdAmount = new Random().nextInt(1000) + 600000000; // precision 4 / 50k usd
                long btcAmount = new Random().nextInt(100000000) + 110000000; // precision 8 / 1 btc
                // usdAmount = 600000000; // precision 4 / 50k usd
                //  btcAmount = 120000000; // precision 8 / 1 btc
                askAsset = getRandomAsset("BTC", btcAmount);
                bidAsset = getRandomAsset("USD", usdAmount);
                baseCurrency = "BTC";
                marketBasedPrice = Optional.of(0.1d + new Random().nextInt(100) / 100d);
                minAmountAsPercentage = new Random().nextBoolean() ? Optional.empty() : Optional.of(0.3);
                // minAmountAsPercentage = Optional.empty();
            } else if (rand == 2) {
                long usdAmount = new Random().nextInt(100000) + 1200000; // precision 4 / 120 usd
                long eurAmount = new Random().nextInt(100000) + 1000000; // precision 4 / 100 eur
                askAsset = getRandomAsset("USD", usdAmount);
                bidAsset = getRandomAsset("EUR", eurAmount);
                baseCurrency = "USD";

            } else {
                // ignore for now as fiat/altcoins calculations not supported and only one market price
                long btcAmount = new Random().nextInt(10000000) + 100000000; // precision 8 / 1 btc //0.007144 BTC
                long xmrAmount = new Random().nextInt(10000000) + 13800000000L; // precision 8 / 138 xmr
                bidAsset = getRandomAsset("BTC", btcAmount);
                askAsset = getRandomAsset("XMR", xmrAmount);
                baseCurrency = "XMR";
                marketBasedPrice = Optional.of(-0.02);
                minAmountAsPercentage = Optional.of(0.8);
            }
            List<SwapProtocolType> protocolTypes = new ArrayList<>();
            rand = new Random().nextInt(3);
            for (int i = 0; i < rand; i++) {
                SwapProtocolType swapProtocolType = SwapProtocolType.values()[new Random().nextInt(SwapProtocolType.values().length)];
                protocolTypes.add(swapProtocolType);
            }
            NetworkId makerNetworkId = new NetworkId(Address.localHost(1000 + new Random().nextInt(1000)), null, "default");

            Optional<SupportOptions> disputeResolutionOptions = Optional.empty();
            Optional<FeeOptions> feeOptions = Optional.empty();
            ReputationProof accountCreationDateProof = new AccountCreationDateProof("hashOfAccount", "otsProof)");
            Optional<ReputationOptions> reputationOptions = Optional.of(new ReputationOptions(Set.of(accountCreationDateProof)));
            Optional<TransferOptions> transferOptions = new Random().nextBoolean() ?
                    Optional.of(new TransferOptions("USA", "HSBC")) :
                    new Random().nextBoolean() ? Optional.of(new TransferOptions("DE", "N26")) :
                            Optional.empty();
            return new Offer(bidAsset, askAsset, baseCurrency, protocolTypes, makerNetworkId,
                    marketBasedPrice, minAmountAsPercentage,
                    disputeResolutionOptions, feeOptions, reputationOptions, transferOptions);
        }

        private static Asset getRandomAsset(String code, long amount) {
            AssetTransfer.Type assetTransferType = new Random().nextBoolean() ? AssetTransfer.Type.AUTOMATIC : AssetTransfer.Type.MANUAL;
            List<TransferType> transferTypes = List.of(FiatTransferType.values()[new Random().nextInt(FiatTransferType.values().length)]);
            return new Asset(code, amount, transferTypes, assetTransferType);
        }

        private static Listing getOfferToRemove() {
            int index = new Random().nextInt(data.size());
            return new ArrayList<>(data.values()).get(index);
        }
    }
}
