package com.talya.searchanalytics.service;

import com.talya.searchanalytics.repo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataMigrationService {

    private final SearchEventRepository searchRepo;
    private final AddToCartEventRepository cartRepo;
    private final ProductClickEventRepository clickRepo;
    private final BuyNowClickEventRepository buyNowRepo;
    private final PurchaseEventRepository purchaseRepo;

    /**
     * Backfill searchGroup field for all events before February 8, 2026.
     * Sets searchGroup = 1 (AI search) for all events where searchGroup is
     * currently null.
     */
    @Transactional
    public String backfillSearchGroupBeforeFeb8() {
        // February 8, 2026 at 00:00:00 UTC
        long cutoffMs = LocalDate.of(2026, 2, 8)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli();

        log.info("Starting searchGroup backfill for events before {}", cutoffMs);

        int searchEventsUpdated = 0;
        int cartEventsUpdated = 0;
        int clickEventsUpdated = 0;
        int buyNowEventsUpdated = 0;
        int purchaseEventsUpdated = 0;

        // Update SearchEvents
        var searchEvents = searchRepo.findAll();
        for (var event : searchEvents) {
            if (event.getTimestampMs() != null && event.getTimestampMs() < cutoffMs && event.getSearchGroup() == null) {
                event.setSearchGroup(1);
                searchRepo.save(event);
                searchEventsUpdated++;
            }
        }

        // Update AddToCartEvents
        var cartEvents = cartRepo.findAll();
        for (var event : cartEvents) {
            if (event.getTimestampMs() != null && event.getTimestampMs() < cutoffMs && event.getSearchGroup() == null) {
                event.setSearchGroup(1);
                cartRepo.save(event);
                cartEventsUpdated++;
            }
        }

        // Update ProductClickEvents
        var clickEvents = clickRepo.findAll();
        for (var event : clickEvents) {
            if (event.getTimestampMs() != null && event.getTimestampMs() < cutoffMs && event.getSearchGroup() == null) {
                event.setSearchGroup(1);
                clickRepo.save(event);
                clickEventsUpdated++;
            }
        }

        // Update BuyNowClickEvents
        var buyNowEvents = buyNowRepo.findAll();
        for (var event : buyNowEvents) {
            if (event.getTimestampMs() != null && event.getTimestampMs() < cutoffMs && event.getSearchGroup() == null) {
                event.setSearchGroup(1);
                buyNowRepo.save(event);
                buyNowEventsUpdated++;
            }
        }

        // Update PurchaseEvents
        var purchaseEvents = purchaseRepo.findAll();
        for (var event : purchaseEvents) {
            if (event.getTimestampMs() != null && event.getTimestampMs() < cutoffMs && event.getSearchGroup() == null) {
                event.setSearchGroup(1);
                purchaseRepo.save(event);
                purchaseEventsUpdated++;
            }
        }

        String summary = String.format(
                "Backfill completed! Updated events before Feb 8, 2026: " +
                        "SearchEvents=%d, AddToCartEvents=%d, ProductClickEvents=%d, BuyNowClickEvents=%d, PurchaseEvents=%d",
                searchEventsUpdated, cartEventsUpdated, clickEventsUpdated, buyNowEventsUpdated, purchaseEventsUpdated);

        log.info(summary);
        return summary;
    }
}
