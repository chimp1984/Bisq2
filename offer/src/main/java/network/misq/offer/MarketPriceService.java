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

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;
import io.reactivex.subjects.BehaviorSubject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import network.misq.common.currency.MisqCurrency;
import network.misq.common.data.Couple;
import network.misq.common.monetary.Quote;
import network.misq.common.timer.UserThread;
import network.misq.common.util.CollectionUtil;
import network.misq.common.util.MathUtils;
import network.misq.common.util.ThreadingUtils;
import network.misq.network.NetworkService;
import network.misq.network.http.common.ClearNetHttpClient;
import network.misq.network.http.common.HttpClient;
import network.misq.network.http.common.Socks5ProxyProvider;
import network.misq.network.http.common.TorHttpClient;
import network.misq.network.p2p.NetworkType;
import network.misq.network.p2p.node.proxy.TorNetworkProxy;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;


@Slf4j
public class MarketPriceService {

    private static final long REQUEST_INTERVAL_SEC = 180;
    private ExecutorService executor;

    public static record Options(Set<Provider> providers) {
    }

    public static record Provider(String url, String operator, NetworkType networkType) {
    }

    private final List<Provider> providers;
    private final List<Provider> candidates = new ArrayList<>();
    private final NetworkService networkService;
    private final String userAgent;
    private Provider provider;
    private HttpClient httpClient;

    @Getter
    private final Map<String, MarketPrice> marketPriceByCurrencyMap = new HashMap<>();
    @Getter
    private final BehaviorSubject<Map<String, MarketPrice>> marketPriceSubject;

    public MarketPriceService(Options options, NetworkService networkService, String version) {
        providers = new ArrayList<>(options.providers);
        checkArgument(!providers.isEmpty(), "providers must not be empty");
        this.networkService = networkService;
        userAgent = "misq/" + version;

        marketPriceSubject = BehaviorSubject.create();
    }

    public CompletableFuture<Boolean> initialize() {
        executor = ThreadingUtils.getSingleThreadExecutor("MarketPriceRequest");
        selectProvider();
        // We start a request but we do not block until response arrives.
        request();

        UserThread.runPeriodically(() -> request().whenComplete((map, throwable) -> {
            if (map.isEmpty() || throwable != null) {
                selectProvider();
                httpClient.shutdown();
                httpClient = getHttpClient(provider);
                request();
            }
        }), REQUEST_INTERVAL_SEC, TimeUnit.SECONDS);
        return CompletableFuture.completedFuture(true);
    }

    public void shutdown() {
        ThreadingUtils.shutdownAndAwaitTermination(executor);
        httpClient.shutdown();
    }

    public Optional<MarketPrice> getMarketPrice(String currencyCode) {
        return Optional.ofNullable(marketPriceByCurrencyMap.get(currencyCode));
    }

    public CompletableFuture<Map<String, MarketPrice>> request() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                while (httpClient.hasPendingRequest() && !Thread.interrupted()) {
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ignore) {
                    }
                }
                long ts = System.currentTimeMillis();
                log.info("Request market price from {}", httpClient.getBaseUrl());
                String json = httpClient.get("getAllMarketPrices", Optional.of(new Couple<>("User-Agent", userAgent)));
                LinkedTreeMap<?, ?> map = new Gson().fromJson(json, LinkedTreeMap.class);
                List<?> list = (ArrayList<?>) map.get("data");
                list.forEach(obj -> {
                    try {
                        LinkedTreeMap<?, ?> treeMap = (LinkedTreeMap<?, ?>) obj;
                        String currencyCode = (String) treeMap.get("currencyCode");
                        String dataProvider = (String) treeMap.get("provider"); // Bisq-Aggregate or name of exchange of price feed
                        double price = (Double) treeMap.get("price");
                        // json uses double for our timestamp long value...
                        long timestampSec = MathUtils.doubleToLong((Double) treeMap.get("timestampSec"));

                        // We only get BTC based prices not fiat-fiat or altcoin-altcoin
                        boolean isFiat = MisqCurrency.isFiat(currencyCode);
                        String baseCurrencyCode = isFiat ? "BTC" : currencyCode;
                        String quoteCurrencyCode = isFiat ? currencyCode : "BTC";
                        Quote quote = Quote.fromPrice(price, baseCurrencyCode, quoteCurrencyCode);
                        marketPriceByCurrencyMap.put(currencyCode,
                                new MarketPrice(quote,
                                        timestampSec * 1000,
                                        dataProvider));
                    } catch (Throwable t) {
                        // We do not fail the whole request if one entry would be invalid
                        log.warn("Market price conversion failed: {} ", obj);
                        t.printStackTrace();
                    }
                });
                log.info("Market price request from {} resulted in {} items took {} ms",
                        httpClient.getBaseUrl(), list.size(), System.currentTimeMillis() - ts);
                marketPriceSubject.onNext(marketPriceByCurrencyMap);
                return marketPriceByCurrencyMap;
            } catch (IOException e) {
                e.printStackTrace();
                return new HashMap<>();
            }
        }, executor);
    }

    private void selectProvider() {
        if (candidates.isEmpty()) {
            // First try to use the clearnet candidate if clearnet is supported
            candidates.addAll(providers.stream()
                    .filter(prov -> networkService.getSupportedNetworkTypes().contains(NetworkType.CLEAR))
                    .filter(prov -> NetworkType.CLEAR == prov.networkType)
                    .toList());
            if (candidates.isEmpty()) {
                candidates.addAll(providers.stream()
                        .filter(prov -> networkService.getSupportedNetworkTypes().contains(prov.networkType))
                        .toList());
            }
        }
        Provider candidate = CollectionUtil.getRandomElement(candidates);
        checkNotNull(candidate);
        candidates.remove(candidate);
        provider = candidate;
        httpClient = getHttpClient(provider);
    }

    private HttpClient getHttpClient(Provider provider) {
        switch (provider.networkType) {
            case TOR:
                // If we have a socks5ProxyAddress defined in options we use that as proxy
                Socks5ProxyProvider socks5ProxyProvider = networkService.getSocks5ProxyAddress()
                        .map(Socks5ProxyProvider::new)
                        .orElse(networkService.getNetworkProxy(NetworkType.TOR)
                                .map(networkProxy -> {
                                    try {
                                        Socks5Proxy socksProxy = ((TorNetworkProxy) networkProxy).getSocksProxy();
                                        return new Socks5ProxyProvider(socksProxy);
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                        return null;
                                    }
                                })
                                .orElse(null));
                checkNotNull(socks5ProxyProvider, "No socks5ProxyAddress provided and no torNetworkProxy available.");
                return new TorHttpClient(provider.url, userAgent, socks5ProxyProvider);
            case I2P:
                // TODO We need to figure out how to get a proxy from i2p or require tor in any case
                throw new IllegalArgumentException("I2P providers not supported yet.");
            case CLEAR:
                return new ClearNetHttpClient(provider.url, userAgent);
            default:
                throw new IllegalArgumentException("Providers network type not recognized. " + provider.networkType);
        }
    }
}
