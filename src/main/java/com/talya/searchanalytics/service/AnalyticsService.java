package com.talya.searchanalytics.service;

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
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private final SearchEventRepository searchRepo;
    private final AddToCartEventRepository cartRepo;
    private final PurchaseEventRepository purchaseRepo;
    private final ProductClickEventRepository clickRepo;
    private final BuyNowClickEventRepository buyNowRepo;

    public AnalyticsSummaryResponse summary(String shopId, long fromMs, long toMs) {
        try {
            log.info("Starting analytics summary for shopId: {}, fromMs: {}, toMs: {}", shopId, fromMs, toMs);

            // Count total search events (each search counts separately)
            long searches = searchRepo.countByShopIdAndTimestampMsBetween(shopId, fromMs, toMs);

            // Calculate previous period
            long periodMs = toMs - fromMs;
            long prevFromMs = fromMs - periodMs;
            long prevToMs = fromMs;
            long prevSearches = searchRepo.countByShopIdAndTimestampMsBetween(shopId, prevFromMs, prevToMs);

            // Get all relevant events
            List<com.talya.searchanalytics.model.SearchEvent> searchEvents = searchRepo
                    .findAllByShopIdAndTimestampMsBetween(shopId, fromMs, toMs);
            List<com.talya.searchanalytics.model.AddToCartEvent> cartEvents = cartRepo
                    .findAllByShopIdAndTimestampMsBetween(shopId, fromMs, toMs);
            List<com.talya.searchanalytics.model.ProductClickEvent> clickEvents = clickRepo
                    .findAllByShopIdAndTimestampMsBetween(shopId, fromMs, toMs);
            List<com.talya.searchanalytics.model.BuyNowClickEvent> buyNowEvents = buyNowRepo
                    .findAllByShopIdAndTimestampMsBetween(shopId, fromMs, toMs);
            List<com.talya.searchanalytics.model.AddToCartEvent> prevCartEvents = cartRepo
                    .findAllByShopIdAndTimestampMsBetween(shopId, prevFromMs, prevToMs);
            List<com.talya.searchanalytics.model.ProductClickEvent> prevClickEvents = clickRepo
                    .findAllByShopIdAndTimestampMsBetween(shopId, prevFromMs, prevToMs);
            List<com.talya.searchanalytics.model.BuyNowClickEvent> prevBuyNowEvents = buyNowRepo
                    .findAllByShopIdAndTimestampMsBetween(shopId, prevFromMs, prevToMs);
            List<com.talya.searchanalytics.model.SearchEvent> prevSearchEvents = searchRepo
                    .findAllByShopIdAndTimestampMsBetween(shopId, prevFromMs, prevToMs);

            // Helper: sessionId -> list of productIds from searches
            java.util.Map<String, java.util.Set<String>> sessionProducts = new java.util.HashMap<>();
            for (com.talya.searchanalytics.model.SearchEvent se : searchEvents) {
                sessionProducts.computeIfAbsent(se.getSessionId(), k -> new java.util.HashSet<>())
                        .addAll(se.getProductIds());
            }
            java.util.Map<String, java.util.Set<String>> prevSessionProducts = new java.util.HashMap<>();
            for (com.talya.searchanalytics.model.SearchEvent se : prevSearchEvents) {
                prevSessionProducts.computeIfAbsent(se.getSessionId(), k -> new java.util.HashSet<>())
                        .addAll(se.getProductIds());
            }

            // Filter add-to-cart events for current period
            List<com.talya.searchanalytics.model.AddToCartEvent> validCartEvents = new ArrayList<>();
            for (com.talya.searchanalytics.model.AddToCartEvent e : cartEvents) {
                java.util.Set<String> products = sessionProducts.get(e.getSessionId());
                if (products != null && products.contains(e.getProductId())) {
                    validCartEvents.add(e);
                }
            }
            long addToCart = validCartEvents.size();
            Double totalAddToCartAmount = validCartEvents.stream()
                    .mapToDouble(ev -> ev.getPrice() != null ? ev.getPrice() : 0d).sum();
            String currency = validCartEvents.stream().map(com.talya.searchanalytics.model.AddToCartEvent::getCurrency)
                    .filter(c -> c != null && !c.isEmpty()).findFirst().orElse("NIS");

            // Build map of sessionId -> set of product IDs that were added to cart
            // This ensures we only count purchases where the product was added to cart in
            // the SAME session
            java.util.Map<String, java.util.Set<String>> sessionCartProducts = new java.util.HashMap<>();
            for (com.talya.searchanalytics.model.AddToCartEvent e : validCartEvents) {
                sessionCartProducts.computeIfAbsent(e.getSessionId(), k -> new java.util.HashSet<>())
                        .add(e.getProductId());
            }

            // Filter purchase events - count each product that was added to cart IN THE
            // SAME SESSION
            List<com.talya.searchanalytics.model.PurchaseEvent> purchaseEvents = purchaseRepo
                    .findAllByShopIdAndTimestampMsBetween(shopId, fromMs, toMs);
            long validPurchasedProductCount = 0; // Count individual products, not orders
            double validPurchaseRevenue = 0.0;

            log.info(
                    "🔍 Purchase Filtering Debug for shopId {}: {} total purchase events, {} sessions with cart events",
                    shopId, purchaseEvents.size(), sessionCartProducts.size());

            for (com.talya.searchanalytics.model.PurchaseEvent pe : purchaseEvents) {
                if (pe.getProductIds() != null && pe.getSessionId() != null) {
                    log.debug("Purchase event: {} products, sessionId: {}, clientId: {}",
                            pe.getProductIds().size(), pe.getSessionId(), pe.getClientId());

                    // Get the cart products for THIS session
                    java.util.Set<String> cartProductsInSession = sessionCartProducts.get(pe.getSessionId());

                    // Count each product that was added to cart in THIS session
                    int validProductsInThisOrder = 0;
                    for (String productId : pe.getProductIds()) {
                        boolean inCartInSameSession = cartProductsInSession != null &&
                                cartProductsInSession.contains(productId);
                        log.debug("  Product {}: in cart (same session) = {}", productId, inCartInSameSession);

                        if (inCartInSameSession) {
                            validPurchasedProductCount++; // Count each valid product
                            validProductsInThisOrder++;
                        }
                    }
                    // Add revenue if at least one product was valid
                    if (validProductsInThisOrder > 0) {
                        validPurchaseRevenue += (pe.getTotalAmount() != null ? pe.getTotalAmount() : 0.0);
                        log.info("✅ Valid purchase: {} products matched, revenue: {}, sessionId: {}, clientId: {}",
                                validProductsInThisOrder, pe.getTotalAmount(), pe.getSessionId(), pe.getClientId());
                    } else {
                        log.warn(
                                "❌ Purchase filtered out: none of {} products were in cart for this session. SessionId: {}, ClientId: {}, ProductIds: {}, Session has cart: {}",
                                pe.getProductIds().size(), pe.getSessionId(), pe.getClientId(),
                                pe.getProductIds(), cartProductsInSession != null);
                    }
                }
            }

            // Use filtered purchase data instead of raw counts
            long purchases = validPurchasedProductCount; // Now counting individual products
            Double totalRevenue = validPurchaseRevenue;

            log.info(
                    "Purchase Summary for shopId {}: {} total purchase events, {} valid products purchased, total revenue: {} {}",
                    shopId, purchaseEvents.size(), purchases, String.format("%.2f", totalRevenue), currency);

            // Calculate Search-to-Purchase Conversion Rate
            // Formula: (Sessions with an Order that included a Search / Total Sessions with
            // a Search) × 100

            // Get total unique sessions that had searches
            java.util.Set<String> totalSearchSessions = new java.util.HashSet<>();
            for (com.talya.searchanalytics.model.SearchEvent se : searchEvents) {
                if (se.getSessionId() != null) {
                    totalSearchSessions.add(se.getSessionId());
                }
            }

            // Find sessions that had both a search AND a purchase (with valid cart
            // products)
            java.util.Set<String> sessionsWithPurchases = new java.util.HashSet<>();

            for (com.talya.searchanalytics.model.PurchaseEvent pe : purchaseEvents) {
                if (pe.getProductIds() != null && pe.getSessionId() != null) {
                    // Check if this session had searches
                    if (!totalSearchSessions.contains(pe.getSessionId())) {
                        continue; // Skip purchases from sessions without searches
                    }

                    // Check if any purchased product was in the cart for THIS session
                    java.util.Set<String> cartProductsInSession = sessionCartProducts.get(pe.getSessionId());
                    boolean hasValidProduct = false;

                    if (cartProductsInSession != null) {
                        for (String purchasedProductId : pe.getProductIds()) {
                            if (cartProductsInSession.contains(purchasedProductId)) {
                                hasValidProduct = true;
                                break;
                            }
                        }
                    }

                    if (hasValidProduct) {
                        sessionsWithPurchases.add(pe.getSessionId());
                    }
                }
            }

            // Conversion Rate = (Sessions with Purchase / Total Search Sessions) × 100
            double conv = (totalSearchSessions.size() == 0) ? 0
                    : (sessionsWithPurchases.size() * 100.0 / totalSearchSessions.size());
            log.info("Conversion Rate for shopId {}: {}/{} sessions converted = {:.2f}%",
                    shopId, sessionsWithPurchases.size(), totalSearchSessions.size(), conv);

            // Filter product click events for current period
            List<com.talya.searchanalytics.model.ProductClickEvent> validClickEvents = new ArrayList<>();
            log.info("Processing {} product click events for shopId: {}", clickEvents.size(), shopId);
            for (com.talya.searchanalytics.model.ProductClickEvent e : clickEvents) {
                java.util.Set<String> products = sessionProducts.get(e.getSessionId());
                log.debug("ProductClick - sessionId: {}, productId: {}, hasSession: {}", e.getSessionId(),
                        e.getProductId(), products != null);

                // Session-based validation: if session has search results and product ID is
                // valid format, allow it
                if (products != null && e.getProductId() != null
                        && e.getProductId().startsWith("gid://shopify/Product/")) {
                    validClickEvents.add(e);
                    log.debug("ProductClick - VALID: sessionId: {}, productId: {}", e.getSessionId(), e.getProductId());
                } else {
                    log.debug("ProductClick - INVALID: sessionId: {}, productId: {}, hasSession: {}", e.getSessionId(),
                            e.getProductId(), products != null);
                }
            }
            long productClicks = validClickEvents.size();
            log.info("Valid product clicks: {} out of {}", productClicks, clickEvents.size());

            // Filter buy now events for current period
            List<com.talya.searchanalytics.model.BuyNowClickEvent> validBuyNowEvents = new ArrayList<>();
            log.info("Processing {} buy now events for shopId: {}", buyNowEvents.size(), shopId);
            for (com.talya.searchanalytics.model.BuyNowClickEvent e : buyNowEvents) {
                java.util.Set<String> products = sessionProducts.get(e.getSessionId());
                log.debug("BuyNow - sessionId: {}, productId: {}, hasSession: {}", e.getSessionId(), e.getProductId(),
                        products != null);

                // Session-based validation: if session has search results and product ID is
                // valid format, allow it
                if (products != null && e.getProductId() != null
                        && e.getProductId().startsWith("gid://shopify/Product/")) {
                    validBuyNowEvents.add(e);
                    log.debug("BuyNow - VALID: sessionId: {}, productId: {}", e.getSessionId(), e.getProductId());
                } else {
                    log.debug("BuyNow - INVALID: sessionId: {}, productId: {}, hasSession: {}", e.getSessionId(),
                            e.getProductId(), products != null);
                }
            }
            long buyNowClicks = validBuyNowEvents.size();
            log.info("Valid buy now clicks: {} out of {}", buyNowClicks, buyNowEvents.size());

            // Calculate click-through rate using searches that actually had clicks
            // Create chronological mapping of searches in each session
            java.util.Map<String, java.util.List<String>> sessionToSearchIds = new java.util.HashMap<>();
            for (com.talya.searchanalytics.model.SearchEvent se : searchEvents) {
                if (se.getSessionId() != null && se.getSearchId() != null) {
                    sessionToSearchIds.computeIfAbsent(se.getSessionId(), k -> new java.util.ArrayList<>())
                            .add(se.getSearchId());
                }
            }

            // Sort searches by timestamp to maintain chronological order
            for (java.util.List<String> searchIds : sessionToSearchIds.values()) {
                // Assuming searchIds are already in chronological order from the query
            }

            java.util.Set<String> searchesWithClicks = new java.util.HashSet<>();
            log.info("Calculating CTR: total searches={}, valid clicks={}", searches, validClickEvents.size());

            // Track how many clicks have been processed per session
            java.util.Map<String, Integer> sessionClickCount = new java.util.HashMap<>();

            for (com.talya.searchanalytics.model.ProductClickEvent e : validClickEvents) {
                log.debug("Click event - searchId: {}, sessionId: {}, query: {}", e.getSearchId(), e.getSessionId(),
                        e.getQuery());

                if (e.getSearchId() != null && !e.getSearchId().isEmpty()) {
                    searchesWithClicks.add(e.getSearchId());
                } else if (e.getSessionId() != null) {
                    java.util.List<String> searchIds = sessionToSearchIds.get(e.getSessionId());
                    if (searchIds != null) {
                        // Get current click count for this session
                        int clickCount = sessionClickCount.getOrDefault(e.getSessionId(), 0);

                        // Map this click to the next search in chronological order
                        if (clickCount < searchIds.size()) {
                            String searchId = searchIds.get(clickCount);
                            searchesWithClicks.add(searchId);
                            log.debug("Mapped click #{} in session {} to search: {}", clickCount + 1, e.getSessionId(),
                                    searchId);
                        }

                        // Increment click count for this session
                        sessionClickCount.put(e.getSessionId(), clickCount + 1);
                    }
                }
            }

            log.info("Unique searches with clicks: {}", searchesWithClicks.size());
            double clickThroughRate = (searches == 0) ? 0 : (searchesWithClicks.size() * 100.0 / searches);

            // Filter add-to-cart events for previous period
            List<com.talya.searchanalytics.model.AddToCartEvent> validPrevCartEvents = new ArrayList<>();
            for (com.talya.searchanalytics.model.AddToCartEvent e : prevCartEvents) {
                java.util.Set<String> products = prevSessionProducts.get(e.getSessionId());
                if (products != null && products.contains(e.getProductId())) {
                    validPrevCartEvents.add(e);
                }
            }

            // Build map of sessionId -> set of product IDs for previous period
            java.util.Map<String, java.util.Set<String>> prevSessionCartProducts = new java.util.HashMap<>();
            for (com.talya.searchanalytics.model.AddToCartEvent e : validPrevCartEvents) {
                prevSessionCartProducts.computeIfAbsent(e.getSessionId(), k -> new java.util.HashSet<>())
                        .add(e.getProductId());
            }

            // Filter previous period purchase events - count each product IN THE SAME
            // SESSION
            List<com.talya.searchanalytics.model.PurchaseEvent> prevPurchaseEvents = purchaseRepo
                    .findAllByShopIdAndTimestampMsBetween(shopId, prevFromMs, prevToMs);
            long validPrevPurchasedProductCount = 0; // Count individual products
            double validPrevPurchaseRevenue = 0.0;

            for (com.talya.searchanalytics.model.PurchaseEvent pe : prevPurchaseEvents) {
                if (pe.getProductIds() != null && pe.getSessionId() != null) {
                    // Get the cart products for THIS session
                    java.util.Set<String> cartProductsInSession = prevSessionCartProducts.get(pe.getSessionId());

                    int validProductsInThisOrder = 0;
                    if (cartProductsInSession != null) {
                        for (String productId : pe.getProductIds()) {
                            if (cartProductsInSession.contains(productId)) {
                                validPrevPurchasedProductCount++; // Count each valid product
                                validProductsInThisOrder++;
                            }
                        }
                    }
                    if (validProductsInThisOrder > 0) {
                        validPrevPurchaseRevenue += (pe.getTotalAmount() != null ? pe.getTotalAmount() : 0.0);
                    }
                }
            }

            long prevPurchases = validPrevPurchasedProductCount;
            Double prevRevenue = validPrevPurchaseRevenue;

            // Calculate previous period Search-to-Purchase Conversion Rate
            // Formula: (Sessions with an Order that included a Search / Total Sessions with
            // a Search) × 100

            // Get total unique sessions that had searches in previous period
            java.util.Set<String> prevTotalSearchSessions = new java.util.HashSet<>();
            for (com.talya.searchanalytics.model.SearchEvent se : prevSearchEvents) {
                if (se.getSessionId() != null) {
                    prevTotalSearchSessions.add(se.getSessionId());
                }
            }

            // Find sessions that had both a search AND a purchase (with valid cart
            // products)
            java.util.Set<String> prevSessionsWithPurchases = new java.util.HashSet<>();

            for (com.talya.searchanalytics.model.PurchaseEvent pe : prevPurchaseEvents) {
                if (pe.getProductIds() != null && pe.getSessionId() != null) {
                    // Check if this session had searches
                    if (!prevTotalSearchSessions.contains(pe.getSessionId())) {
                        continue; // Skip purchases from sessions without searches
                    }

                    // Check if any purchased product was in the cart for THIS session
                    java.util.Set<String> cartProductsInSession = prevSessionCartProducts.get(pe.getSessionId());
                    boolean hasValidProduct = false;

                    if (cartProductsInSession != null) {
                        for (String purchasedProductId : pe.getProductIds()) {
                            if (cartProductsInSession.contains(purchasedProductId)) {
                                hasValidProduct = true;
                                break;
                            }
                        }
                    }

                    if (hasValidProduct) {
                        prevSessionsWithPurchases.add(pe.getSessionId());
                    }
                }
            }

            double prevConv = (prevTotalSearchSessions.size() == 0) ? 0
                    : (prevSessionsWithPurchases.size() * 100.0 / prevTotalSearchSessions.size());

            // Filter previous period events
            List<com.talya.searchanalytics.model.ProductClickEvent> validPrevClickEvents = new ArrayList<>();
            for (com.talya.searchanalytics.model.ProductClickEvent e : prevClickEvents) {
                java.util.Set<String> products = prevSessionProducts.get(e.getSessionId());
                if (products != null && e.getProductId() != null
                        && e.getProductId().startsWith("gid://shopify/Product/")) {
                    validPrevClickEvents.add(e);
                }
            }
            long prevProductClicks = validPrevClickEvents.size();

            List<com.talya.searchanalytics.model.BuyNowClickEvent> validPrevBuyNowEvents = new ArrayList<>();
            for (com.talya.searchanalytics.model.BuyNowClickEvent e : prevBuyNowEvents) {
                java.util.Set<String> products = prevSessionProducts.get(e.getSessionId());
                if (products != null && e.getProductId() != null
                        && e.getProductId().startsWith("gid://shopify/Product/")) {
                    validPrevBuyNowEvents.add(e);
                }
            }
            long prevBuyNowClicks = validPrevBuyNowEvents.size();

            // Calculate previous period click-through rate using same logic
            java.util.Map<String, java.util.List<String>> prevSessionToSearchIds = new java.util.HashMap<>();
            for (com.talya.searchanalytics.model.SearchEvent se : prevSearchEvents) {
                if (se.getSessionId() != null && se.getSearchId() != null) {
                    prevSessionToSearchIds.computeIfAbsent(se.getSessionId(), k -> new java.util.ArrayList<>())
                            .add(se.getSearchId());
                }
            }

            java.util.Set<String> prevSearchesWithClicks = new java.util.HashSet<>();
            java.util.Map<String, Integer> prevSessionClickCount = new java.util.HashMap<>();

            for (com.talya.searchanalytics.model.ProductClickEvent e : validPrevClickEvents) {
                if (e.getSearchId() != null && !e.getSearchId().isEmpty()) {
                    prevSearchesWithClicks.add(e.getSearchId());
                } else if (e.getSessionId() != null) {
                    java.util.List<String> searchIds = prevSessionToSearchIds.get(e.getSessionId());
                    if (searchIds != null) {
                        int clickCount = prevSessionClickCount.getOrDefault(e.getSessionId(), 0);
                        if (clickCount < searchIds.size()) {
                            String searchId = searchIds.get(clickCount);
                            prevSearchesWithClicks.add(searchId);
                        }
                        prevSessionClickCount.put(e.getSessionId(), clickCount + 1);
                    }
                }
            }
            double prevClickThroughRate = (prevSearches == 0) ? 0
                    : (prevSearchesWithClicks.size() * 100.0 / prevSearches);

            Double prevAddToCartAmount = validPrevCartEvents.stream()
                    .mapToDouble(ev -> ev.getPrice() != null ? ev.getPrice() : 0d).sum();
            if (totalAddToCartAmount == null)
                totalAddToCartAmount = 0d;
            if (prevAddToCartAmount == null)
                prevAddToCartAmount = 0d;
            Double addToCartAmountChangePercent = percentChange(prevAddToCartAmount, totalAddToCartAmount);

            // Calculate percentage change
            Double searchesChange = percentChange(prevSearches, searches);
            Double addToCartChange = percentChange(validPrevCartEvents.size(), addToCart);
            Double purchasesChange = percentChange(prevPurchases, purchases);
            Double productClicksChange = percentChange(prevProductClicks, productClicks);
            Double buyNowClicksChange = percentChange(prevBuyNowClicks, buyNowClicks);
            Double revenueChange = percentChange(prevRevenue, totalRevenue);
            Double convChange = percentChange(prevConv, conv);
            Double clickThroughRateChange = percentChange(prevClickThroughRate, clickThroughRate);

            // Aggregate time series data
            List<AnalyticsSummaryResponse.TimePoint> series = new ArrayList<>();
            LocalDate start = Instant.ofEpochMilli(fromMs).atZone(ZoneOffset.UTC).toLocalDate();
            LocalDate end = Instant.ofEpochMilli(toMs).atZone(ZoneOffset.UTC).toLocalDate();
            for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                long dayStartMs = d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
                long dayEndMs = d.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() - 1;
                int daySearches = (int) searchRepo.countByShopIdAndTimestampMsBetween(shopId, dayStartMs, dayEndMs);

                // Filter add-to-cart events for this day
                List<com.talya.searchanalytics.model.AddToCartEvent> dayCartEvents = cartRepo
                        .findAllByShopIdAndTimestampMsBetween(shopId, dayStartMs, dayEndMs);
                List<com.talya.searchanalytics.model.SearchEvent> daySearchEvents = searchRepo
                        .findAllByShopIdAndTimestampMsBetween(shopId, dayStartMs, dayEndMs);
                java.util.Map<String, java.util.Set<String>> daySessionProducts = new java.util.HashMap<>();
                for (com.talya.searchanalytics.model.SearchEvent se : daySearchEvents) {
                    daySessionProducts.computeIfAbsent(se.getSessionId(), k -> new java.util.HashSet<>())
                            .addAll(se.getProductIds());
                }
                List<com.talya.searchanalytics.model.AddToCartEvent> validDayCartEvents = new ArrayList<>();
                for (com.talya.searchanalytics.model.AddToCartEvent e : dayCartEvents) {
                    java.util.Set<String> products = daySessionProducts.get(e.getSessionId());
                    if (products != null && products.contains(e.getProductId())) {
                        validDayCartEvents.add(e);
                    }
                }
                int dayAddToCart = validDayCartEvents.size();

                // Build map of sessionId -> set of product IDs for this day
                java.util.Map<String, java.util.Set<String>> daySessionCartProducts = new java.util.HashMap<>();
                for (com.talya.searchanalytics.model.AddToCartEvent e : validDayCartEvents) {
                    daySessionCartProducts.computeIfAbsent(e.getSessionId(), k -> new java.util.HashSet<>())
                            .add(e.getProductId());
                }

                // Filter purchase events for this day - count each product IN THE SAME SESSION
                List<com.talya.searchanalytics.model.PurchaseEvent> dayPurchaseEvents = purchaseRepo
                        .findAllByShopIdAndTimestampMsBetween(shopId, dayStartMs, dayEndMs);
                long dayValidPurchasedProductCount = 0;

                for (com.talya.searchanalytics.model.PurchaseEvent pe : dayPurchaseEvents) {
                    if (pe.getProductIds() != null && pe.getSessionId() != null) {
                        java.util.Set<String> cartProductsInSession = daySessionCartProducts.get(pe.getSessionId());
                        if (cartProductsInSession != null) {
                            for (String productId : pe.getProductIds()) {
                                if (cartProductsInSession.contains(productId)) {
                                    dayValidPurchasedProductCount++;
                                }
                            }
                        }
                    }
                }

                int dayPurchases = (int) dayValidPurchasedProductCount;

                Double dayAddToCartAmount = validDayCartEvents.stream()
                        .mapToDouble(ev -> ev.getPrice() != null ? ev.getPrice() : 0d).sum();
                String dayCurrency = validDayCartEvents.stream()
                        .map(com.talya.searchanalytics.model.AddToCartEvent::getCurrency)
                        .filter(c -> c != null && !c.isEmpty()).findFirst().orElse(currency);
                series.add(AnalyticsSummaryResponse.TimePoint.builder()
                        .date(d.toString()).searches(daySearches).addToCart(dayAddToCart).purchases(dayPurchases)
                        .addToCartAmount(dayAddToCartAmount).currency(dayCurrency).build());
            }

            List<Object[]> rows = searchRepo.topQueries(shopId, fromMs, toMs);
            List<AnalyticsSummaryResponse.TopQuery> tq = new ArrayList<>();
            for (Object[] r : rows) {
                String term = (String) r[0];
                long cnt = (r[1] instanceof Long) ? (Long) r[1] : ((Number) r[1]).longValue();
                tq.add(new AnalyticsSummaryResponse.TopQuery(term, cnt));
            }

            return AnalyticsSummaryResponse.builder()
                    .totalSearches(searches)
                    .totalAddToCart(addToCart)
                    .totalPurchases(purchases)
                    .totalProductClicks(productClicks)
                    .totalBuyNowClicks(buyNowClicks)
                    .totalRevenue(totalRevenue)
                    .conversionRate(Math.round(conv * 10.0) / 10.0)
                    .clickThroughRate(Math.round(clickThroughRate * 10.0) / 10.0)
                    .timeSeries(series)
                    .topQueries(tq)
                    .searchesChangePercent(searchesChange)
                    .addToCartChangePercent(addToCartChange)
                    .purchasesChangePercent(purchasesChange)
                    .productClicksChangePercent(productClicksChange)
                    .buyNowClicksChangePercent(buyNowClicksChange)
                    .revenueChangePercent(revenueChange)
                    .conversionRateChangePercent(convChange)
                    .clickThroughRateChangePercent(clickThroughRateChange)
                    .totalAddToCartAmount(totalAddToCartAmount)
                    .prevAddToCartAmount(prevAddToCartAmount)
                    .addToCartAmountChangePercent(addToCartAmountChangePercent)
                    .currency(currency)
                    .build();
        } catch (Exception e) {
            log.error("Error calculating analytics summary for shopId: {}", shopId, e);

            // Return safe default response
            return AnalyticsSummaryResponse.builder()
                    .totalSearches(0L)
                    .totalAddToCart(0L)
                    .totalPurchases(0L)
                    .totalProductClicks(0L)
                    .totalBuyNowClicks(0L)
                    .totalRevenue(0.0)
                    .conversionRate(0.0)
                    .clickThroughRate(0.0)
                    .timeSeries(new ArrayList<>())
                    .topQueries(new ArrayList<>())
                    .searchesChangePercent(0.0)
                    .addToCartChangePercent(0.0)
                    .purchasesChangePercent(0.0)
                    .productClicksChangePercent(0.0)
                    .buyNowClicksChangePercent(0.0)
                    .revenueChangePercent(0.0)
                    .conversionRateChangePercent(0.0)
                    .clickThroughRateChangePercent(0.0)
                    .totalAddToCartAmount(0.0)
                    .prevAddToCartAmount(0.0)
                    .addToCartAmountChangePercent(0.0)
                    .currency("NIS")
                    .build();
        }
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
