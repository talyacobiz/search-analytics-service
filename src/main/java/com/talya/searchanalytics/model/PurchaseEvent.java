package com.talya.searchanalytics.model;

import javax.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "purchase_events",
    indexes = {
        @Index(name = "idx_purchase_shop_time", columnList = "shopId, timestampMs"),
        @Index(name = "idx_purchase_session", columnList = "sessionId"),
        @Index(name = "idx_purchase_user", columnList = "userId")
    }
)
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

    /** Shopify checkout token (legacy session - still kept for reference) */
    @Column
    private String sessionId;

    /** SearchAI user ID extracted from order note attributes */
    @Column
    private String userId;

    /** List of purchased product IDs */
    @ElementCollection
    @CollectionTable(
        name = "purchase_products",
        joinColumns = @JoinColumn(name = "purchase_event_id")
    )
    @Column(name = "productId")
    private java.util.List<String> productIds;

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

    /** Unix timestamp (ms) of purchase detection */
    @Column(nullable = false)
    private Long timestampMs;
}
