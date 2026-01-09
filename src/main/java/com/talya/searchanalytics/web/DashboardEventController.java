package com.talya.searchanalytics.web;

import com.talya.searchanalytics.model.*;
import com.talya.searchanalytics.repo.*;
import com.talya.searchanalytics.web.dto.EventDTOs.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/dashboard/api/v1/events")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.OPTIONS})
@RequiredArgsConstructor
public class DashboardEventController {

    private static final Logger log = LoggerFactory.getLogger(DashboardEventController.class);

    private final SearchEventRepository searchRepo;
    private final AddToCartEventRepository cartRepo;
    private final PurchaseEventRepository purchaseRepo;
    private final ProductClickEventRepository clickRepo;
    private final BuyNowClickEventRepository buyNowRepo;

    @PostMapping("/search")
    public ResponseEntity<?> recordSearch(@RequestBody SearchEventRequest req) {
        log.info("POST /dashboard/api/v1/events/search - payload: {}", req);
        SearchEvent e = SearchEvent.builder()
                .shopId(req.getShopId())
                .searchId(req.getSearchId())
                .customerId(req.getCustomerId())
                .sessionId(req.getSessionId())
                .query(req.getQuery())
                .productIds(req.getProductIds())
                .timestampMs(req.getTimestampMs())
                .build();
        return ResponseEntity.ok(searchRepo.save(e).getId());
    }

    private boolean isValidSearchEvent(String shopId, String sessionId, String productId, String eventType) {
        if (sessionId == null || productId == null) {
            log.info("{} ignored: missing sessionId or productId", eventType);
            return false;
        }

        List<SearchEvent> searches = searchRepo.findAllByShopIdAndSessionId(shopId, sessionId);
        if (searches.isEmpty()) {
            log.info("{} ignored: no search events found for session {}", eventType, sessionId);
            return false;
        }

        log.debug("{} - Found {} search events for session {}", eventType, searches.size(), sessionId);

        // First try direct match
        for (SearchEvent search : searches) {
            if (search.getProductIds() != null && search.getProductIds().contains(productId)) {
                log.debug("{} - Direct match found: {}", eventType, productId);
                return true;
            }
        }

        // If no direct match and it's a valid Shopify product format, allow it (session-based validation)
        if (productId.startsWith("gid://shopify/Product/")) {
            log.debug("{} - Session-based validation: allowing product {} from session with {} searches",
                     eventType, productId, searches.size());
            return true;
        }

        log.info("{} ignored: productId {} not validated for session {}", eventType, productId, sessionId);
        return false;
    }

    @PostMapping("/add-to-cart")
    public ResponseEntity<?> recordAddToCart(@RequestBody AddToCartRequest req) {
        log.info("POST /dashboard/api/v1/events/add-to-cart - payload: {}", req);
        Double price = null;
        String currency = null;
        if (req.getPrice() != null) {
            String[] parts = req.getPrice().trim().split(" ");
            try {
                price = Double.parseDouble(parts[0]);
            } catch (Exception e) {
                log.warn("Failed to parse price: {}", req.getPrice());
            }
            if (parts.length > 1) {
                currency = parts[1];
            }
        }

        if (!isValidSearchEvent(req.getShopId(), req.getSessionId(), req.getProductId(), "AddToCartEvent")) {
            return ResponseEntity.noContent().build();
        }

        AddToCartEvent e = AddToCartEvent.builder()
                .shopId(req.getShopId())
                .customerId(req.getCustomerId())
                .sessionId(req.getSessionId())
                .productId(req.getProductId())
                .searchId(req.getSearchId())
                .timestampMs(req.getTimestampMs())
                .price(price)
                .currency(currency)
                .build();
        return ResponseEntity.ok(cartRepo.save(e).getId());
    }

    @PostMapping("/product-click")
    public ResponseEntity<?> recordProductClick(@RequestBody ProductClickRequest req) {
        log.info("POST /dashboard/api/v1/events/product-click - payload: {}", req);

        if (!isValidSearchEvent(req.getShopId(), req.getSessionId(), req.getProductId(), "ProductClickEvent")) {
            return ResponseEntity.noContent().build();
        }

        ProductClickEvent e = ProductClickEvent.builder()
                .shopId(req.getShopId())
                .customerId(req.getCustomerId())
                .sessionId(req.getSessionId())
                .productId(req.getProductId())
                .searchId(req.getSearchId())
                .timestampMs(req.getTimestampMs())
                .build();
        return ResponseEntity.ok(clickRepo.save(e).getId());
    }

    @PostMapping("/buy-now-click")
    public ResponseEntity<?> recordBuyNowClick(@RequestBody BuyNowClickRequest req) {
        log.info("POST /dashboard/api/v1/events/buy-now-click - payload: {}", req);

        if (!isValidSearchEvent(req.getShopId(), req.getSessionId(), req.getProductId(), "BuyNowClickEvent")) {
            return ResponseEntity.noContent().build();
        }

        BuyNowClickEvent e = BuyNowClickEvent.builder()
                .shopId(req.getShopId())
                .customerId(req.getCustomerId())
                .sessionId(req.getSessionId())
                .productId(req.getProductId())
                .timestampMs(req.getTimestampMs())
                .build();
        return ResponseEntity.ok(buyNowRepo.save(e).getId());
    }

    @PostMapping("/purchase")
    public ResponseEntity<?> recordPurchase(@RequestBody PurchaseRequest req) {
        log.info("POST /dashboard/api/v1/events/purchase - payload: {}", req);

        // Note: PurchaseRequest has productIds (List), but validation expects single productId
        // For now, we'll validate the first product if available
        String firstProductId = (req.getProductIds() != null && !req.getProductIds().isEmpty())
                ? req.getProductIds().get(0) : null;

        if (firstProductId != null && !isValidSearchEvent(req.getShopId(), req.getSessionId(), firstProductId, "PurchaseEvent")) {
            return ResponseEntity.noContent().build();
        }

        PurchaseEvent e = PurchaseEvent.builder()
                .shopId(req.getShopId())
                .customerId(req.getCustomerId())
                .sessionId(req.getSessionId())
                .productIds(req.getProductIds())
                .totalAmount(req.getTotalAmount())
                .currency(req.getCurrency())
                .timestampMs(req.getTimestampMs())
                .build();
        return ResponseEntity.ok(purchaseRepo.save(e).getId());
    }
}

