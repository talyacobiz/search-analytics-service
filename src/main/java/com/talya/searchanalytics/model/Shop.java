package com.talya.searchanalytics.model;

import lombok.*;
import javax.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "shops", indexes = {
        @Index(name = "idx_shop_domain", columnList = "shopDomain", unique = true)
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Shop {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String shopDomain; // username

    @Column(nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant lastLoginAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) status = Status.ACTIVE;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    public enum Role { OWNER, SHOP }
    public enum Status { ACTIVE, DISABLED }
}

