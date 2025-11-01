package com.talya.searchanalytics.repo;

import com.talya.searchanalytics.model.AddToCartEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AddToCartEventRepository extends JpaRepository<AddToCartEvent, Long> {
    long countByShopIdAndTimestampMsBetween(String shopId, Long from, Long to);
    List<AddToCartEvent> findAllByShopIdAndTimestampMsBetween(String shopId, Long from, Long to);
}
