package com.talya.searchanalytics.repo;

import com.talya.searchanalytics.model.BuyNowClickEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BuyNowClickEventRepository extends JpaRepository<BuyNowClickEvent, Long> {
    long countByShopIdAndTimestampMsBetween(String shopId, Long from, Long to);

    List<BuyNowClickEvent> findAllByShopIdAndTimestampMsBetween(String shopId, Long from, Long to);

    // A/B Testing: Filter by searchGroup (ignores null searchGroup)
    long countByShopIdAndTimestampMsBetweenAndSearchGroup(String shopId, Long from, Long to, Integer searchGroup);

    List<BuyNowClickEvent> findAllByShopIdAndTimestampMsBetweenAndSearchGroup(String shopId, Long from, Long to,
            Integer searchGroup);
}
