package com.talya.searchanalytics.model;

import javax.persistence.*;
import lombok.*;

@Entity
@Table(name="purchase_events", indexes = {
        @Index(name="idx_purchase_shop_time", columnList="shopId, timestampMs"),
        @Index(name="idx_purchase_session", columnList="sessionId")
})
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class PurchaseEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable=false)
    private String shopId;

    @Column
    private String customerId;

    @Column(nullable=false)
    private String sessionId;

    @ElementCollection
    @CollectionTable(name="purchase_products", joinColumns=@JoinColumn(name="purchase_event_id"))
    @Column(name="productId")
    private java.util.List<String> productIds;

    @Column(nullable=false)
    private Double totalAmount;

    @Column
    private String currency;

    @Column(nullable=false)
    private Long timestampMs;
}
