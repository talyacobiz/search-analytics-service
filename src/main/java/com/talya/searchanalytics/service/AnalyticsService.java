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
            
            log.info("STEP 1 - Add-to-Cart Filtering: {} valid out of {} total events, total amount: {} {}", 
                    addToCart, cartEvents.size(), String.format("%.2f", totalAddToCartAmount), currency);

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
            
            // Calculate purchase value in EUR with currency conversion (reuse existing filtering)
            Map<String, Double> conversionRatesUsed = new HashMap<>();
            double totalPurchaseValueEur = 0.0;

            log.info(
                    "Purchase Filtering Debug for shopId {}: {} total purchase events, {} sessions with cart events",
                    shopId, purchaseEvents.size(), sessionCartProducts.size());

            for (com.talya.searchanalytics.model.PurchaseEvent pe : purchaseEvents) {
                if (pe.getProducts() != null && pe.getSessionId() != null) {

                    // Get the cart products for THIS session
                    java.util.Set<String> cartProductsInSession = sessionCartProducts.get(pe.getSessionId());

                    // Count valid products and check if we have product prices
                    int validProductsInThisOrder = 0;
                    boolean hasProductPrices = false;
                    
                    for (Product product : pe.getProducts()) {
                        boolean inCartInSameSession = cartProductsInSession != null &&
                                cartProductsInSession.contains(product.getProductId());

                        if (inCartInSameSession) {
                            validPurchasedProductCount++;
                            validProductsInThisOrder++;
                            
                            if (product.getPrice() != null) {
                                hasProductPrices = true;
                                double productTotal = product.getPrice() * (product.getAmount() != null ? product.getAmount() : 1);
                                
                                if (pe.getCurrency() != null) {
                                    String purchaseCurrency = pe.getCurrency().toUpperCase();
                                    double rate = currencyService.getExchangeRate(purchaseCurrency);
                                    conversionRatesUsed.put(purchaseCurrency, rate);
                                    double eurAmount = currencyService.convertToEur(productTotal, purchaseCurrency);
                                    totalPurchaseValueEur += eurAmount;
                                    validPurchaseRevenue += eurAmount;
                                }
                            }
                        }
                    }
                    
                    // Fallback: if no product prices available, use proportional amount from totalAmount
                    if (validProductsInThisOrder > 0 && !hasProductPrices && pe.getTotalAmount() != null) {
                        double proportionalAmount = (pe.getTotalAmount() * validProductsInThisOrder) / pe.getProducts().size();
                        
                        if (pe.getCurrency() != null) {
                            String purchaseCurrency = pe.getCurrency().toUpperCase();
                            double rate = currencyService.getExchangeRate(purchaseCurrency);
                            conversionRatesUsed.put(purchaseCurrency, rate);
                            double eurAmount = currencyService.convertToEur(proportionalAmount, purchaseCurrency);
                            totalPurchaseValueEur += eurAmount;
                            validPurchaseRevenue += eurAmount;
                        }
                    }
                }
            }

            // Use filtered purchase data
            long purchases = validPurchasedProductCount;
            Double totalRevenue = validPurchaseRevenue;
            
            log.info("STEP 2 - Purchase Processing Complete: {} valid products from {} orders, EUR revenue: {}, fallback used for historical data", 
                    purchases, purchaseEvents.size(), String.format("%.2f", totalPurchaseValueEur));
            
            log.info("Total purchase value in EUR: {} (from {} valid products)", totalPurchaseValueEur, validPurchasedProductCount);
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
                if (pe.getProducts() != null && pe.getSessionId() != null) {
                    // Check if this session had searches
                    if (!totalSearchSessions.contains(pe.getSessionId())) {
                        continue; // Skip purchases from sessions without searches
                    }

                    // Check if any purchased product was in the cart for THIS session
                    java.util.Set<String> cartProductsInSession = sessionCartProducts.get(pe.getSessionId());
                    boolean hasValidProduct = false;

                    if (cartProductsInSession != null) {
                        for (Product product : pe.getProducts()) {
                            if (cartProductsInSession.contains(product.getProductId())) {
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
            
            log.info("STEP 3 - Conversion Rate Calculated: {}/{} sessions converted = {:.2f}%",
                    sessionsWithPurchases.size(), totalSearchSessions.size(), conv);

            // Filter product click events for current period
            List<com.talya.searchanalytics.model.ProductClickEvent> validClickEvents = new ArrayList<>();
            log.info("Processing {} product click events for shopId: {}", clickEvents.size(), shopId);
            for (com.talya.searchanalytics.model.ProductClickEvent e : clickEvents) {
                java.util.Set<String> products = sessionProducts.get(e.getSessionId());

                // Session-based validation: if session has search results and product ID is
                // valid format, allow it
                if (products != null && e.getProductId() != null
                        && e.getProductId().startsWith("gid://shopify/Product/")) {
                    validClickEvents.add(e);
                }
            }
            long productClicks = validClickEvents.size();
            log.info("STEP 4 - Click Events Processed: {} valid out of {} total clicks", productClicks, clickEvents.size());

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
            log.info("STEP 5 - Buy Now Events Processed: {} valid out of {} total buy now clicks", buyNowClicks, buyNowEvents.size());

            // Calculate click-through rate using sessions that actually had clicks (Shopify approach)
            // Reuse totalSearchSessions already defined above for conversion rate

            // Find sessions that had clicks
            java.util.Set<String> sessionsWithClicks = new java.util.HashSet<>();
            log.info("Calculating CTR: total sessions={}, valid clicks={}", totalSearchSessions.size(), validClickEvents.size());

            for (com.talya.searchanalytics.model.ProductClickEvent e : validClickEvents) {
                if (e.getSessionId() != null) {
                    sessionsWithClicks.add(e.getSessionId());
                }
            }

            log.info("Unique sessions with clicks: {}", sessionsWithClicks.size());
            double clickThroughRate = (totalSearchSessions.size() == 0) ? 0 : (sessionsWithClicks.size() * 100.0 / totalSearchSessions.size());
            
            log.info("STEP 6 - Click-Through Rate Calculated: {}/{} sessions with clicks = {:.2f}%", 
                    sessionsWithClicks.size(), totalSearchSessions.size(), clickThroughRate);

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
                if (pe.getProducts() != null && pe.getSessionId() != null) {
                    // Get the cart products for THIS session
                    java.util.Set<String> cartProductsInSession = prevSessionCartProducts.get(pe.getSessionId());

                    if (cartProductsInSession != null) {
                        for (Product product : pe.getProducts()) {
                            if (cartProductsInSession.contains(product.getProductId())) {
                                validPrevPurchasedProductCount++;
                                
                                if (product.getPrice() != null) {
                                    double productTotal = product.getPrice() * (product.getAmount() != null ? product.getAmount() : 1);
                                    validPrevPurchaseRevenue += productTotal;
                                }
                            }
                        }
                    }
                }
            }

            long prevPurchases = validPrevPurchasedProductCount;
            Double prevRevenue = validPrevPurchaseRevenue;
            // Calculate previous period purchase value in EUR (only from search sessions)
            double prevPurchaseValueEur = 0.0;

            // Use the same filtering logic as validPrevPurchaseRevenue
            for (com.talya.searchanalytics.model.PurchaseEvent pe : prevPurchaseEvents) {
                if (pe.getProducts() != null && pe.getSessionId() != null && pe.getCurrency() != null) {
                    // Get the cart products for THIS session
                    java.util.Set<String> cartProductsInSession = prevSessionCartProducts.get(pe.getSessionId());
                    
                    if (cartProductsInSession != null) {
                        for (Product product : pe.getProducts()) {
                            if (cartProductsInSession.contains(product.getProductId()) && product.getPrice() != null) {
                                double productTotal = product.getPrice() * (product.getAmount() != null ? product.getAmount() : 1);
                                String purchaseCurrency = pe.getCurrency().toUpperCase();
                                double rate = currencyService.getExchangeRate(purchaseCurrency);
                                conversionRatesUsed.put(purchaseCurrency, rate);
                                prevPurchaseValueEur += currencyService.convertToEur(productTotal, purchaseCurrency);
                            }
                        }
                    }
                }
            }

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
                if (pe.getProducts() != null && pe.getSessionId() != null) {
                    // Check if this session had searches
                    if (!prevTotalSearchSessions.contains(pe.getSessionId())) {
                        continue; // Skip purchases from sessions without searches
                    }

                    // Check if any purchased product was in the cart for THIS session
                    java.util.Set<String> cartProductsInSession = prevSessionCartProducts.get(pe.getSessionId());
                    boolean hasValidProduct = false;

                    if (cartProductsInSession != null) {
                        for (Product product : pe.getProducts()) {
                            if (cartProductsInSession.contains(product.getProductId())) {
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

            // Calculate previous period click-through rate using same session-based logic
            // Reuse prevTotalSearchSessions already defined above for conversion rate

            java.util.Set<String> prevSessionsWithClicks = new java.util.HashSet<>();

            for (com.talya.searchanalytics.model.ProductClickEvent e : validPrevClickEvents) {
                if (e.getSessionId() != null) {
                    prevSessionsWithClicks.add(e.getSessionId());
                }
            }
            double prevClickThroughRate = (prevTotalSearchSessions.size() == 0) ? 0
                    : (prevSessionsWithClicks.size() * 100.0 / prevTotalSearchSessions.size());

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
            Double purchaseValueChangePercent = percentChange(prevPurchaseValueEur, totalPurchaseValueEur);
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
                    if (pe.getProducts() != null && pe.getSessionId() != null) {
                        java.util.Set<String> cartProductsInSession = daySessionCartProducts.get(pe.getSessionId());
                        if (cartProductsInSession != null) {
                            for (Product product : pe.getProducts()) {
                                if (cartProductsInSession.contains(product.getProductId())) {
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
                    .totalPurchaseValueEur(totalPurchaseValueEur)
                    .purchaseValueChangePercent(purchaseValueChangePercent)
                    .conversionRatesUsed(conversionRatesUsed)
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
                    .totalPurchaseValueEur(0.0)
                    .purchaseValueChangePercent(0.0)
                    .conversionRatesUsed(new HashMap<>())
                    .build();
        }
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
        java.util.Set<String> totalSearchSessions = new java.util.HashSet<>();
        java.util.Set<String> sessionsWithPurchases = new java.util.HashSet<>();
        java.util.Set<String> sessionsWithClicks = new java.util.HashSet<>();
    }

    private PeriodAnalytics calculatePeriodAnalytics(String shopId, long fromMs, long toMs, Map<String, Double> conversionRatesUsed) {
        PeriodAnalytics analytics = new PeriodAnalytics();
        
        // Get all events for this period
        List<com.talya.searchanalytics.model.SearchEvent> searchEvents = searchRepo.findAllByShopIdAndTimestampMsBetween(shopId, fromMs, toMs);
        List<com.talya.searchanalytics.model.AddToCartEvent> cartEvents = cartRepo.findAllByShopIdAndTimestampMsBetween(shopId, fromMs, toMs);
        List<com.talya.searchanalytics.model.ProductClickEvent> clickEvents = clickRepo.findAllByShopIdAndTimestampMsBetween(shopId, fromMs, toMs);
        List<com.talya.searchanalytics.model.BuyNowClickEvent> buyNowEvents = buyNowRepo.findAllByShopIdAndTimestampMsBetween(shopId, fromMs, toMs);
        List<com.talya.searchanalytics.model.PurchaseEvent> purchaseEvents = purchaseRepo.findAllByShopIdAndTimestampMsBetween(shopId, fromMs, toMs);
        
        // Build session products map
        java.util.Map<String, java.util.Set<String>> sessionProducts = new java.util.HashMap<>();
        for (com.talya.searchanalytics.model.SearchEvent se : searchEvents) {
            sessionProducts.computeIfAbsent(se.getSessionId(), k -> new java.util.HashSet<>()).addAll(se.getProductIds());
            if (se.getSessionId() != null) {
                analytics.totalSearchSessions.add(se.getSessionId());
            }
        }
        
        // Filter and count add-to-cart events
        List<com.talya.searchanalytics.model.AddToCartEvent> validCartEvents = new ArrayList<>();
        for (com.talya.searchanalytics.model.AddToCartEvent e : cartEvents) {
            java.util.Set<String> products = sessionProducts.get(e.getSessionId());
            if (products != null && products.contains(e.getProductId())) {
                validCartEvents.add(e);
            }
        }
        analytics.addToCartCount = validCartEvents.size();
        analytics.addToCartAmount = validCartEvents.stream().mapToDouble(ev -> ev.getPrice() != null ? ev.getPrice() : 0d).sum();
        
        // Build session cart products map
        java.util.Map<String, java.util.Set<String>> sessionCartProducts = new java.util.HashMap<>();
        for (com.talya.searchanalytics.model.AddToCartEvent e : validCartEvents) {
            sessionCartProducts.computeIfAbsent(e.getSessionId(), k -> new java.util.HashSet<>()).add(e.getProductId());
        }
        
        // Filter and calculate purchase metrics
        for (com.talya.searchanalytics.model.PurchaseEvent pe : purchaseEvents) {
            if (pe.getProducts() != null && pe.getSessionId() != null) {
                java.util.Set<String> cartProductsInSession = sessionCartProducts.get(pe.getSessionId());
                
                for (Product product : pe.getProducts()) {
                    if (cartProductsInSession != null && cartProductsInSession.contains(product.getProductId())) {
                        analytics.validPurchasedProductCount++;
                        
                        if (product.getPrice() != null) {
                            double productTotal = product.getPrice() * (product.getAmount() != null ? product.getAmount() : 1);
                            analytics.validPurchaseRevenue += productTotal;
                            
                            // Add EUR conversion
                            if (pe.getCurrency() != null) {
                                String purchaseCurrency = pe.getCurrency().toUpperCase();
                                double rate = currencyService.getExchangeRate(purchaseCurrency);
                                conversionRatesUsed.put(purchaseCurrency, rate);
                                analytics.totalPurchaseValueEur += currencyService.convertToEur(productTotal, purchaseCurrency);
                            }
                        }
                    }
                }
                
                // Track sessions with purchases if any product was valid
                boolean hasValidProduct = false;
                if (cartProductsInSession != null) {
                    for (Product product : pe.getProducts()) {
                        if (cartProductsInSession.contains(product.getProductId())) {
                            hasValidProduct = true;
                            break;
                        }
                    }
                }
                if (hasValidProduct && analytics.totalSearchSessions.contains(pe.getSessionId())) {
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
        
        return analytics;
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
