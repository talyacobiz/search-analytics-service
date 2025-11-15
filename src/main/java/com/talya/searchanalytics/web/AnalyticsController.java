package com.talya.searchanalytics.web;

import com.talya.searchanalytics.service.AnalyticsService;
import com.talya.searchanalytics.web.dto.AnalyticsSummaryResponse;
import com.talya.searchanalytics.web.dto.AnalyticsFullResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@RestController
@RequestMapping("/api/v1/analytics")
@CrossOrigin(
        origins = "*",
        allowedHeaders = {
                "Content-Type",
                "Accept",
                "Authorization",
                "X-Requested-With",
                "ngrok-skip-browser-warning"
        },
        exposedHeaders = {
                "Location", "Link"
        },
        methods = { RequestMethod.GET, RequestMethod.POST, RequestMethod.OPTIONS },
        allowCredentials = "false",
        maxAge = 3600
)
public class AnalyticsController {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsController.class);
    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping(value = "/summary", produces = "application/json")
    public ResponseEntity<?> summary(
            @RequestParam(name = "fromMs") long fromMs,
            @RequestParam(name = "toMs") long toMs,
            @RequestParam(name = "shopId", required = false) String legacyShopId
    ) {
        String shopId = currentShop();
        if (shopId == null) return error(HttpStatus.UNAUTHORIZED, "MISSING_TOKEN");
        if (legacyShopId != null && !legacyShopId.equals(shopId)) return error(HttpStatus.FORBIDDEN, "SHOP_ID_MISMATCH");
        if (fromMs > toMs) return error(HttpStatus.BAD_REQUEST, "INVALID_RANGE");
        log.info("GET /summary - shopId: {}, fromMs: {}, toMs: {}", shopId, fromMs, toMs);
        return ResponseEntity.ok(analyticsService.summary(shopId, fromMs, toMs));
    }

    @GetMapping(value = "/full", produces = "application/json")
    public ResponseEntity<?> full(
            @RequestParam(name = "fromMs") long fromMs,
            @RequestParam(name = "toMs") long toMs,
            @RequestParam(name = "shopId", required = false) String legacyShopId
    ) {
        String shopId = currentShop();
        if (shopId == null) return error(HttpStatus.UNAUTHORIZED, "MISSING_TOKEN");
        if (legacyShopId != null && !legacyShopId.equals(shopId)) return error(HttpStatus.FORBIDDEN, "SHOP_ID_MISMATCH");
        if (fromMs > toMs) return error(HttpStatus.BAD_REQUEST, "INVALID_RANGE");
        log.info("GET /full - shopId: {}, fromMs: {}, toMs: {}", shopId, fromMs, toMs);
        return ResponseEntity.ok(analyticsService.full(shopId, fromMs, toMs));
    }

    private String currentShop() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null) return null;
        Object p = a.getPrincipal();
        return (p instanceof String) ? (String)p : null;
    }

    private ResponseEntity<?> error(HttpStatus status, String code) { return ResponseEntity.status(status).body(java.util.Map.of("error", code)); }

    /*@PostMapping("/buy-now")
    public ResponseEntity<?> buyNow(@RequestBody java.util.Map<String, Object> event) {
        String shopId = (String) event.get("shopId");
        if (shopId == null) return error(HttpStatus.BAD_REQUEST, "MISSING_SHOP_ID");
        
        log.info("POST /buy-now - shopId: {}, productId: {}", shopId, event.get("product_id"));
        analyticsService.recordBuyNow(shopId, event);
        return ResponseEntity.ok(java.util.Map.of("status", "recorded"));
    }

    @PostMapping("/product-click")
    public ResponseEntity<?> productClick(@RequestBody java.util.Map<String, Object> event) {
        String shopId = (String) event.get("shopId");
        if (shopId == null) return error(HttpStatus.BAD_REQUEST, "MISSING_SHOP_ID");
        
        log.info("POST /product-click - shopId: {}, productId: {}", shopId, event.get("product_id"));
        analyticsService.recordProductClick(shopId, event);
        return ResponseEntity.ok(java.util.Map.of("status", "recorded"));
    }*/
}
