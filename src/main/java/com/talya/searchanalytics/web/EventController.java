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
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {

    private static final Logger log = LoggerFactory.getLogger(EventController.class);

    private final SearchEventRepository searchRepo;
    private final AddToCartEventRepository cartRepo;
    private final PurchaseEventRepository purchaseRepo;
    private final ProductClickEventRepository clickRepo;
    private final BuyNowClickEventRepository buyNowRepo;

    @PostMapping("/search")
    public ResponseEntity<?> recordSearch(@RequestBody SearchEventRequest req) {
        log.info("POST /api/v1/events/search - payload: {}", req);
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

    @PostMapping("/add-to-cart")
    public ResponseEntity<?> recordAddToCart(@RequestBody AddToCartRequest req) {
        log.info("POST /api/v1/events/add-to-cart - payload: {}", req);
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
        // Smart add-to-cart logic
        List<SearchEvent> searches = searchRepo.findAllByShopIdAndSessionId(req.getShopId(), req.getSessionId());
        log.info("🛒 Add-to-cart validation: sessionId={}, productId={}, found {} search events",
                req.getSessionId(), req.getProductId(), searches.size());

        boolean found = false;
        for (SearchEvent search : searches) {
            if (search.getProductIds() != null) {
                log.debug("Search {} has {} products: {}", search.getSearchId(),
                        search.getProductIds().size(), search.getProductIds());
                if (search.getProductIds().contains(req.getProductId())) {
                    found = true;
                    break;
                }
            }
        }
        if (!found) {
            log.warn(
                    "AddToCartEvent REJECTED: productId {} not found in any search results for session {}. Searched {} events.",
                    req.getProductId(), req.getSessionId(), searches.size());
            return ResponseEntity.noContent().build();
        }
        log.info("AddToCartEvent ACCEPTED: productId {} found in search results", req.getProductId());
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

    @PostMapping("/purchase")
    public ResponseEntity<?> handleShopifyOrder(@RequestBody java.util.Map<String, Object> orderData) {
        try {
            // --- Extract raw Shopify info ---
            String shopId = (String) orderData.get("shopId");
            log.info("Received Shopify order from: {}", shopId);

            String searchaiUserId = (String) orderData.get("searchai_user_id");
            String searchaiSessionId = (String) orderData.get("searchai_session_id");
            String currency = (String) orderData.get("currency");
            Object timeObj = orderData.get("time");
            Long timestampMs = timeObj instanceof Number ? ((Number) timeObj).longValue() : System.currentTimeMillis();

            @SuppressWarnings("unchecked")
            List<java.util.Map<String, Object>> products = (List<java.util.Map<String, Object>>) orderData
                    .get("products");

            // --- Convert product list ---
            List<Product> products_list = new java.util.ArrayList<>();
            double totalAmount = 0.0;

            if (products != null) {
                for (java.util.Map<String, Object> p : products) {
                    String productId = String.valueOf(p.get("product_id"));
                    String name = (String) p.get("name");
                    double price = Double.parseDouble(p.get("price").toString());
                    int amount = Integer.parseInt(p.get("amount").toString());
                    
                    products_list.add(new Product(productId, name, price, amount));
                    totalAmount += price * amount;
                }
            }

            // --- Build PurchaseEvent ---
            PurchaseEvent purchase = PurchaseEvent.builder()
                    .shopId(shopId)
                    .clientId(searchaiUserId)
                    .sessionId(searchaiSessionId)
                    .products(products_list)
                    .totalAmount(totalAmount)
                    .currency(currency)
                    .timestampMs(timestampMs)
                    .orderStatus((String) orderData.get("financial_status"))
                    .build();

            purchaseRepo.save(purchase);
            log.info("✅ Saved purchase event for order with {} products", products_list.size());

            return ResponseEntity.ok().build();

        } catch (Exception e) {
            log.error("Error processing Shopify order: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body("Error processing order: " + e.getMessage());
        }
    }

    @PostMapping("/product-click")
    public ResponseEntity<?> recordProductClick(@RequestBody ProductClickRequest req) {
        log.info("POST /api/v1/events/product-click - payload: {}", req);
        ProductClickEvent e = ProductClickEvent.builder()
                .shopId(req.getShopId())
                .clientId(req.getClientId())
                .sessionId(req.getSessionId())
                .productId(req.getProductId())
                .searchId(req.getSearchId())
                .timestampMs(req.getTimestampMs())
                .build();
        return ResponseEntity.ok(clickRepo.save(e).getId());
    }

    @PostMapping("/buy-now-click")
    public ResponseEntity<?> recordBuyNowClick(@RequestBody BuyNowClickRequest req) {
        log.info("POST /api/v1/events/buy-now-click - payload: {}", req);
        BuyNowClickEvent e = BuyNowClickEvent.builder()
                .shopId(req.getShopId())
                .clientId(req.getClientId())
                .sessionId(req.getSessionId())
                .productId(req.getProductId())
                .timestampMs(req.getTimestampMs())
                .build();
        return ResponseEntity.ok(buyNowRepo.save(e).getId());
    }

}
