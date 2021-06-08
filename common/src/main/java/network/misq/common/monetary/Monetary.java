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
import network.misq.common.locale.LocaleRepository;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;

@EqualsAndHashCode
@Getter
public abstract class Monetary implements Comparable<Monetary> {
    protected final long value;
    protected final String currencyCode;
    protected final int smallestUnitExponent;
    @Nullable
    private DecimalFormat decimalFormat;

    public static Monetary from(Monetary monetary, long newValue) {
        if (monetary instanceof Fiat) {
            return Fiat.of(newValue, monetary.getCurrencyCode(), monetary.getSmallestUnitExponent());
        } else {
            return Coin.of(newValue, monetary.getCurrencyCode(), monetary.getSmallestUnitExponent());
        }
    }

    public static Monetary from(long amount, String currencyCode) {
        if (MisqCurrency.isFiat(currencyCode)) {
            return Fiat.of(amount, currencyCode);
        } else {
            return Coin.of(amount, currencyCode);
        }
    }

    protected Monetary(long value, String currencyCode, int smallestUnitExponent) {
        this.value = value;
        this.smallestUnitExponent = smallestUnitExponent;
        this.currencyCode = currencyCode;
    }

    protected Monetary(double value, String currencyCode, int smallestUnitExponent) {
        double max = BigDecimal.valueOf(Long.MAX_VALUE).movePointLeft(smallestUnitExponent).doubleValue();
        if (value > max) {
            throw new ArithmeticException("Provided value would lead to an overflow");
        }
        this.value = BigDecimal.valueOf(value).movePointRight(smallestUnitExponent).longValue();
        this.smallestUnitExponent = smallestUnitExponent;
        this.currencyCode = currencyCode;
    }

    public String format() {
        return format(LocaleRepository.getDefaultLocale());
    }

    public String formatWithCode() {
        return formatWithCode(LocaleRepository.getDefaultLocale());
    }

    abstract public String format(Locale locale);

    abstract public String formatWithCode(Locale locale);

    public String format(Locale locale, int precision) {
        if (decimalFormat == null) {
            decimalFormat = (DecimalFormat) NumberFormat.getNumberInstance(locale);
            decimalFormat.applyPattern(getPattern(precision));
        }
        return decimalFormat.format(asDouble());
    }


    public String formatWithCode(Locale locale, int precision) {
        return format(locale, precision) + " " + currencyCode;
    }

    protected String getPattern(int precision) {
        return "0." + "0".repeat(Math.max(0, precision));
    }

    abstract public double asDouble();

    @Override
    public int compareTo(Monetary o) {
        return Long.compare(value, o.getValue());
    }


    @Override
    public String toString() {
        return "Monetary{" +
                "\n     value=" + value +
                ",\n     currencyCode='" + currencyCode + '\'' +
                ",\n     smallestUnitExponent=" + smallestUnitExponent +
                ",\n     decimalFormat=" + decimalFormat +
                "\n}";
    }
}