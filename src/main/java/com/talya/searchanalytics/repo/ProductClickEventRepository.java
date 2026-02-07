package com.talya.searchanalytics.repo;

import com.talya.searchanalytics.model.ProductClickEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProductClickEventRepository extends JpaRepository<ProductClickEvent, Long> {
    long countByShopIdAndTimestampMsBetween(String shopId, Long from, Long to);

    List<ProductClickEvent> findAllByShopIdAndTimestampMsBetween(String shopId, Long from, Long to);

    // A/B Testing: Filter by searchGroup (ignores null searchGroup)
    long countByShopIdAndTimestampMsBetweenAndSearchGroup(String shopId, Long from, Long to, Integer searchGroup);

    List<ProductClickEvent> findAllByShopIdAndTimestampMsBetweenAndSearchGroup(String shopId, Long from, Long to,
            Integer searchGroup);
}
