package com.talya.searchanalytics.web;

import com.talya.searchanalytics.service.AnalyticsService;
import com.talya.searchanalytics.web.dto.AnalyticsSummaryResponse;
import com.talya.searchanalytics.web.dto.AnalyticsFullResponse;
import lombok.RequiredArgsConstructor;
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
        methods = { RequestMethod.GET, RequestMethod.OPTIONS },
        allowCredentials = "false",
        maxAge = 3600
)
@RequiredArgsConstructor
public class AnalyticsController {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsController.class);
    private final AnalyticsService analyticsService;

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
}
