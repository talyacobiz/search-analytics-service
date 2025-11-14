package com.talya.searchanalytics.repo;

import com.talya.searchanalytics.model.PurchaseEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface PurchaseEventRepository extends JpaRepository<PurchaseEvent, Long> {
    long countByShopIdAndTimestampMsBetween(String shopId, Long from, Long to);

    @Query("select coalesce(sum(p.totalAmount), 0) from PurchaseEvent p where p.shopId=:shop and p.timestampMs between :from and :to")
    Double sumTotalAmountByShopIdAndTimestampMsBetween(@Param("shop") String shopId, @Param("from") Long from, @Param("to") Long to);

    List<PurchaseEvent> findAllByShopIdAndTimestampMsBetween(String shopId, Long from, Long to);
    long countByShopId(String shopId);
}
