package com.talya.searchanalytics.web;

import com.talya.searchanalytics.service.AnalyticsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/dashboard")
@CrossOrigin(
        origins = "*",
        allowedHeaders = "*",
        methods = { RequestMethod.GET, RequestMethod.POST, RequestMethod.OPTIONS },
        allowCredentials = "false"
)
public class DashboardController {

    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);
    private final AnalyticsService analyticsService;

    public DashboardController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /*@PostMapping("/buy-now-click")
    public ResponseEntity<?> buyNowClick(@RequestBody java.util.Map<String, Object> event) {
        String shopId = (String) event.get("shopId");
        if (shopId == null) return error(HttpStatus.BAD_REQUEST, "MISSING_SHOP_ID");

        log.info("POST /dashboard/buy-now-click - shopId: {}, productId: {}, sessionId: {}, event: {}",
                shopId, event.get("product_id"), event.get("session_id"), event);
        analyticsService.recordBuyNow(shopId, event);
        return ResponseEntity.ok(java.util.Map.of("status", "recorded"));
    }

    @PostMapping("/product-click")
    public ResponseEntity<?> productClick(@RequestBody java.util.Map<String, Object> event) {
        String shopId = (String) event.get("shopId");
        if (shopId == null) return error(HttpStatus.BAD_REQUEST, "MISSING_SHOP_ID");

        log.info("POST /dashboard/product-click - shopId: {}, productId: {}, sessionId: {}, event: {}",
                shopId, event.get("productId"), event.get("sessionId"), event);
        analyticsService.recordProductClick(shopId, event);
        return ResponseEntity.ok(java.util.Map.of("status", "recorded"));
    }*/

    private ResponseEntity<?> error(HttpStatus status, String code) {
        return ResponseEntity.status(status).body(java.util.Map.of("error", code));
    }
}
