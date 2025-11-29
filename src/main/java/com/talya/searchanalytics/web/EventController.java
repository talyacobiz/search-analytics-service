package com.talya.searchanalytics.web;

import com.talya.searchanalytics.model.*;
import com.talya.searchanalytics.repo.*;
import com.talya.searchanalytics.web.dto.EventDTOs.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

@RestController
@RequestMapping("/api/v1/events")
// @CrossOrigin(origins = "*")
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
                .customerId(req.getCustomerId())
                .sessionId(req.getSessionId())
                .query(req.getQuery())
                .productIds(req.getProductIds())
                .timestampMs(req.getTimestampMs())
                .build();
        return ResponseEntity.ok(searchRepo.save(e).getId());
    }

    // @RequestMapping(value = "/search", method = RequestMethod.OPTIONS)
    // public void corsHeadersSearch(HttpServletResponse response) {
    //     response.setHeader("Access-Control-Allow-Origin", "*");
    //     response.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
    //     response.setHeader("Access-Control-Allow-Headers", "Content-Type");
    //     response.setStatus(HttpServletResponse.SC_OK);
    // }

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
        boolean found = false;
        for (SearchEvent search : searches) {
            if (search.getProductIds() != null && search.getProductIds().contains(req.getProductId())) {
                found = true;
                break;
            }
        }
        if (!found) {
            log.info("AddToCartEvent ignored: productId {} not found in any search results for session {}", req.getProductId(), req.getSessionId());
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

    // @RequestMapping(value = "/add-to-cart", method = RequestMethod.OPTIONS)
    // public void corsHeadersAddToCart(HttpServletResponse response) {
    //     response.setHeader("Access-Control-Allow-Origin", "*");
    //     response.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
    //     response.setHeader("Access-Control-Allow-Headers", "Content-Type");
    //     response.setStatus(HttpServletResponse.SC_OK);
    // }
    
@PostMapping("/purchase")
public ResponseEntity<?> handleShopifyOrder(
        @RequestBody Map<String, Object> orderData) {

    

    try {
        // --- Extract raw Shopify info ---
        String shopId = (String) orderData.get("shopId");
        log.info("🛍️ Received Shopify order from: {}", shopId);

        String searchaiUserId = (String) orderData.get("searchai_user_id");
        String searchaiSessionId = (String) orderData.get("searchai_session_id");
        String currency = (String) orderData.get("currency");
        String time = (String) orderData.get("time");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> products =
                (List<Map<String, Object>>) orderData.get("products");

        // --- Convert product list ---
        List<String> productIds = new ArrayList<>();
        List<String> productTitles = new ArrayList<>();
        List<String> variantIds = new ArrayList<>(); // no variant in orderData → keep empty
        double totalAmount = 0.0;

        for (Map<String, Object> p : products) {
            productIds.add(String.valueOf(p.get("product_id")));
            productTitles.add((String) p.get("name"));

            double price = Double.parseDouble(p.get("price").toString());
            int qty = Integer.parseInt(p.get("amount").toString());
            totalAmount += price * qty;
        }

        // --- Build PurchaseEvent ---
        PurchaseEvent purchase = PurchaseEvent.builder()
                .shopId(shopId)

                // new SearchAI identifiers
                .userId(searchaiUserId)
                .sessionId(searchaiSessionId)

                .productIds(productIds)
                .productTitles(String.join(",", productTitles))

                .totalAmount(totalAmount)
                .currency(currency)

                .timestampMs((Long) orderData.get("time")) // or parse `time`
                .orderStatus((String) orderData.get("financial_status"))
                .build();

        purchaseRepo.save(purchase);
        log.info("✅ Saved purchase event for order");

        return ResponseEntity.ok().build();

    } catch (Exception e) {
        log.error("❌ Error processing Shopify order: {}", e.getMessage(), e);
        return ResponseEntity.badRequest().body("Error processing order: " + e.getMessage());
    }
}


    // @RequestMapping(value = "/purchase", method = RequestMethod.OPTIONS)
    // public void corsHeadersPurchase(HttpServletResponse response) {
    //     response.setHeader("Access-Control-Allow-Origin", "*");
    //     response.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
    //     response.setHeader("Access-Control-Allow-Headers", "Content-Type");
    //     response.setStatus(HttpServletResponse.SC_OK);
    // }

    @PostMapping("/product-click")
    public ResponseEntity<?> recordProductClick(@RequestBody ProductClickRequest req) {
        log.info("POST /api/v1/events/product-click - payload: {}", req);
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

    // @RequestMapping(value = "/product-click", method = RequestMethod.OPTIONS)
    // public void corsHeadersProductClick(HttpServletResponse response) {
    //     response.setHeader("Access-Control-Allow-Origin", "*");
    //     response.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
    //     response.setHeader("Access-Control-Allow-Headers", "Content-Type");
    //     response.setStatus(HttpServletResponse.SC_OK);
    // }

    @PostMapping("/buy-now-click")
    public ResponseEntity<?> recordBuyNowClick(@RequestBody BuyNowClickRequest req) {
        log.info("POST /api/v1/events/buy-now-click - payload: {}", req);
        BuyNowClickEvent e = BuyNowClickEvent.builder()
                .shopId(req.getShopId())
                .customerId(req.getCustomerId())
                .sessionId(req.getSessionId())
                .productId(req.getProductId())
                .timestampMs(req.getTimestampMs())
                .build();
        return ResponseEntity.ok(buyNowRepo.save(e).getId());
    }

    // @RequestMapping(value = "/buy-now-click", method = RequestMethod.OPTIONS)
    // public void corsHeadersBuyNowClick(HttpServletResponse response) {
    //     response.setHeader("Access-Control-Allow-Origin", "*");
    //     response.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
    //     response.setHeader("Access-Control-Allow-Headers", "Content-Type");
    //     response.setStatus(HttpServletResponse.SC_OK);
    // }
}
