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

import lombok.Getter;
import network.misq.contract.SwapProtocolType;
import network.misq.network.NetworkId;
import network.misq.offer.options.OfferOption;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Offer for a asset swap offer. Supports multiple protocolTypes in case the maker wants to give more flexibility
 * to takers.
 */
@Getter
public class Offer extends Listing {
    private final Asset bidAsset;
    private final Asset askAsset;
    private final String baseCurrency;
    private final Optional<Double> marketBasedPrice;
    private final Optional<Double> minAmountAsPercentage;

    private transient final long minBaseAmount;

    public Offer(List<SwapProtocolType> protocolTypes,
                 NetworkId makerNetworkId,
                 Asset bidAsset,
                 Asset askAsset) {
        this(bidAsset, askAsset, bidAsset.code(), protocolTypes, makerNetworkId,
                Optional.empty(), Optional.empty(), new HashSet<>());
    }

    public Offer(Asset bidAsset,
                 Asset askAsset,
                 String baseCurrency,
                 List<SwapProtocolType> protocolTypes,
                 NetworkId makerNetworkId,
                 Optional<Double> marketBasedPrice, //todo use option
                 Optional<Double> minAmountAsPercentage, //todo use option
                 Set<OfferOption> offerOptions) {
        super(protocolTypes, makerNetworkId, offerOptions);

        this.bidAsset = bidAsset;
        this.askAsset = askAsset;
        this.baseCurrency = baseCurrency;
        this.marketBasedPrice = marketBasedPrice;
        this.minAmountAsPercentage = minAmountAsPercentage;

       /* minAmountAsPercentage =   offerOptions.stream()
                .filter(e-> e instanceof AmountOption)
                .map(e->(AmountOption)e)
                .map(e-> e.getMinAmountAsPercentage())
                .findAny().orElse(1d);*/

        minBaseAmount = minAmountAsPercentage.map(perc -> Math.round(getBaseAsset().amount() * perc))
                .orElse(getBaseAsset().amount());
    }

    public double getFixPrice() {
        double baseAssetAmount = (double) getBaseAsset().amount();
        double quoteAssetAmount = (double) getQuoteAsset().amount();
        checkArgument(quoteAssetAmount > 0);
        return quoteAssetAmount / baseAssetAmount * 10000; // for fiat...
    }

    public Asset getBaseAsset() {
        if (askAsset.code().equals(baseCurrency)) {
            return askAsset;
        } else {
            return bidAsset;
        }
    }

    public Asset getQuoteAsset() {
        if (bidAsset.code().equals(baseCurrency)) {
            return askAsset;
        } else {
            return bidAsset;
        }
    }
}
