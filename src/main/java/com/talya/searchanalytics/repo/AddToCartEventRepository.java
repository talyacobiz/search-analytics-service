package com.talya.searchanalytics.repo;

import com.talya.searchanalytics.model.AddToCartEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AddToCartEventRepository extends JpaRepository<AddToCartEvent, Long> {
    long countByShopIdAndTimestampMsBetween(String shopId, Long from, Long to);

    long countByShopId(String shopId);

    List<AddToCartEvent> findAllByShopIdAndTimestampMsBetween(String shopId, Long from, Long to);

    @Query("SELECT SUM(e.price) FROM AddToCartEvent e WHERE e.shopId = :shopId AND e.timestampMs BETWEEN :from AND :to")
    Double sumPriceByShopIdAndTimestampMsBetween(@Param("shopId") String shopId, @Param("from") Long from,
            @Param("to") Long to); // Sum price of all add-to-cart events in the given period

    // A/B Testing: Filter by searchGroup (ignores null searchGroup)
    long countByShopIdAndTimestampMsBetweenAndSearchGroup(String shopId, Long from, Long to, Integer searchGroup);

    List<AddToCartEvent> findAllByShopIdAndTimestampMsBetweenAndSearchGroup(String shopId, Long from, Long to,
            Integer searchGroup);

    @Query("SELECT SUM(e.price) FROM AddToCartEvent e WHERE e.shopId = :shopId AND e.timestampMs BETWEEN :from AND :to AND e.searchGroup = :searchGroup")
    Double sumPriceByShopIdAndTimestampMsBetweenAndSearchGroup(@Param("shopId") String shopId, @Param("from") Long from,
            @Param("to") Long to, @Param("searchGroup") Integer searchGroup);
}
