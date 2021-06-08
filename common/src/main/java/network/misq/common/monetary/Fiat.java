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
import lombok.Setter;
import network.misq.common.currency.MisqCurrency;
import network.misq.common.util.MathUtils;

import java.math.BigDecimal;
import java.util.Locale;

@EqualsAndHashCode(callSuper = true)
public class Fiat extends Monetary {
    @Setter
    private int precision = 2;

    public static Fiat parse(String string, String currencyCode) {
        return Fiat.of(new BigDecimal(string).movePointRight(4).longValue(),
                currencyCode,
                4);
    }

    public static Fiat parse(String string, String currencyCode, int smallestUnitExponent) {
        return Fiat.of(new BigDecimal(string).movePointRight(smallestUnitExponent).longValue(),
                currencyCode,
                smallestUnitExponent);
    }

    public static Fiat parse(String string) {
        String[] tokens = string.split(" ");
        if (tokens.length == 2) {
            String code = tokens[1];
            if (MisqCurrency.isFiat(code)) {
                return parse(tokens[0], code);
            }
        }
        throw new IllegalArgumentException("input could not be parsed. Expected: number value + space + currency code (e.g. 234.12 USD)");
    }

    public static Fiat of(long value, String currencyCode) {
        return new Fiat(value, currencyCode, 4);
    }

    public static Fiat of(double value, String currencyCode) {
        return new Fiat(value, currencyCode, 4);
    }

    public static Fiat of(long value, String currencyCode, int smallestUnitExponent) {
        return new Fiat(value, currencyCode, smallestUnitExponent);
    }

    private Fiat(long value, String currencyCode, int smallestUnitExponent) {
        super(value, currencyCode, smallestUnitExponent);
    }

    private Fiat(double value, String currencyCode, int smallestUnitExponent) {
        super(value, currencyCode, smallestUnitExponent);
    }

    @Override
    public double asDouble() {
        return MathUtils.roundDouble(BigDecimal.valueOf(value).movePointLeft(smallestUnitExponent).doubleValue(), precision);
    }

    @Override
    public String format(Locale locale) {
        return format(locale, precision);
    }

    @Override
    public String formatWithCode(Locale locale) {
        return formatWithCode(locale, precision);
    }

    @Override
    public String toString() {
        return "Fiat{" +
                "\n     precision=" + precision +
                "\n} " + super.toString();
    }
}