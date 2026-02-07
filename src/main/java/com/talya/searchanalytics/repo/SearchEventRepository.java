package com.talya.searchanalytics.repo;

import com.talya.searchanalytics.model.SearchEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface SearchEventRepository extends JpaRepository<SearchEvent, Long> {
    long countByShopIdAndTimestampMsBetween(String shopId, Long from, Long to);

    long countByShopId(String shopId);

    List<SearchEvent> findAllByShopIdAndTimestampMsBetween(String shopId, Long from, Long to);

    List<SearchEvent> findAllByShopIdAndSessionId(String shopId, String sessionId);

    List<SearchEvent> findAllByShopIdAndSessionIdAndSearchGroup(String shopId, String sessionId, Integer searchGroup);

    @Query("select e.query as term, count(e.id) as cnt from SearchEvent e where e.shopId=:shop and e.timestampMs between :from and :to group by e.query order by cnt desc")
    List<Object[]> topQueries(@Param("shop") String shopId, @Param("from") Long from, @Param("to") Long to);

    // A/B Testing: Filter by searchGroup (ignores null searchGroup)
    long countByShopIdAndTimestampMsBetweenAndSearchGroup(String shopId, Long from, Long to, Integer searchGroup);

    List<SearchEvent> findAllByShopIdAndTimestampMsBetweenAndSearchGroup(String shopId, Long from, Long to,
            Integer searchGroup);
}
