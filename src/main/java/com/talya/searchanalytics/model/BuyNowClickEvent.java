package com.talya.searchanalytics.model;

import javax.persistence.*;
import lombok.*;

@Entity
@Table(name="buy_now_click_events", indexes = {
        @Index(name="idx_buynow_shop_time", columnList="shopId, timestampMs"),
        @Index(name="idx_buynow_session", columnList="sessionId")
})
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class BuyNowClickEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable=false)
    private String shopId;

    @Column
    private String customerId;

    @Column(nullable=false)
    private String sessionId;

    @Column(nullable=false)
    private String productId;

    @Column(nullable=false)
    private Long timestampMs;
}
