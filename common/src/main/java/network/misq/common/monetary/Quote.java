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

package network.misq.common.monetary;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import network.misq.common.currency.MisqCurrency;
import network.misq.common.util.MathUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A price quote is using the concept of base currency and quote currency. Base currency is left and quote currency
 * right. Often separated by '/' or '-'. The intuitive interpretation of division due the usage of '/' is misleading.
 * The price or quote is the amount of quote currency one gets for 1 unit of the base currency. E.g. a BTC/USD price
 * of 50 000 BTC/USD means you get 50 000 USD for 1 BTC.
 * <p>
 * For the smallestUnitExponent of the quote we use the smallestUnitExponent of the quote currency.
 */
@EqualsAndHashCode
@Getter
public class Quote implements Comparable<Quote> {
    private final long value;
    private final Monetary baseMonetary;
    private final Monetary quoteMonetary;
    private final int smallestUnitExponent;

    private Quote(long value, Monetary baseMonetary, Monetary quoteMonetary) {
        this.value = value;
        this.baseMonetary = baseMonetary;
        this.quoteMonetary = quoteMonetary;
        this.smallestUnitExponent = quoteMonetary.smallestUnitExponent;
    }

    /**
     * @param price            Price of a BTC-Fiat quote (e.g. BTC/USD). Bitcoin is base currency
     * @param fiatCurrencyCode Currency code of the fiat (quote) side
     * @return A quote object using 1 BTC as base coin.
     */
    public static Quote fromFiatPrice(double price, String fiatCurrencyCode) {
        return Quote.of(Coin.asBtc(1.0), Fiat.of(price, fiatCurrencyCode));
    }

    /**
     * @param price       Price of a Altcoin-BTC quote (e.g. XMR/BTC). Altcoin is base currency
     * @param altCoinCode Currency code of the altcoin (base) side
     * @return A quote object using 1 unit of the altcoin as base coin.
     */
    public static Quote fromAltCoinPrice(double price, String altCoinCode) {
        return Quote.of(Coin.of(1.0, altCoinCode), Coin.asBtc(price));
    }

    /**
     * @param price             Price (e.g. EUR/USD). Anything can be base currency or quote currency.
     * @param baseCurrencyCode  Base currency code
     * @param quoteCurrencyCode Quote currency code
     * @return A quote object using 1 unit of the base asset.
     */
    public static Quote fromPrice(double price, String baseCurrencyCode, String quoteCurrencyCode) {
        Monetary baseMonetary = MisqCurrency.isFiat(baseCurrencyCode) ?
                Fiat.of(1d, baseCurrencyCode) :
                Coin.of(1d, baseCurrencyCode);
        Monetary quoteMonetary = MisqCurrency.isFiat(quoteCurrencyCode) ?
                Fiat.of(price, quoteCurrencyCode) :
                Coin.of(price, quoteCurrencyCode);

        return Quote.of(baseMonetary, quoteMonetary);
    }

    public static Quote of(Monetary baseMonetary, Monetary quoteMonetary) {
        checkArgument(baseMonetary.value != 0, "baseMonetary.value must not be 0");
        long value = BigDecimal.valueOf(quoteMonetary.value)
                .movePointRight(baseMonetary.smallestUnitExponent)
                .divide(BigDecimal.valueOf(baseMonetary.value), RoundingMode.HALF_UP)
                .longValue();
        return new Quote(value, baseMonetary, quoteMonetary);
    }

    /**
     * @param marketPrice Current market price
     * @param offset      Offset from market price in percent.
     * @return The quote representing the offset from market price
     */
    public static Quote fromMarketPriceOffset(Quote marketPrice, double offset) {
        double price = marketPrice.asDouble() * (1 + offset);
        return Quote.fromPrice(price, marketPrice.baseMonetary.currencyCode, marketPrice.quoteMonetary.currencyCode);
    }

    public static Monetary toQuoteMonetary(Monetary baseMonetary, Quote quote) {
        Monetary quoteMonetary = quote.quoteMonetary;
        long value = BigDecimal.valueOf(baseMonetary.value).multiply(BigDecimal.valueOf(quote.value))
                .movePointLeft(baseMonetary.smallestUnitExponent)
                .longValue();
        if (quoteMonetary instanceof Fiat) {
            return new Fiat(value,
                    quoteMonetary.currencyCode,
                    quoteMonetary.smallestUnitExponent);
        } else {
            return new Coin(value,
                    quoteMonetary.currencyCode,
                    quoteMonetary.smallestUnitExponent);
        }
    }

    public double asDouble() {
        return asDouble(smallestUnitExponent);
    }

    public double asDouble(int precision) {
        return MathUtils.roundDouble(BigDecimal.valueOf(value).movePointLeft(smallestUnitExponent).doubleValue(), precision);
    }

    public String getQuoteCode() {
        return baseMonetary.currencyCode + "/" + quoteMonetary.currencyCode;
    }

    @Override
    public int compareTo(Quote o) {
        return Long.compare(value, o.getValue());
    }

    @Override
    public String toString() {
        return "Quote{" +
                "\n     value=" + value +
                ",\n     baseMonetary=" + baseMonetary +
                ",\n     quoteMonetary=" + quoteMonetary +
                ",\n     smallestUnitExponent=" + smallestUnitExponent +
                "\n}";
    }
}