package com.talya.searchanalytics.web.dto;

import com.talya.searchanalytics.model.*;
import lombok.*;
import java.util.List;

@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AnalyticsFullResponse {
    private String shopId;
    private long fromMs;
    private long toMs;
    private List<SearchEvent> searchEvents;
    private List<ProductClickEvent> productClickEvents;
    private List<AddToCartEvent> addToCartEvents;
    private List<PurchaseEvent> purchaseEvents;
    private List<BuyNowClickEvent> buyNowClickEvents;
}
