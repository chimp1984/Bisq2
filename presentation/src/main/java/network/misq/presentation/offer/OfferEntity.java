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

package network.misq.presentation.offer;

import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import network.misq.common.monetary.Monetary;
import network.misq.common.monetary.Quote;
import network.misq.offer.MarketPrice;
import network.misq.offer.Offer;
import network.misq.offer.options.TransferOption;
import network.misq.presentation.formatters.AmountFormatter;
import network.misq.presentation.formatters.QuoteFormatter;

import java.util.Map;

/**
 * Enriched offer object which carries the dynamic data as well as formatted strings for presentation.
 */
@Getter
@Slf4j
public class OfferEntity implements Comparable<OfferEntity> {
    protected final Offer offer;
    private Quote quote;
    private Monetary quoteAmount;
    protected final String formattedBaseAmountWithMinAmount;
    protected final String formattedTransferOptions;
    protected String formattedQuote;
    protected String formattedQuoteAmount;
    protected String formattedQuoteAmountWithMinAmount;

    protected final BehaviorSubject<Map<String, MarketPrice>> marketPriceSubject;
    protected Disposable marketPriceDisposable;

    public OfferEntity(Offer offer, BehaviorSubject<Map<String, MarketPrice>> marketPriceSubject) {
        this.offer = offer;
        this.marketPriceSubject = marketPriceSubject;

        formattedBaseAmountWithMinAmount = AmountFormatter.formatAmountWithMinAmount(offer.getBaseAsset().monetary(),
                offer.getOptionalMinBaseAmount());

        formattedTransferOptions = offer.getOfferOptions().stream()
                .filter(offerOption -> offerOption instanceof TransferOption)
                .map(offerOption -> (TransferOption) offerOption)
                .map(OfferFormatter::formatTransferOptions)
                .findAny().orElse("");

        marketPriceDisposable = marketPriceSubject.subscribe(this::updatedPriceAndAmount, Throwable::printStackTrace);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////////////

    public void activate() {
        marketPriceDisposable = marketPriceSubject.subscribe(this::updatedPriceAndAmount);
    }

    public void deactivate() {
        marketPriceDisposable.dispose();
    }

    public int compareBaseAmount(OfferEntity other) {
        return Long.compare(offer.getBaseAsset().amount(), other.getOffer().getBaseAsset().amount());
    }

    public int compareQuoteAmount(OfferEntity other) {
        return quoteAmount.compareTo(other.quoteAmount);
    }

    public int compareQuote(OfferEntity other) {
        return quote.compareTo(other.quote);
    }

    @Override
    public int compareTo(OfferEntity other) {
        return compareQuote(other);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////
    // Internal
    ///////////////////////////////////////////////////////////////////////////////////////////////////

    protected void updatedPriceAndAmount(Map<String, MarketPrice> marketPriceMap) {
        MarketPrice marketPrice = marketPriceMap.get(offer.getQuoteAsset().currencyCode());
        if (marketPrice == null) {
            return;
        }

        Quote marketQuote = marketPrice.quote();
        double offset;
        String type;
        if (offer.getMarketPriceOffset().isPresent()) {
            offset = offer.getMarketPriceOffset().get();
            quote = Quote.fromMarketPriceOffset(marketQuote, offset);
            quoteAmount = Quote.toQuoteMonetary(offer.getBaseAsset().monetary(), quote);
            type = "Var";
        } else {
            quote = offer.getQuote();
            quoteAmount = offer.getQuoteAsset().monetary();
            offset = Quote.offsetOf(marketQuote, quote);
            type = "Fix";
        }
        formattedQuote = QuoteFormatter.formatWithQuoteCode(quote, offset) + " [" + type + "]";
        log.error("updatedPriceAndAmount {} {}", formattedQuote, offer.getQuoteAsset().currencyCode());

        formattedQuoteAmount = AmountFormatter.formatAmount(quoteAmount);

        formattedQuoteAmountWithMinAmount = AmountFormatter.formatAmountWithMinAmount(quoteAmount,
                offer.getOptionalMinQuoteAmount(quoteAmount.getValue()));
    }

    @Override
    public String toString() {
        return "OfferEntity{" +
                "\r\n     offer=" + offer +
                ",\r\n     quote=" + quote +
                ",\r\n     quoteAmount=" + quoteAmount +
                ",\r\n     formattedBaseAmountWithMinAmount='" + formattedBaseAmountWithMinAmount + '\'' +
                ",\r\n     formattedTransferOptions='" + formattedTransferOptions + '\'' +
                ",\r\n     formattedQuote='" + formattedQuote + '\'' +
                ",\r\n     formattedQuoteAmount='" + formattedQuoteAmount + '\'' +
                ",\r\n     formattedQuoteAmountWithMinAmount='" + formattedQuoteAmountWithMinAmount + '\'' +
                "\r\n}";
    }
}
