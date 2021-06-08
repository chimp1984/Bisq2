/*
 * Copyright 2014 Andreas Schildbach
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package network.misq.common.monetary.bitcoinj;

import com.google.common.math.LongMath;

import java.io.Serializable;
import java.math.BigDecimal;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Represents a monetary Bitcoin value. This class is immutable.
 */
public final class BitcoinJCoin implements BitcoinJMonetary, Comparable<BitcoinJCoin>, Serializable {

    /**
     * Number of decimals for one Bitcoin. This constant is useful for quick adapting to other coins because a lot of
     * constants derive from it.
     */
    public static final int SMALLEST_UNIT_EXPONENT = 8;

    /**
     * The number of satoshis equal to one bitcoin.
     */
    private static final long COIN_VALUE = LongMath.pow(10, SMALLEST_UNIT_EXPONENT);

    /**
     * Zero Bitcoins.
     */
    public static final BitcoinJCoin ZERO = BitcoinJCoin.valueOf(0);

    /**
     * One Bitcoin.
     */
    public static final BitcoinJCoin COIN = BitcoinJCoin.valueOf(COIN_VALUE);

    /**
     * 0.01 Bitcoins. This unit is not really used much.
     */
    public static final BitcoinJCoin CENT = COIN.divide(100);

    /**
     * 0.001 Bitcoins, also known as 1 mBTC.
     */
    public static final BitcoinJCoin MILLICOIN = COIN.divide(1000);

    /**
     * 0.000001 Bitcoins, also known as 1 µBTC or 1 uBTC.
     */
    public static final BitcoinJCoin MICROCOIN = MILLICOIN.divide(1000);

    /**
     * A satoshi is the smallest unit that can be transferred. 100 million of them fit into a Bitcoin.
     */
    public static final BitcoinJCoin SATOSHI = BitcoinJCoin.valueOf(1);

    public static final BitcoinJCoin FIFTY_COINS = COIN.multiply(50);

    /**
     * Represents a monetary value of minus one satoshi.
     */
    public static final BitcoinJCoin NEGATIVE_SATOSHI = BitcoinJCoin.valueOf(-1);

    /**
     * The number of satoshis of this monetary value.
     */
    public final long value;

    private BitcoinJCoin(final long satoshis) {
        this.value = satoshis;
    }

    /**
     * Create a {@code Coin} from a long integer number of satoshis.
     *
     * @param satoshis number of satoshis
     * @return {@code Coin} object containing value in satoshis
     */
    public static BitcoinJCoin valueOf(final long satoshis) {
        return new BitcoinJCoin(satoshis);
    }

    @Override
    public int smallestUnitExponent() {
        return SMALLEST_UNIT_EXPONENT;
    }

    /**
     * Returns the number of satoshis of this monetary value.
     */
    @Override
    public long getValue() {
        return value;
    }

    /**
     * Create a {@code Coin} from an amount expressed in "the way humans are used to".
     *
     * @param coins Number of bitcoins
     * @param cents Number of bitcents (0.01 bitcoin)
     * @return {@code Coin} object containing value in satoshis
     */
    public static BitcoinJCoin valueOf(final int coins, final int cents) {
        checkArgument(cents < 100);
        checkArgument(cents >= 0);
        checkArgument(coins >= 0);
        return COIN.multiply(coins).add(CENT.multiply(cents));
    }

    /**
     * Convert a decimal amount of BTC into satoshis.
     *
     * @param coins number of coins
     * @return number of satoshis
     */
    public static long btcToSatoshi(BigDecimal coins) {
        return coins.movePointRight(SMALLEST_UNIT_EXPONENT).longValueExact();
    }

    /**
     * Convert an amount in satoshis to an amount in BTC.
     *
     * @param satoshis number of satoshis
     * @return number of bitcoins (in BTC)
     */
    public static BigDecimal satoshiToBtc(long satoshis) {
        return new BigDecimal(satoshis).movePointLeft(SMALLEST_UNIT_EXPONENT);
    }

    /**
     * Create a {@code Coin} from a decimal amount of BTC.
     *
     * @param coins number of coins (in BTC)
     * @return {@code Coin} object containing value in satoshis
     */
    public static BitcoinJCoin ofBtc(BigDecimal coins) {
        return BitcoinJCoin.valueOf(btcToSatoshi(coins));
    }

    /**
     * Create a {@code Coin} from a long integer number of satoshis.
     *
     * @param satoshis number of satoshis
     * @return {@code Coin} object containing value in satoshis
     */
    public static BitcoinJCoin ofSat(long satoshis) {
        return BitcoinJCoin.valueOf(satoshis);
    }

    /**
     * Create a {@code Coin} by parsing a {@code String} amount expressed in "the way humans are used to".
     *
     * @param str string in a format understood by {@link BigDecimal#BigDecimal(String)}, for example "0", "1", "0.10",
     *            * "1.23E3", "1234.5E-5".
     * @return {@code Coin} object containing value in satoshis
     * @throws IllegalArgumentException if you try to specify fractional satoshis, or a value out of range.
     */
    public static BitcoinJCoin parseCoin(final String str) {
        try {
            long satoshis = btcToSatoshi(new BigDecimal(str));
            return BitcoinJCoin.valueOf(satoshis);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(e); // Repackage exception to honor method contract
        }
    }

