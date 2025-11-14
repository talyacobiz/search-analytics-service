package com.talya.searchanalytics.repo;

import com.talya.searchanalytics.model.Shop;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ShopRepository extends JpaRepository<Shop, Long> {
    Optional<Shop> findByShopDomain(String shopDomain);
    boolean existsByShopDomain(String shopDomain);
    java.util.List<Shop> findByRole(Shop.Role role);
}

