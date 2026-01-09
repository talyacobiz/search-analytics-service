package com.talya.searchanalytics.web;

import com.talya.searchanalytics.model.*;
import com.talya.searchanalytics.repo.*;
import com.talya.searchanalytics.web.dto.EventDTOs.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.util.List;

@RestController
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class LegacyEventController {

    private static final Logger log = LoggerFactory.getLogger(LegacyEventController.class);

    private final SearchEventRepository searchRepo;
    private final AddToCartEventRepository cartRepo;
    private final PurchaseEventRepository purchaseRepo;
    private final ProductClickEventRepository clickRepo;
    private final BuyNowClickEventRepository buyNowRepo;

    @PostMapping("/dashboard/search")
    public ResponseEntity<?> recordSearch(@RequestBody SearchEventRequest req) {
        log.info("POST /dashboard/search - payload: {}", req);
        SearchEvent e = SearchEvent.builder()
                .shopId(req.getShopId())
                .searchId(req.getSearchId())
                .clientId(req.getClientId())
                .sessionId(req.getSessionId())
                .query(req.getQuery())
                .productIds(req.getProductIds())
                .timestampMs(req.getTimestampMs())
                .build();
        return ResponseEntity.ok(searchRepo.save(e).getId());
    }

    @RequestMapping(value = "/dashboard/search", method = RequestMethod.OPTIONS)
    public void corsHeadersSearch(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");
        response.setStatus(HttpServletResponse.SC_OK);
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

        // If no direct match and it's a valid Shopify product format, allow it
        // (session-based validation)
        if (productId.startsWith("gid://shopify/Product/")) {
            log.debug("{} - Session-based validation: allowing product {} from session with {} searches",
                    eventType, productId, searches.size());
            return true;
        }

        log.info("{} ignored: productId {} not validated for session {}", eventType, productId, sessionId);
        return false;
    }

    @PostMapping("/dashboard/add-to-cart")
    public ResponseEntity<?> recordAddToCart(@RequestBody AddToCartRequest req) {
        log.info("POST /dashboard/add-to-cart - payload: {}", req);
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
                .clientId(req.getClientId())
                .sessionId(req.getSessionId())
                .productId(req.getProductId())
                .searchId(req.getSearchId())
                .timestampMs(req.getTimestampMs())
                .price(price)
                .currency(currency)
                .build();
        return ResponseEntity.ok(cartRepo.save(e).getId());
    }

    @RequestMapping(value = "/dashboard/add-to-cart", method = RequestMethod.OPTIONS)
    public void corsHeadersAddToCart(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");
        response.setStatus(HttpServletResponse.SC_OK);
    }

    @PostMapping("/dashboard/buy-now-click")
    public ResponseEntity<?> buyNowClick(@RequestBody java.util.Map<String, Object> event) {
        String shopId = (String) event.get("shopId");
        String sessionId = (String) event.get("sessionId");
        String productId = (String) event.get("productId");
        if (shopId == null)
            return error(HttpStatus.BAD_REQUEST, "MISSING_SHOP_ID");

        log.info("POST /dashboard/buy-now-click - shopId: {}, productId: {}, sessionId: {}", shopId, productId,
                sessionId);

        // Parse price and currency
        Double price = null;
        String currency = null;
        Object priceObj = event.get("price");
        if (priceObj != null) {
            String priceStr = priceObj.toString().trim();
            String[] parts = priceStr.split(" ");
            try {
                price = Double.parseDouble(parts[0]);
            } catch (Exception e) {
                log.warn("Failed to parse price: {}", priceStr);
            }
            if (parts.length > 1) {
                currency = parts[1];
            }
        }

        // TODO: Remove from comment
        /*
         * if (!isValidSearchEvent(shopId, sessionId, productId, "BuyNowClickEvent")) {
         * return ResponseEntity.noContent().build();
         * }
         */

        BuyNowClickEvent e = BuyNowClickEvent.builder()
                .shopId(shopId)
                .clientId((String) event.get("client_id"))
                .sessionId(sessionId)
                .productId(productId)
                .timestampMs(event.get("timestamp") != null ? ((Number) event.get("timestamp")).longValue()
                        : System.currentTimeMillis())
                .price(price)
                .currency(currency)
                .build();
        log.info("BuyNowClickEvent recorded: productId {} for session {}", productId, sessionId);
        return ResponseEntity.ok(buyNowRepo.save(e).getId());
    }

    @PostMapping("/dashboard/product-click")
    public ResponseEntity<?> productClick(@RequestBody java.util.Map<String, Object> event) {
        String shopId = (String) event.get("shopId");
        String sessionId = (String) event.get("sessionId");
        String productId = (String) event.get("productId");

        // URL decode the product ID if it's encoded
        if (productId != null && productId.contains("%")) {
            try {
                productId = java.net.URLDecoder.decode(productId, "UTF-8");
            } catch (Exception ex) {
                log.warn("Failed to decode productId: {}", productId);
            }
        }

        if (shopId == null)
            return error(HttpStatus.BAD_REQUEST, "MISSING_SHOP_ID");

        log.info("POST /dashboard/product-click - shopId: {}, productId: {}, sessionId: {}", shopId, productId,
                sessionId);

        // TODO: Remove from comment
        /*
         * if (!isValidSearchEvent(shopId, sessionId, productId, "ProductClickEvent")) {
         * return ResponseEntity.noContent().build();
         * }
         */

        ProductClickEvent e = ProductClickEvent.builder()
                .shopId(shopId)
                .clientId((String) event.get("client_id"))
                .sessionId(sessionId)
                .productId(productId)
                .searchId((String) event.get("search_id"))
                .query((String) event.get("query"))
                .productTitle((String) event.get("product_title"))
                .url((String) event.get("url"))
                .timestampMs(event.get("timestamp") != null ? ((Number) event.get("timestamp")).longValue()
                        : System.currentTimeMillis())
                .build();
        log.info("ProductClickEvent recorded: productId {} for session {}", productId, sessionId);
        return ResponseEntity.ok(clickRepo.save(e).getId());
    }

    private ResponseEntity<?> error(HttpStatus status, String code) {
        return ResponseEntity.status(status).body(java.util.Map.of("error", code));
    }
}
