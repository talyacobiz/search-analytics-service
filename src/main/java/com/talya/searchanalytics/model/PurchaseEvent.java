package com.talya.searchanalytics.model;

import javax.persistence.*;
import lombok.*;

@Entity
@Table(name = "purchase_events", indexes = {
        @Index(name = "idx_purchase_shop_time", columnList = "shopId, timestampMs"),
        @Index(name = "idx_purchase_session", columnList = "sessionId")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Shopify store domain */
    @Column(nullable = false)
    private String shopId;

    @Column
    private String clientId;

    @Column
    private String sessionId;

    /** List of purchased products */
    @ElementCollection
    @CollectionTable(name = "purchase_products", joinColumns = @JoinColumn(name = "purchase_event_id"))
    private java.util.List<Product> products;

    /** Total order value in currency */
    @Column(nullable = false)
    private Double totalAmount;

    /** ISO currency code (ILS, USD, etc.) */
    @Column
    private String currency;

    /** Comma-separated product titles */
    @Column
    private String productTitles;

    /** Shopify financial status (paid, refunded, etc.) */
    @Column
    private String orderStatus;

    /** A/B test search group: 0 = Shopify search, 1 = AI search */
    @Column
    private Integer searchGroup;

    /** Unix timestamp (ms) of purchase detection */
    @Column(nullable = false)
    private Long timestampMs;
}
