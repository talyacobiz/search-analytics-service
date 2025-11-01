package com.talya.searchanalytics.repo;

import com.talya.searchanalytics.model.ProductClickEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProductClickEventRepository extends JpaRepository<ProductClickEvent, Long> {
    long countByShopIdAndTimestampMsBetween(String shopId, Long from, Long to);
    List<ProductClickEvent> findAllByShopIdAndTimestampMsBetween(String shopId, Long from, Long to);
}
