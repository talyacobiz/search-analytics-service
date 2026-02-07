package com.talya.searchanalytics.web.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ABTestComparisonResponse {

    // Group A (e.g., Shopify search, searchGroup = 0)
    private AnalyticsSummaryResponse groupA;
    private String groupALabel;

    // Group B (e.g., AI search, searchGroup = 1)
    private AnalyticsSummaryResponse groupB;
    private String groupBLabel;

    // Percentage differences (Group B vs Group A)
    // Positive values mean Group B is better, negative means Group A is better
    private ComparisonMetrics comparison;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ComparisonMetrics {
        // Core metrics differences (B vs A)
        private Double searchesDiff; // % difference in total searches
        private Double addToCartDiff; // % difference in add-to-cart events
        private Double purchasesDiff; // % difference in purchases
        private Double productClicksDiff; // % difference in product clicks
        private Double buyNowClicksDiff; // % difference in buy-now clicks
        private Double revenueDiff; // % difference in revenue

        // Rate metrics differences (absolute difference in percentage points)
        private Double conversionRateDiff; // Difference in conversion rate (e.g., 5.2% - 3.1% = +2.1pp)
        private Double clickThroughRateDiff; // Difference in CTR
        private Double addToCartRateDiff; // Difference in add-to-cart rate

        // Session metrics differences
        private Double sessionsWithClicksDiff;
        private Double sessionsWithAddToCartsDiff;
        private Double sessionsWithPurchasesDiff;
        private Double sessionsWithSearchesDiff;

        // Cart amount differences
        private Double addToCartAmountDiff;
        private Double purchaseValueEurDiff;

        // Query complexity differences
        private Double averageWordsPerQueryDiff;
        private Double longQueryCountDiff;
        private Double longQueryPercentageDiff;

        // Winner indicator for key metrics
        private String conversionWinner; // "A", "B", or "TIE"
        private String revenueWinner; // "A", "B", or "TIE"
        private String clickThroughWinner; // "A", "B", or "TIE"
    }
}