    /**
     * Create a {@code Coin} by parsing a {@code String} amount expressed in "the way humans are used to".
     * The amount is cut to satoshi precision.
     *
     * @param str string in a format understood by {@link BigDecimal#BigDecimal(String)}, for example "0", "1", "0.10",
     *            * "1.23E3", "1234.5E-5".
     * @return {@code Coin} object containing value in satoshis
     * @throws IllegalArgumentException if you try to specify a value out of range.
     */
    public static BitcoinJCoin parseCoinInexact(final String str) {
        try {
            long satoshis = new BigDecimal(str).movePointRight(SMALLEST_UNIT_EXPONENT).longValue();
            return BitcoinJCoin.valueOf(satoshis);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(e); // Repackage exception to honor method contract
        }
    }

    public BitcoinJCoin add(final BitcoinJCoin value) {
        return new BitcoinJCoin(LongMath.checkedAdd(this.value, value.value));
    }

    /**
     * Alias for add
     */
    public BitcoinJCoin plus(final BitcoinJCoin value) {
        return add(value);
    }

    public BitcoinJCoin subtract(final BitcoinJCoin value) {
        return new BitcoinJCoin(LongMath.checkedSubtract(this.value, value.value));
    }

    /**
     * Alias for subtract
     */
    public BitcoinJCoin minus(final BitcoinJCoin value) {
        return subtract(value);
    }

    public BitcoinJCoin multiply(final long factor) {
        return new BitcoinJCoin(LongMath.checkedMultiply(this.value, factor));
    }

    /**
     * Alias for multiply
     */
    public BitcoinJCoin times(final long factor) {
        return multiply(factor);
    }

    /**
     * Alias for multiply
     */
    public BitcoinJCoin times(final int factor) {
        return multiply(factor);
    }

    public BitcoinJCoin divide(final long divisor) {
        return new BitcoinJCoin(this.value / divisor);
    }

    /**
     * Alias for divide
     */
    public BitcoinJCoin div(final long divisor) {
        return divide(divisor);
    }

    /**
     * Alias for divide
     */
    public BitcoinJCoin div(final int divisor) {
        return divide(divisor);
    }

    public BitcoinJCoin[] divideAndRemainder(final long divisor) {
        return new BitcoinJCoin[]{new BitcoinJCoin(this.value / divisor), new BitcoinJCoin(this.value % divisor)};
    }

    public long divide(final BitcoinJCoin divisor) {
        return this.value / divisor.value;
    }

    /**
     * Returns true if and only if this instance represents a monetary value greater than zero,
     * otherwise false.
     */
    public boolean isPositive() {
        return signum() == 1;
    }

    /**
     * Returns true if and only if this instance represents a monetary value less than zero,
     * otherwise false.
     */
    public boolean isNegative() {
        return signum() == -1;
    }

    /**
     * Returns true if and only if this instance represents zero monetary value,
     * otherwise false.
     */
    public boolean isZero() {
        return signum() == 0;
    }

    /**
     * Returns true if the monetary value represented by this instance is greater than that
     * of the given other Coin, otherwise false.
     */
    public boolean isGreaterThan(BitcoinJCoin other) {
        return compareTo(other) > 0;
    }

    /**
     * Returns true if the monetary value represented by this instance is less than that
     * of the given other Coin, otherwise false.
     */
    public boolean isLessThan(BitcoinJCoin other) {
        return compareTo(other) < 0;
    }

    public BitcoinJCoin shiftLeft(final int n) {
        return new BitcoinJCoin(this.value << n);
    }

    public BitcoinJCoin shiftRight(final int n) {
        return new BitcoinJCoin(this.value >> n);
    }

    @Override
    public int signum() {
        if (this.value == 0)
            return 0;
        return this.value < 0 ? -1 : 1;
    }

    public BitcoinJCoin negate() {
        return new BitcoinJCoin(-this.value);
    }

    /**
     * Returns the number of satoshis of this monetary value. It's deprecated in favour of accessing {@link #value}
     * directly.
     */
    public long longValue() {
        return this.value;
    }

    /**
     * Convert to number of satoshis
     *
     * @return decimal number of satoshis
     */
    public long toSat() {
        return this.value;
    }

    /**
     * Convert to number of bitcoin (in BTC)
     *
     * @return decimal number of bitcoin (in BTC)
     */
    public BigDecimal toBtc() {
        return satoshiToBtc(this.value);
    }

    private static final MonetaryFormat FRIENDLY_FORMAT = MonetaryFormat.BTC.minDecimals(2).repeatOptionalDecimals(1, 6).postfixCode();

    /**
     * Returns the value as a 0.12 type string. More digits after the decimal place will be used
     * if necessary, but two will always be present.
     */
    public String toFriendlyString() {
        return FRIENDLY_FORMAT.format(this).toString();
    }

    private static final MonetaryFormat PLAIN_FORMAT = MonetaryFormat.BTC.minDecimals(0).repeatOptionalDecimals(1, 8).noCode();

    /**
     * <p>
     * Returns the value as a plain string denominated in BTC.
     * The result is unformatted with no trailing zeroes.
     * For instance, a value of 150000 satoshis gives an output string of "0.0015" BTC
     * </p>
     */
    public String toPlainString() {
        return PLAIN_FORMAT.format(this).toString();
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return this.value == ((BitcoinJCoin) o).value;
    }

    @Override
    public int hashCode() {
        return (int) this.value;
    }

    @Override
    public int compareTo(final BitcoinJCoin other) {
        return Long.compare(this.value, other.value);
    }
}
