package com.talya.searchanalytics.model;

import javax.persistence.*;
import lombok.*;
import java.util.List;

@Entity
@Table(name="search_events", indexes = {
        @Index(name="idx_search_shop_time", columnList="shopId, timestampMs"),
        @Index(name="idx_search_session", columnList="sessionId")
})
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class SearchEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable=false)
    private String shopId;

    @Column(nullable=false)
    private String searchId;

    @Column
    private String customerId;

    @Column(nullable=false)
    private String sessionId;

    @Column(nullable=false, length=2000)
    private String query;

    @ElementCollection
    @CollectionTable(name="search_result_products", joinColumns=@JoinColumn(name="search_event_id"))
    @Column(name="productId")
    private java.util.List<String> productIds;

    @Column(nullable=false)
    private Long timestampMs;
}
