package com.talya.searchanalytics.model;

import javax.persistence.*;
import lombok.*;

@Entity
@Table(name = "buy_now_click_events", indexes = {
        @Index(name = "idx_buynow_shop_time", columnList = "shopId, timestampMs"),
        @Index(name = "idx_buynow_session", columnList = "sessionId")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BuyNowClickEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String shopId;

    @Column
    private String clientId;

    @Column
    private String sessionId;

    @Column
    private String productId;

    @Column
    private Double price;

    @Column
    private String currency;

    @Column(nullable = false)
    private Long timestampMs;
}
