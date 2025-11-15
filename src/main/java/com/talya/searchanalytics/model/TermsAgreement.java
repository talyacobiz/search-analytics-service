package com.talya.searchanalytics.model;

import lombok.*;

import javax.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "terms_agreements", indexes = {
        @Index(name = "idx_terms_shop", columnList = "shopId", unique = true)
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TermsAgreement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String shopId;

    @Column
    private String termsVersion;

    @Column(nullable = false)
    private Instant acceptedAt;
}
