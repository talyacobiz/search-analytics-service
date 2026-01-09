// filepath: c:\Users\taly\search-analytics-service\src\main\java\com\talya\searchanalytics\web\dto\AnalyticsSummaryResponse.java
package com.talya.searchanalytics.web.dto;

import lombok.*;
import java.util.List;
import java.util.Map;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AnalyticsSummaryResponse {
    private long totalSearches;
    private long totalAddToCart;
    private long totalPurchases;
    private long totalProductClicks;
    private long totalBuyNowClicks;
    private Double totalRevenue;
    private double conversionRate;
    private double clickThroughRate;
    private List<TimePoint> timeSeries;
    private List<TopQuery> topQueries;
    private Double searchesChangePercent;
    private Double addToCartChangePercent;
    private Double purchasesChangePercent;
    private Double productClicksChangePercent;
    private Double buyNowClicksChangePercent;
    private Double revenueChangePercent;
    private Double conversionRateChangePercent;
    private Double clickThroughRateChangePercent;
    private Double totalAddToCartAmount;
    private Double prevAddToCartAmount;
    private Double addToCartAmountChangePercent;
    private String currency;
    
    // Purchase value fields
    private Double totalPurchaseValueEur;
    private Double purchaseValueChangePercent;
    private Map<String, Double> conversionRatesUsed;

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class TimePoint {
        private String date;
        private int searches;
        private int addToCart;
        private int purchases;
        private Double addToCartAmount;
        private String currency;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class TopQuery {
        private String term;
        private long count;
    }
}
