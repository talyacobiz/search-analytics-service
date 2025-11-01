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

        List<AnalyticsSummaryResponse.TimePoint> series = new ArrayList<>();
        LocalDate start = Instant.ofEpochMilli(fromMs).atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate end = Instant.ofEpochMilli(toMs).atZone(ZoneOffset.UTC).toLocalDate();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            series.add(AnalyticsSummaryResponse.TimePoint.builder()
                    .date(d.toString()).searches(0).addToCart(0).purchases(0).build());
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
                .build();
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
