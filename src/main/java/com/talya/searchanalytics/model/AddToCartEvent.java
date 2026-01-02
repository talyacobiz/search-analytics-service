package com.talya.searchanalytics.model;

import javax.persistence.*;
import lombok.*;

@Entity
@Table(name = "add_to_cart_events", indexes = {
        @Index(name = "idx_cart_shop_time", columnList = "shopId, timestampMs"),
        @Index(name = "idx_cart_session", columnList = "sessionId")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddToCartEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String shopId;

    @Column
    private String clientId;

    @Column(nullable = false)
    private String sessionId;

    @Column(nullable = false)
    private String productId;

    @Column
    private String searchId;

    @Column(nullable = false)
    private Long timestampMs;

    private Double price;

    private String currency;
}
