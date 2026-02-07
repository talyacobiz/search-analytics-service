package com.talya.searchanalytics.web.dto;

import lombok.*;
import java.util.List;

public class EventDTOs {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SearchEventRequest {
        private String shopId;
        private String searchId;
        private String clientId;
        private String sessionId;
        private String query;
        private List<String> productIds;
        private Integer searchGroup;
        private Long timestampMs;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AddToCartRequest {
        private String shopId;
        private String clientId;
        private String sessionId;
        private String productId;
        private String searchId;
        private Integer searchGroup;
        private Long timestampMs;
        private String price; // changed from Double to String
        private String currency; // new field for currency as text
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PurchaseRequest {
        private String shopId;
        private String clientId;
        private String sessionId;
        private List<String> productIds;
        private Double totalAmount;
        private String currency;
        private Integer searchGroup;
        private Long timestampMs;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProductClickRequest {
        private String shopId;
        private String clientId;
        private String sessionId;
        private String productId;
        private String searchId;
        private Integer searchGroup;
        private Long timestampMs;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BuyNowClickRequest {
        private String shopId;
        private String clientId;
        private String sessionId;
        private String productId;
        private Integer searchGroup;
        private Long timestampMs;
    }
}
