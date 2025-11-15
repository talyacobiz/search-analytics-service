package com.talya.searchanalytics.service;

import com.talya.searchanalytics.repo.*;
import com.talya.searchanalytics.web.dto.AnalyticsFullResponse;
import com.talya.searchanalytics.web.dto.AnalyticsSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final SearchEventRepository searchRepo;
    private final AddToCartEventRepository cartRepo;
    private final PurchaseEventRepository purchaseRepo;
    private final ProductClickEventRepository clickRepo;
    private final BuyNowClickEventRepository buyNowRepo;

    public AnalyticsSummaryResponse summary(String shopId, long fromMs, long toMs) {
        long searches = searchRepo.countByShopIdAndTimestampMsBetween(shopId, fromMs, toMs);
        long purchases = purchaseRepo.countByShopIdAndTimestampMsBetween(shopId, fromMs, toMs);
        Double totalRevenue = purchaseRepo.sumTotalAmountByShopIdAndTimestampMsBetween(shopId, fromMs, toMs);
        if (totalRevenue == null) totalRevenue = 0d;
        double conv = (searches == 0) ? 0 : (purchases * 100.0 / searches);

        // Calculate previous period
        long periodMs = toMs - fromMs;
        long prevFromMs = fromMs - periodMs;
        long prevToMs = fromMs;
        long prevSearches = searchRepo.countByShopIdAndTimestampMsBetween(shopId, prevFromMs, prevToMs);
        long prevPurchases = purchaseRepo.countByShopIdAndTimestampMsBetween(shopId, prevFromMs, prevToMs);
        Double prevRevenue = purchaseRepo.sumTotalAmountByShopIdAndTimestampMsBetween(shopId, prevFromMs, prevToMs);
        if (prevRevenue == null) prevRevenue = 0d;
        double prevConv = (prevSearches == 0) ? 0 : (prevPurchases * 100.0 / prevSearches);

        // Get all relevant events
        List<com.talya.searchanalytics.model.AddToCartEvent> cartEvents = cartRepo.findAllByShopIdAndTimestampMsBetween(shopId, fromMs, toMs);
        List<com.talya.searchanalytics.model.ProductClickEvent> clickEvents = clickRepo.findAllByShopIdAndTimestampMsBetween(shopId, fromMs, toMs);
        List<com.talya.searchanalytics.model.BuyNowClickEvent> buyNowEvents = buyNowRepo.findAllByShopIdAndTimestampMsBetween(shopId, fromMs, toMs);
        List<com.talya.searchanalytics.model.SearchEvent> searchEvents = searchRepo.findAllByShopIdAndTimestampMsBetween(shopId, fromMs, toMs);
        List<com.talya.searchanalytics.model.AddToCartEvent> prevCartEvents = cartRepo.findAllByShopIdAndTimestampMsBetween(shopId, prevFromMs, prevToMs);
        List<com.talya.searchanalytics.model.ProductClickEvent> prevClickEvents = clickRepo.findAllByShopIdAndTimestampMsBetween(shopId, prevFromMs, prevToMs);
        List<com.talya.searchanalytics.model.BuyNowClickEvent> prevBuyNowEvents = buyNowRepo.findAllByShopIdAndTimestampMsBetween(shopId, prevFromMs, prevToMs);
        List<com.talya.searchanalytics.model.SearchEvent> prevSearchEvents = searchRepo.findAllByShopIdAndTimestampMsBetween(shopId, prevFromMs, prevToMs);

        // Helper: sessionId -> list of productIds from searches
        java.util.Map<String, java.util.Set<String>> sessionProducts = new java.util.HashMap<>();
        for (com.talya.searchanalytics.model.SearchEvent se : searchEvents) {
            sessionProducts.computeIfAbsent(se.getSessionId(), k -> new java.util.HashSet<>()).addAll(se.getProductIds());
        }
        java.util.Map<String, java.util.Set<String>> prevSessionProducts = new java.util.HashMap<>();
        for (com.talya.searchanalytics.model.SearchEvent se : prevSearchEvents) {
            prevSessionProducts.computeIfAbsent(se.getSessionId(), k -> new java.util.HashSet<>()).addAll(se.getProductIds());
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
        Double totalAddToCartAmount = validCartEvents.stream().mapToDouble(ev -> ev.getPrice() != null ? ev.getPrice() : 0d).sum();
        String currency = validCartEvents.stream().map(com.talya.searchanalytics.model.AddToCartEvent::getCurrency).filter(c -> c != null && !c.isEmpty()).findFirst().orElse("NIS");

        // Filter product click events for current period
        List<com.talya.searchanalytics.model.ProductClickEvent> validClickEvents = new ArrayList<>();
        for (com.talya.searchanalytics.model.ProductClickEvent e : clickEvents) {
            java.util.Set<String> products = sessionProducts.get(e.getSessionId());
            if (products != null && (e.getProductId() == null || products.contains(e.getProductId()))) {
                validClickEvents.add(e);
            }
        }
        long productClicks = validClickEvents.size();

        // Filter buy now events for current period
        List<com.talya.searchanalytics.model.BuyNowClickEvent> validBuyNowEvents = new ArrayList<>();
        for (com.talya.searchanalytics.model.BuyNowClickEvent e : buyNowEvents) {
            java.util.Set<String> products = sessionProducts.get(e.getSessionId());
            if (products != null && (e.getProductId() == null || products.contains(e.getProductId()))) {
                validBuyNowEvents.add(e);
            }
        }
        long buyNowClicks = validBuyNowEvents.size();

        // Calculate click-through rate
        double clickThroughRate = (searches == 0) ? 0 : (productClicks * 100.0 / searches);

        // Filter add-to-cart events for previous period
        List<com.talya.searchanalytics.model.AddToCartEvent> validPrevCartEvents = new ArrayList<>();
        for (com.talya.searchanalytics.model.AddToCartEvent e : prevCartEvents) {
            java.util.Set<String> products = prevSessionProducts.get(e.getSessionId());
            if (products != null && products.contains(e.getProductId())) {
                validPrevCartEvents.add(e);
            }
        }

        // Filter previous period events
        List<com.talya.searchanalytics.model.ProductClickEvent> validPrevClickEvents = new ArrayList<>();
        for (com.talya.searchanalytics.model.ProductClickEvent e : prevClickEvents) {
            java.util.Set<String> products = prevSessionProducts.get(e.getSessionId());
            if (products != null && (e.getProductId() == null || products.contains(e.getProductId()))) {
                validPrevClickEvents.add(e);
            }
        }
        long prevProductClicks = validPrevClickEvents.size();

        List<com.talya.searchanalytics.model.BuyNowClickEvent> validPrevBuyNowEvents = new ArrayList<>();
        for (com.talya.searchanalytics.model.BuyNowClickEvent e : prevBuyNowEvents) {
            java.util.Set<String> products = prevSessionProducts.get(e.getSessionId());
            if (products != null && (e.getProductId() == null || products.contains(e.getProductId()))) {
                validPrevBuyNowEvents.add(e);
            }
        }
        long prevBuyNowClicks = validPrevBuyNowEvents.size();
        double prevClickThroughRate = (prevSearches == 0) ? 0 : (prevProductClicks * 100.0 / prevSearches);

        Double prevAddToCartAmount = validPrevCartEvents.stream().mapToDouble(ev -> ev.getPrice() != null ? ev.getPrice() : 0d).sum();
        if (totalAddToCartAmount == null) totalAddToCartAmount = 0d;
        if (prevAddToCartAmount == null) prevAddToCartAmount = 0d;
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
            List<com.talya.searchanalytics.model.AddToCartEvent> dayCartEvents = cartRepo.findAllByShopIdAndTimestampMsBetween(shopId, dayStartMs, dayEndMs);
            List<com.talya.searchanalytics.model.SearchEvent> daySearchEvents = searchRepo.findAllByShopIdAndTimestampMsBetween(shopId, dayStartMs, dayEndMs);
            java.util.Map<String, java.util.Set<String>> daySessionProducts = new java.util.HashMap<>();
            for (com.talya.searchanalytics.model.SearchEvent se : daySearchEvents) {
                daySessionProducts.computeIfAbsent(se.getSessionId(), k -> new java.util.HashSet<>()).addAll(se.getProductIds());
            }
            List<com.talya.searchanalytics.model.AddToCartEvent> validDayCartEvents = new ArrayList<>();
            for (com.talya.searchanalytics.model.AddToCartEvent e : dayCartEvents) {
                java.util.Set<String> products = daySessionProducts.get(e.getSessionId());
                if (products != null && products.contains(e.getProductId())) {
                    validDayCartEvents.add(e);
                }
            }
            int dayAddToCart = validDayCartEvents.size();
            int dayPurchases = (int) purchaseRepo.countByShopIdAndTimestampMsBetween(shopId, dayStartMs, dayEndMs);
            Double dayAddToCartAmount = validDayCartEvents.stream().mapToDouble(ev -> ev.getPrice() != null ? ev.getPrice() : 0d).sum();
            String dayCurrency = validDayCartEvents.stream().map(com.talya.searchanalytics.model.AddToCartEvent::getCurrency).filter(c -> c != null && !c.isEmpty()).findFirst().orElse(currency);
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
                .conversionRate(Math.round(conv * 10.0)/10.0)
                .clickThroughRate(Math.round(clickThroughRate * 10.0)/10.0)
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
    }

    private Double percentChange(double prev, double curr) {
        if (prev == 0 && curr == 0) return 0.0;
        if (prev == 0) return 100.0;
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
            List<com.talya.searchanalytics.model.SearchEvent> searchEvents = searchRepo.findAllByShopIdAndSessionId(shopId, sessionId);
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
        buyNow.setTimestampMs(event.get("timestamp") != null ? ((Number) event.get("timestamp")).longValue() : System.currentTimeMillis());
        buyNowRepo.save(buyNow);
    }

    public void recordProductClick(String shopId, java.util.Map<String, Object> event) {
        String sessionId = (String) event.get("session_id");
        String productId = (String) event.get("product_id");
        
        // Validate that this event came from a search session
        if (sessionId != null) {
            List<com.talya.searchanalytics.model.SearchEvent> searchEvents = searchRepo.findAllByShopIdAndSessionId(shopId, sessionId);
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
        click.setTimestampMs(event.get("timestamp") != null ? ((Number) event.get("timestamp")).longValue() : System.currentTimeMillis());
        clickRepo.save(click);
    }
}
