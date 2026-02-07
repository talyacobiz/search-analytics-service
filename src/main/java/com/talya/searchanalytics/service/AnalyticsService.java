package com.talya.searchanalytics.service;

import com.talya.searchanalytics.model.Product;
import com.talya.searchanalytics.repo.*;
import com.talya.searchanalytics.web.dto.AnalyticsFullResponse;
import com.talya.searchanalytics.web.dto.AnalyticsSummaryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private final SearchEventRepository searchRepo;
    private final AddToCartEventRepository cartRepo;
    private final PurchaseEventRepository purchaseRepo;
    private final ProductClickEventRepository clickRepo;
    private final BuyNowClickEventRepository buyNowRepo;
    private final CurrencyService currencyService;

    public AnalyticsSummaryResponse summary(String shopId, long fromMs, long toMs, Integer searchGroup) {
        try {
            log.info("Starting analytics summary for shopId: {}, fromMs: {}, toMs: {}, searchGroup: {}",
                    shopId, fromMs, toMs, searchGroup);

            // Calculate periods
            long periodMs = toMs - fromMs;
            long prevFromMs = fromMs - periodMs;
            long prevToMs = fromMs;

            // Get current and previous period analytics
            Map<String, Double> conversionRatesUsed = new HashMap<>();
            PeriodAnalytics currentPeriod = calculatePeriodAnalytics(shopId, fromMs, toMs, conversionRatesUsed,
                    searchGroup);
            PeriodAnalytics previousPeriod = calculatePeriodAnalytics(shopId, prevFromMs, prevToMs,
                    conversionRatesUsed, searchGroup);

            // Calculate percentage changes
            Double searchesChange = percentChange(
                    countSearches(shopId, prevFromMs, prevToMs, searchGroup),
                    countSearches(shopId, fromMs, toMs, searchGroup));
            Double addToCartChange = percentChange(previousPeriod.addToCartCount, currentPeriod.addToCartCount);
            Double purchasesChange = percentChange(previousPeriod.validPurchasedProductCount,
                    currentPeriod.validPurchasedProductCount);
            Double productClicksChange = percentChange(previousPeriod.productClicks, currentPeriod.productClicks);
            Double buyNowClicksChange = percentChange(previousPeriod.buyNowClicks, currentPeriod.buyNowClicks);
            Double revenueChange = percentChange(previousPeriod.validPurchaseRevenue,
                    currentPeriod.validPurchaseRevenue);
            Double purchaseValueChangePercent = percentChange(previousPeriod.totalPurchaseValueEur,
                    currentPeriod.totalPurchaseValueEur);
            Double convChange = percentChange(calculateConversionRate(previousPeriod),
                    calculateConversionRate(currentPeriod));
            Double clickThroughRateChange = percentChange(calculateClickThroughRate(previousPeriod),
                    calculateClickThroughRate(currentPeriod));
            Double addToCartRateChange = percentChange(calculateAddToCartRate(previousPeriod),
                    calculateAddToCartRate(currentPeriod));
            Double sessionsWithClicksChange = percentChange(previousPeriod.sessionsWithClicks.size(),
                    currentPeriod.sessionsWithClicks.size());
            Double sessionsWithAddToCartsChange = percentChange(previousPeriod.sessionsWithAddToCarts.size(),
                    currentPeriod.sessionsWithAddToCarts.size());
            Double sessionsWithPurchasesChange = percentChange(previousPeriod.sessionsWithPurchases.size(),
                    currentPeriod.sessionsWithPurchases.size());
            Double sessionsWithSearchesChange = percentChange(previousPeriod.totalSearchSessions.size(),
                    currentPeriod.totalSearchSessions.size());
            Double averageWordsPerQueryChange = percentChange(calculateAverageWordsPerQuery(previousPeriod),
                    calculateAverageWordsPerQuery(currentPeriod));
            Double longQueryCountChange = percentChange(previousPeriod.longQueryCount, currentPeriod.longQueryCount);
            Double longQueryPercentageChange = percentChange(calculateLongQueryPercentage(previousPeriod),
                    calculateLongQueryPercentage(currentPeriod));
            Double addToCartAmountChangePercent = percentChange(previousPeriod.addToCartAmount,
                    currentPeriod.addToCartAmount);

            // Generate time series
            List<AnalyticsSummaryResponse.TimePoint> series = generateTimeSeries(shopId, fromMs, toMs,
                    currentPeriod.currency, searchGroup);

            // Get top queries
            List<AnalyticsSummaryResponse.TopQuery> topQueries = getTopQueries(shopId, fromMs, toMs);

            return AnalyticsSummaryResponse.builder()
                    .totalSearches(countSearches(shopId, fromMs, toMs, searchGroup))
                    .totalAddToCart(currentPeriod.addToCartCount)
                    .totalPurchases(currentPeriod.validPurchasedProductCount)
                    .totalProductClicks(currentPeriod.productClicks)
                    .totalBuyNowClicks(currentPeriod.buyNowClicks)
                    .totalRevenue(currentPeriod.validPurchaseRevenue)
                    .conversionRate(Math.round(calculateConversionRate(currentPeriod) * 10.0) / 10.0)
                    .clickThroughRate(Math.round(calculateClickThroughRate(currentPeriod) * 10.0) / 10.0)
                    .timeSeries(series)
                    .topQueries(topQueries)
                    .searchesChangePercent(searchesChange)
                    .addToCartChangePercent(addToCartChange)
                    .purchasesChangePercent(purchasesChange)
                    .productClicksChangePercent(productClicksChange)
                    .buyNowClicksChangePercent(buyNowClicksChange)
                    .revenueChangePercent(revenueChange)
                    .conversionRateChangePercent(convChange)
                    .clickThroughRateChangePercent(clickThroughRateChange)
                    .totalAddToCartAmount(currentPeriod.addToCartAmount)
                    .prevAddToCartAmount(previousPeriod.addToCartAmount)
                    .addToCartAmountChangePercent(addToCartAmountChangePercent)
                    .currency(currentPeriod.currency)
                    .totalPurchaseValueEur(currentPeriod.totalPurchaseValueEur)
                    .purchaseValueChangePercent(purchaseValueChangePercent)
                    .conversionRatesUsed(conversionRatesUsed)
                    // New session-based metrics
                    .sessionsWithClicks((long) currentPeriod.sessionsWithClicks.size())
                    .sessionsWithAddToCarts((long) currentPeriod.sessionsWithAddToCarts.size())
                    .sessionsWithPurchases((long) currentPeriod.sessionsWithPurchases.size())
                    .sessionsWithSearches((long) currentPeriod.totalSearchSessions.size())
                    .addToCartRate(Math.round(calculateAddToCartRate(currentPeriod) * 10.0) / 10.0)
                    .averageWordsPerQuery(calculateAverageWordsPerQuery(currentPeriod))
                    .longQueryCount(currentPeriod.longQueryCount)
                    .longQueryPercentage(calculateLongQueryPercentage(currentPeriod))
                    .sessionsWithClicksChangePercent(sessionsWithClicksChange)
                    .sessionsWithAddToCartsChangePercent(sessionsWithAddToCartsChange)
                    .sessionsWithPurchasesChangePercent(sessionsWithPurchasesChange)
                    .sessionsWithSearchesChangePercent(sessionsWithSearchesChange)
                    .addToCartRateChangePercent(addToCartRateChange)
                    .averageWordsPerQueryChangePercent(averageWordsPerQueryChange)
                    .longQueryCountChangePercent(longQueryCountChange)
                    .longQueryPercentageChangePercent(longQueryPercentageChange)
                    .build();
        } catch (Exception e) {
            log.error("Error calculating analytics summary for shopId: {}", shopId, e);
            return createDefaultResponse();
        }
    }

    // Helper method to count searches with optional searchGroup filter
    private long countSearches(String shopId, long fromMs, long toMs, Integer searchGroup) {
        if (searchGroup != null) {
            return searchRepo.countByShopIdAndTimestampMsBetweenAndSearchGroup(shopId, fromMs, toMs, searchGroup);
        }
        return searchRepo.countByShopIdAndTimestampMsBetween(shopId, fromMs, toMs);
    }

    /**
     * Compare two search groups (A/B testing)
     * 
     * @param shopId Shop identifier
     * @param fromMs Start timestamp
     * @param toMs   End timestamp
     * @param groupA First group to compare (e.g., 0 for Shopify)
     * @param groupB Second group to compare (e.g., 1 for AI)
     * @return Comparison response with both summaries and differences
     */
    public com.talya.searchanalytics.web.dto.ABTestComparisonResponse compareGroups(
            String shopId, long fromMs, long toMs, Integer groupA, Integer groupB) {

        log.info("Comparing search groups - shopId: {}, fromMs: {}, toMs: {}, groupA: {}, groupB: {}",
                shopId, fromMs, toMs, groupA, groupB);

        // Get summaries for both groups
        AnalyticsSummaryResponse summaryA = summary(shopId, fromMs, toMs, groupA);
        AnalyticsSummaryResponse summaryB = summary(shopId, fromMs, toMs, groupB);

        // Calculate comparison metrics
        com.talya.searchanalytics.web.dto.ABTestComparisonResponse.ComparisonMetrics comparison = com.talya.searchanalytics.web.dto.ABTestComparisonResponse.ComparisonMetrics
                .builder()
                // Core metrics (percentage difference)
                .searchesDiff(percentDifference(summaryA.getTotalSearches(), summaryB.getTotalSearches()))
                .addToCartDiff(percentDifference(summaryA.getTotalAddToCart(), summaryB.getTotalAddToCart()))
                .purchasesDiff(percentDifference(summaryA.getTotalPurchases(), summaryB.getTotalPurchases()))
                .productClicksDiff(
                        percentDifference(summaryA.getTotalProductClicks(), summaryB.getTotalProductClicks()))
                .buyNowClicksDiff(percentDifference(summaryA.getTotalBuyNowClicks(), summaryB.getTotalBuyNowClicks()))
                .revenueDiff(percentDifference(summaryA.getTotalRevenue(), summaryB.getTotalRevenue()))

                // Rate metrics (absolute difference in percentage points)
                .conversionRateDiff(absoluteDifference(summaryA.getConversionRate(), summaryB.getConversionRate()))
                .clickThroughRateDiff(
                        absoluteDifference(summaryA.getClickThroughRate(), summaryB.getClickThroughRate()))
                .addToCartRateDiff(absoluteDifference(summaryA.getAddToCartRate(), summaryB.getAddToCartRate()))

                // Session metrics
                .sessionsWithClicksDiff(
                        percentDifference(summaryA.getSessionsWithClicks(), summaryB.getSessionsWithClicks()))
                .sessionsWithAddToCartsDiff(
                        percentDifference(summaryA.getSessionsWithAddToCarts(), summaryB.getSessionsWithAddToCarts()))
                .sessionsWithPurchasesDiff(
                        percentDifference(summaryA.getSessionsWithPurchases(), summaryB.getSessionsWithPurchases()))
                .sessionsWithSearchesDiff(
                        percentDifference(summaryA.getSessionsWithSearches(), summaryB.getSessionsWithSearches()))

                // Cart and purchase amounts
                .addToCartAmountDiff(
                        percentDifference(summaryA.getTotalAddToCartAmount(), summaryB.getTotalAddToCartAmount()))
                .purchaseValueEurDiff(
                        percentDifference(summaryA.getTotalPurchaseValueEur(), summaryB.getTotalPurchaseValueEur()))

                // Query complexity
                .averageWordsPerQueryDiff(
                        absoluteDifference(summaryA.getAverageWordsPerQuery(), summaryB.getAverageWordsPerQuery()))
                .longQueryCountDiff(percentDifference(summaryA.getLongQueryCount(), summaryB.getLongQueryCount()))
                .longQueryPercentageDiff(
                        absoluteDifference(summaryA.getLongQueryPercentage(), summaryB.getLongQueryPercentage()))

                // Winners
                .conversionWinner(determineWinner(summaryA.getConversionRate(), summaryB.getConversionRate()))
                .revenueWinner(determineWinner(summaryA.getTotalRevenue(), summaryB.getTotalRevenue()))
                .clickThroughWinner(determineWinner(summaryA.getClickThroughRate(), summaryB.getClickThroughRate()))
                .build();

        return com.talya.searchanalytics.web.dto.ABTestComparisonResponse.builder()
                .groupA(summaryA)
                .groupALabel(getGroupLabel(groupA))
                .groupB(summaryB)
                .groupBLabel(getGroupLabel(groupB))
                .comparison(comparison)
                .build();
    }

    // Helper: Get descriptive label for search group
    private String getGroupLabel(Integer group) {
        if (group == null)
            return "All";
        if (group == 0)
            return "Shopify Search";
        if (group == 1)
            return "AI Search";
        return "Group " + group;
    }

    // Helper: Calculate percentage difference (B vs A)
    private Double percentDifference(Number a, Number b) {
        if (a == null || b == null)
            return 0.0;
        double valA = a.doubleValue();
        double valB = b.doubleValue();
        if (valA == 0 && valB == 0)
            return 0.0;
        if (valA == 0)
            return 100.0;
        return Math.round(((valB - valA) / valA) * 1000.0) / 10.0;
    }

    // Helper: Calculate absolute difference (B - A) for rates
    private Double absoluteDifference(Number a, Number b) {
        if (a == null || b == null)
            return 0.0;
        double diff = b.doubleValue() - a.doubleValue();
        return Math.round(diff * 10.0) / 10.0;
    }

    // Helper: Determine winner
    private String determineWinner(Number a, Number b) {
        if (a == null || b == null)
            return "TIE";
        double valA = a.doubleValue();
        double valB = b.doubleValue();
        double diff = Math.abs(valB - valA);
        if (diff < 0.1)
            return "TIE"; // Less than 0.1% difference is a tie
        return valB > valA ? "B" : "A";
    }

    // Helper class to hold period analytics data
    private static class PeriodAnalytics {
        long validPurchasedProductCount = 0;
        double validPurchaseRevenue = 0.0;
        double totalPurchaseValueEur = 0.0;
        long addToCartCount = 0;
        double addToCartAmount = 0.0;
        long productClicks = 0;
        long buyNowClicks = 0;
        String currency = "NIS";
        java.util.Set<String> totalSearchSessions = new java.util.HashSet<>();
        java.util.Set<String> sessionsWithPurchases = new java.util.HashSet<>();
        java.util.Set<String> sessionsWithClicks = new java.util.HashSet<>();
        java.util.Set<String> sessionsWithAddToCarts = new java.util.HashSet<>();
        long totalWordCount = 0;
        long queryCount = 0;
        long longQueryCount = 0; // Queries with 3+ words
    }

    private PeriodAnalytics calculatePeriodAnalytics(String shopId, long fromMs, long toMs,
            Map<String, Double> conversionRatesUsed, Integer searchGroup) {
        return calculatePeriodAnalytics(shopId, fromMs, toMs, conversionRatesUsed, true, searchGroup);
    }

    private PeriodAnalytics calculatePeriodAnalytics(String shopId, long fromMs, long toMs,
            Map<String, Double> conversionRatesUsed, boolean enableLogging, Integer searchGroup) {
        PeriodAnalytics analytics = new PeriodAnalytics();

        // Get all events for this period (filtered by searchGroup if provided)
        List<com.talya.searchanalytics.model.SearchEvent> searchEvents = searchGroup != null
                ? searchRepo.findAllByShopIdAndTimestampMsBetweenAndSearchGroup(shopId, fromMs, toMs, searchGroup)
                : searchRepo.findAllByShopIdAndTimestampMsBetween(shopId, fromMs, toMs);
        List<com.talya.searchanalytics.model.AddToCartEvent> cartEvents = searchGroup != null
                ? cartRepo.findAllByShopIdAndTimestampMsBetweenAndSearchGroup(shopId, fromMs, toMs, searchGroup)
                : cartRepo.findAllByShopIdAndTimestampMsBetween(shopId, fromMs, toMs);
        List<com.talya.searchanalytics.model.ProductClickEvent> clickEvents = searchGroup != null
                ? clickRepo.findAllByShopIdAndTimestampMsBetweenAndSearchGroup(shopId, fromMs, toMs, searchGroup)
                : clickRepo.findAllByShopIdAndTimestampMsBetween(shopId, fromMs, toMs);
        List<com.talya.searchanalytics.model.BuyNowClickEvent> buyNowEvents = searchGroup != null
                ? buyNowRepo.findAllByShopIdAndTimestampMsBetweenAndSearchGroup(shopId, fromMs, toMs, searchGroup)
                : buyNowRepo.findAllByShopIdAndTimestampMsBetween(shopId, fromMs, toMs);
        List<com.talya.searchanalytics.model.PurchaseEvent> purchaseEvents = searchGroup != null
                ? purchaseRepo.findAllByShopIdAndTimestampMsBetweenAndSearchGroup(shopId, fromMs, toMs, searchGroup)
                : purchaseRepo.findAllByShopIdAndTimestampMsBetween(shopId, fromMs, toMs);

        // Build session products map and calculate word counts
        java.util.Map<String, java.util.Set<String>> sessionProducts = new java.util.HashMap<>();
        for (com.talya.searchanalytics.model.SearchEvent se : searchEvents) {
            sessionProducts.computeIfAbsent(se.getSessionId(), k -> new java.util.HashSet<>())
                    .addAll(se.getProductIds());
            if (se.getSessionId() != null) {
                analytics.totalSearchSessions.add(se.getSessionId());
            }
            // Calculate word count for this query
            if (se.getQuery() != null && !se.getQuery().trim().isEmpty()) {
                String[] words = se.getQuery().trim().split("\\s+");
                int wordCount = words.length;
                analytics.totalWordCount += wordCount;
                analytics.queryCount++;
                // Track queries with 3+ words
                if (wordCount >= 3) {
                    analytics.longQueryCount++;
                }
            }
        }

        // Filter and count add-to-cart events
        List<com.talya.searchanalytics.model.AddToCartEvent> validCartEvents = new ArrayList<>();
        for (com.talya.searchanalytics.model.AddToCartEvent e : cartEvents) {
            java.util.Set<String> products = sessionProducts.get(e.getSessionId());
            if (products != null && products.contains(e.getProductId())) {
                validCartEvents.add(e);
                // Track sessions with add-to-carts
                if (e.getSessionId() != null) {
                    analytics.sessionsWithAddToCarts.add(e.getSessionId());
                }
            }
        }
        analytics.addToCartCount = validCartEvents.size();
        analytics.addToCartAmount = validCartEvents.stream()
                .mapToDouble(ev -> ev.getPrice() != null ? ev.getPrice() : 0d).sum();
        analytics.currency = validCartEvents.stream().map(com.talya.searchanalytics.model.AddToCartEvent::getCurrency)
                .filter(c -> c != null && !c.isEmpty()).findFirst().orElse("NIS");

        // Build session cart products map
        java.util.Map<String, java.util.Set<String>> sessionCartProducts = new java.util.HashMap<>();
        for (com.talya.searchanalytics.model.AddToCartEvent e : validCartEvents) {
            sessionCartProducts.computeIfAbsent(e.getSessionId(), k -> new java.util.HashSet<>()).add(e.getProductId());
        }

        // Filter and calculate purchase metrics with fallback logic
        int fallbackOrdersCount = 0;
        for (com.talya.searchanalytics.model.PurchaseEvent pe : purchaseEvents) {
            if (pe.getProducts() != null && pe.getSessionId() != null) {
                java.util.Set<String> cartProductsInSession = sessionCartProducts.get(pe.getSessionId());

                int validProductsInThisOrder = 0;
                boolean hasProductPrices = false;

                for (Product product : pe.getProducts()) {
                    if (cartProductsInSession != null && cartProductsInSession.contains(product.getProductId())) {
                        analytics.validPurchasedProductCount++;
                        validProductsInThisOrder++;

                        if (product.getPrice() != null) {
                            hasProductPrices = true;
                            double productTotal = product.getPrice()
                                    * (product.getAmount() != null ? product.getAmount() : 1);
                            analytics.validPurchaseRevenue += productTotal;

                            if (pe.getCurrency() != null) {
                                String purchaseCurrency = pe.getCurrency().toUpperCase();
                                double rate = currencyService.getExchangeRate(purchaseCurrency);
                                conversionRatesUsed.put(purchaseCurrency, rate);
                                analytics.totalPurchaseValueEur += currencyService.convertToEur(productTotal,
                                        purchaseCurrency);
                            }
                        }
                    }
                }

                // Fallback: use proportional amount if no product prices
                if (validProductsInThisOrder > 0 && !hasProductPrices && pe.getTotalAmount() != null) {
                    fallbackOrdersCount++;

                    double proportionalAmount = (pe.getTotalAmount() * validProductsInThisOrder)
                            / pe.getProducts().size();
                    analytics.validPurchaseRevenue += proportionalAmount;

                    if (pe.getCurrency() != null) {
                        String purchaseCurrency = pe.getCurrency().toUpperCase();
                        double rate = currencyService.getExchangeRate(purchaseCurrency);
                        conversionRatesUsed.put(purchaseCurrency, rate);
                        analytics.totalPurchaseValueEur += currencyService.convertToEur(proportionalAmount,
                                purchaseCurrency);
                    }
                }

                // Track sessions with purchases
                if (validProductsInThisOrder > 0 && analytics.totalSearchSessions.contains(pe.getSessionId())) {
                    analytics.sessionsWithPurchases.add(pe.getSessionId());
                }
            }
        }

        // Filter and count click events
        for (com.talya.searchanalytics.model.ProductClickEvent e : clickEvents) {
            java.util.Set<String> products = sessionProducts.get(e.getSessionId());
            if (products != null && e.getProductId() != null && e.getProductId().startsWith("gid://shopify/Product/")) {
                analytics.productClicks++;
                if (e.getSessionId() != null) {
                    analytics.sessionsWithClicks.add(e.getSessionId());
                }
            }
        }

        // Filter and count buy now events
        for (com.talya.searchanalytics.model.BuyNowClickEvent e : buyNowEvents) {
            java.util.Set<String> products = sessionProducts.get(e.getSessionId());
            if (products != null && e.getProductId() != null && e.getProductId().startsWith("gid://shopify/Product/")) {
                analytics.buyNowClicks++;
            }
        }

        // Log summary with fallback info if used (only when logging is enabled)
        if (enableLogging) {
            String logMessage = String.format(
                    "Analytics processed - Cart: %d/%d valid, Purchases: %d products (%.2f€), Clicks: %d/%d, BuyNow: %d/%d, Conv: %.1f%%, CTR: %.1f%%",
                    analytics.addToCartCount, cartEvents.size(),
                    analytics.validPurchasedProductCount, analytics.totalPurchaseValueEur,
                    analytics.productClicks, clickEvents.size(),
                    analytics.buyNowClicks, buyNowEvents.size(),
                    calculateConversionRate(analytics), calculateClickThroughRate(analytics));

            if (fallbackOrdersCount > 0) {
                logMessage += String.format(" [Fallback: %d orders used avg pricing]", fallbackOrdersCount);
            }

            log.info(logMessage);
        }

        return analytics;
    }

    private double calculateConversionRate(PeriodAnalytics analytics) {
        return analytics.totalSearchSessions.size() == 0 ? 0
                : (analytics.sessionsWithPurchases.size() * 100.0 / analytics.totalSearchSessions.size());
    }

    private double calculateClickThroughRate(PeriodAnalytics analytics) {
        return analytics.totalSearchSessions.size() == 0 ? 0
                : (analytics.sessionsWithClicks.size() * 100.0 / analytics.totalSearchSessions.size());
    }

    private double calculateAddToCartRate(PeriodAnalytics analytics) {
        return analytics.totalSearchSessions.size() == 0 ? 0
                : (analytics.sessionsWithAddToCarts.size() * 100.0 / analytics.totalSearchSessions.size());
    }

    private double calculateAverageWordsPerQuery(PeriodAnalytics analytics) {
        return analytics.queryCount == 0 ? 0.0
                : Math.round((analytics.totalWordCount * 10.0) / analytics.queryCount) / 10.0;
    }

    private double calculateLongQueryPercentage(PeriodAnalytics analytics) {
        return analytics.queryCount == 0 ? 0.0
                : Math.round((analytics.longQueryCount * 1000.0) / analytics.queryCount) / 10.0;
    }

    private List<AnalyticsSummaryResponse.TimePoint> generateTimeSeries(String shopId, long fromMs, long toMs,
            String currency, Integer searchGroup) {
        List<AnalyticsSummaryResponse.TimePoint> series = new ArrayList<>();
        LocalDate start = Instant.ofEpochMilli(fromMs).atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate end = Instant.ofEpochMilli(toMs).atZone(ZoneOffset.UTC).toLocalDate();

        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            long dayStartMs = d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
            long dayEndMs = d.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() - 1;

            Map<String, Double> dayConversionRates = new HashMap<>();
            PeriodAnalytics dayAnalytics = calculatePeriodAnalytics(shopId, dayStartMs, dayEndMs, dayConversionRates,
                    false, searchGroup);

            series.add(AnalyticsSummaryResponse.TimePoint.builder()
                    .date(d.toString())
                    .searches((int) countSearches(shopId, dayStartMs, dayEndMs, searchGroup))
                    .addToCart((int) dayAnalytics.addToCartCount)
                    .purchases((int) dayAnalytics.validPurchasedProductCount)
                    .addToCartAmount(dayAnalytics.addToCartAmount)
                    .currency(dayAnalytics.currency.isEmpty() ? currency : dayAnalytics.currency)
                    .build());
        }
        return series;
    }

    private List<AnalyticsSummaryResponse.TopQuery> getTopQueries(String shopId, long fromMs, long toMs) {
        List<Object[]> rows = searchRepo.topQueries(shopId, fromMs, toMs);
        List<AnalyticsSummaryResponse.TopQuery> topQueries = new ArrayList<>();
        for (Object[] r : rows) {
            String term = (String) r[0];
            long cnt = (r[1] instanceof Long) ? (Long) r[1] : ((Number) r[1]).longValue();
            topQueries.add(new AnalyticsSummaryResponse.TopQuery(term, cnt));
        }
        return topQueries;
    }

    private AnalyticsSummaryResponse createDefaultResponse() {
        return AnalyticsSummaryResponse.builder()
                .totalSearches(0L).totalAddToCart(0L).totalPurchases(0L)
                .totalProductClicks(0L).totalBuyNowClicks(0L).totalRevenue(0.0)
                .conversionRate(0.0).clickThroughRate(0.0)
                .timeSeries(new ArrayList<>()).topQueries(new ArrayList<>())
                .searchesChangePercent(0.0).addToCartChangePercent(0.0).purchasesChangePercent(0.0)
                .productClicksChangePercent(0.0).buyNowClicksChangePercent(0.0).revenueChangePercent(0.0)
                .conversionRateChangePercent(0.0).clickThroughRateChangePercent(0.0)
                .totalAddToCartAmount(0.0).prevAddToCartAmount(0.0).addToCartAmountChangePercent(0.0)
                .currency("NIS").totalPurchaseValueEur(0.0).purchaseValueChangePercent(0.0)
                .conversionRatesUsed(new HashMap<>())
                // New session-based metrics
                .sessionsWithClicks(0L).sessionsWithAddToCarts(0L).sessionsWithPurchases(0L)
                .sessionsWithSearches(0L).addToCartRate(0.0)
                .sessionsWithClicksChangePercent(0.0).sessionsWithAddToCartsChangePercent(0.0)
                .sessionsWithPurchasesChangePercent(0.0).sessionsWithSearchesChangePercent(0.0)
                .addToCartRateChangePercent(0.0)
                // Query complexity metrics
                .averageWordsPerQuery(0.0).averageWordsPerQueryChangePercent(0.0)
                .longQueryCount(0L).longQueryPercentage(0.0)
                .longQueryCountChangePercent(0.0).longQueryPercentageChangePercent(0.0)
                .build();
    }

    private Double percentChange(double prev, double curr) {
        if (prev == 0 && curr == 0)
            return 0.0;
        if (prev == 0)
            return 100.0;
        return Math.round(((curr - prev) / prev) * 1000.0) / 10.0;
    }

    public AnalyticsFullResponse full(String shopId, long fromMs, long toMs) {
        return AnalyticsFullResponse.builder()
                .shopId(shopId)
                .fromMs(fromMs)
                .toMs(toMs)
                .searchEvents(searchRepo.findAllByShopIdAndTimestampMsBetween(shopId, fromMs, toMs))
                .productClickEvents(clickRepo.findAllByShopIdAndTimestampMsBetween(shopId, fromMs, toMs))
                .addToCartEvents(cartRepo.findAllByShopIdAndTimestampMsBetween(shopId, fromMs, toMs))
                .purchaseEvents(purchaseRepo.findAllByShopIdAndTimestampMsBetween(shopId, fromMs, toMs))
                .buyNowClickEvents(buyNowRepo.findAllByShopIdAndTimestampMsBetween(shopId, fromMs, toMs))
                .build();
    }

    public void recordBuyNow(String shopId, java.util.Map<String, Object> event) {
        String sessionId = (String) event.get("session_id");
        String productId = (String) event.get("product_id");

        // Validate that this event came from a search session
        if (sessionId != null) {
            List<com.talya.searchanalytics.model.SearchEvent> searchEvents = searchRepo
                    .findAllByShopIdAndSessionId(shopId, sessionId);
            if (searchEvents.isEmpty()) {
                return; // Skip events not from search sessions
            }

            // If productId is provided, validate it was in search results
            if (productId != null) {
                boolean validProduct = searchEvents.stream()
                        .anyMatch(se -> se.getProductIds() != null && se.getProductIds().contains(productId));
                if (!validProduct) {
                    return; // Skip if product wasn't in search results
                }
            }
        } else {
            return; // Skip events without session
        }

        com.talya.searchanalytics.model.BuyNowClickEvent buyNow = new com.talya.searchanalytics.model.BuyNowClickEvent();
        buyNow.setShopId(shopId);
        buyNow.setProductId(productId);

        // Handle price conversion safely
        Object priceObj = event.get("price");
        if (priceObj != null) {
            if (priceObj instanceof Number) {
                buyNow.setPrice(((Number) priceObj).doubleValue());
            } else if (priceObj instanceof String) {
                try {
                    buyNow.setPrice(Double.parseDouble((String) priceObj));
                } catch (NumberFormatException e) {
                    buyNow.setPrice(null);
                }
            }
        }

        buyNow.setCurrency((String) event.get("currency"));
        buyNow.setSessionId(sessionId);
        buyNow.setClientId((String) event.get("client_id"));
        buyNow.setTimestampMs(event.get("timestamp") != null ? ((Number) event.get("timestamp")).longValue()
                : System.currentTimeMillis());
        buyNowRepo.save(buyNow);
    }

    public void recordProductClick(String shopId, java.util.Map<String, Object> event) {
        String sessionId = (String) event.get("session_id");
        String productId = (String) event.get("product_id");

        // Validate that this event came from a search session
        if (sessionId != null) {
            List<com.talya.searchanalytics.model.SearchEvent> searchEvents = searchRepo
                    .findAllByShopIdAndSessionId(shopId, sessionId);
            if (searchEvents.isEmpty()) {
                return; // Skip events not from search sessions
            }

            // If productId is provided, validate it was in search results
            if (productId != null) {
                boolean validProduct = searchEvents.stream()
                        .anyMatch(se -> se.getProductIds() != null && se.getProductIds().contains(productId));
                if (!validProduct) {
                    return; // Skip if product wasn't in search results
                }
            }
        } else {
            return; // Skip events without session
        }

        com.talya.searchanalytics.model.ProductClickEvent click = new com.talya.searchanalytics.model.ProductClickEvent();
        click.setShopId(shopId);
        click.setQuery((String) event.get("query"));
        click.setProductId(productId);
        click.setSearchId((String) event.get("search_id"));
        click.setProductTitle((String) event.get("product_title"));
        click.setUrl((String) event.get("url"));
        click.setSessionId(sessionId);
        click.setClientId((String) event.get("client_id"));
        click.setTimestampMs(event.get("timestamp") != null ? ((Number) event.get("timestamp")).longValue()
                : System.currentTimeMillis());
        clickRepo.save(click);
    }
}