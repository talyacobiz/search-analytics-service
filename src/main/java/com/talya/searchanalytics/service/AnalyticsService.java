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
        long addToCart = cartRepo.countByShopIdAndTimestampMsBetween(shopId, fromMs, toMs);
        long purchases = purchaseRepo.countByShopIdAndTimestampMsBetween(shopId, fromMs, toMs);
        Double totalRevenue = purchaseRepo.sumTotalAmountByShopIdAndTimestampMsBetween(shopId, fromMs, toMs);
        if (totalRevenue == null) totalRevenue = 0d;
        double conv = (searches == 0) ? 0 : (purchases * 100.0 / searches);

        // Calculate previous period
        long periodMs = toMs - fromMs;
        long prevFromMs = fromMs - periodMs;
        long prevToMs = fromMs;
        long prevSearches = searchRepo.countByShopIdAndTimestampMsBetween(shopId, prevFromMs, prevToMs);
        long prevAddToCart = cartRepo.countByShopIdAndTimestampMsBetween(shopId, prevFromMs, prevToMs);
        long prevPurchases = purchaseRepo.countByShopIdAndTimestampMsBetween(shopId, prevFromMs, prevToMs);
        Double prevRevenue = purchaseRepo.sumTotalAmountByShopIdAndTimestampMsBetween(shopId, prevFromMs, prevToMs);
        if (prevRevenue == null) prevRevenue = 0d;
        double prevConv = (prevSearches == 0) ? 0 : (prevPurchases * 100.0 / prevSearches);

        // Calculate percentage change
        Double searchesChange = percentChange(prevSearches, searches);
        Double addToCartChange = percentChange(prevAddToCart, addToCart);
        Double purchasesChange = percentChange(prevPurchases, purchases);
        Double revenueChange = percentChange(prevRevenue, totalRevenue);
        Double convChange = percentChange(prevConv, conv);

        // Aggregate time series data
        List<AnalyticsSummaryResponse.TimePoint> series = new ArrayList<>();
        LocalDate start = Instant.ofEpochMilli(fromMs).atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate end = Instant.ofEpochMilli(toMs).atZone(ZoneOffset.UTC).toLocalDate();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            long dayStartMs = d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
            long dayEndMs = d.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() - 1;
            int daySearches = (int) searchRepo.countByShopIdAndTimestampMsBetween(shopId, dayStartMs, dayEndMs);
            int dayAddToCart = (int) cartRepo.countByShopIdAndTimestampMsBetween(shopId, dayStartMs, dayEndMs);
            int dayPurchases = (int) purchaseRepo.countByShopIdAndTimestampMsBetween(shopId, dayStartMs, dayEndMs);
            series.add(AnalyticsSummaryResponse.TimePoint.builder()
                    .date(d.toString()).searches(daySearches).addToCart(dayAddToCart).purchases(dayPurchases).build());
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
                .totalRevenue(totalRevenue)
                .conversionRate(Math.round(conv * 10.0)/10.0)
                .timeSeries(series)
                .topQueries(tq)
                .searchesChangePercent(searchesChange)
                .addToCartChangePercent(addToCartChange)
                .purchasesChangePercent(purchasesChange)
                .revenueChangePercent(revenueChange)
                .conversionRateChangePercent(convChange)
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
}
