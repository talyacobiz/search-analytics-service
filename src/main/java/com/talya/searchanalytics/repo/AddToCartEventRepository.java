package com.talya.searchanalytics.repo;

import com.talya.searchanalytics.model.AddToCartEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AddToCartEventRepository extends JpaRepository<AddToCartEvent, Long> {
    long countByShopIdAndTimestampMsBetween(String shopId, Long from, Long to);
    List<AddToCartEvent> findAllByShopIdAndTimestampMsBetween(String shopId, Long from, Long to);
    @Query("SELECT SUM(e.price) FROM AddToCartEvent e WHERE e.shopId = :shopId AND e.timestampMs BETWEEN :from AND :to")
    Double sumPriceByShopIdAndTimestampMsBetween(@Param("shopId") String shopId, @Param("from") Long from, @Param("to") Long to); // Sum price of all add-to-cart events in the given period
}
