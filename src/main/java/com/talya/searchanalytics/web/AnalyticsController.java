package com.talya.searchanalytics.web;

import com.talya.searchanalytics.service.AnalyticsService;
import com.talya.searchanalytics.service.DataMigrationService;
import com.talya.searchanalytics.repo.TermsAgreementRepository;
import lombok.RequiredArgsConstructor;

import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/analytics")
@CrossOrigin(origins = "*", allowedHeaders = {
        "Content-Type",
        "Accept",
        "Authorization",
        "X-Requested-With",
        "ngrok-skip-browser-warning"
}, exposedHeaders = {
        "Location", "Link"
}, methods = { RequestMethod.GET, RequestMethod.POST,
        RequestMethod.OPTIONS }, allowCredentials = "false", maxAge = 3600)
@RequiredArgsConstructor
public class AnalyticsController {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsController.class);
    private final AnalyticsService analyticsService;
    private final DataMigrationService dataMigrationService;
    private final TermsAgreementRepository termsRepo;

    @GetMapping(value = "/test", produces = "application/json")
    public ResponseEntity<?> test() {
        return ResponseEntity.ok(java.util.Map.of(
                "status", "ok",
                "totalSearches", 0,
                "totalProductClicks", 0,
                "clickThroughRate", 0.0,
                "message", "test endpoint working"));
    }

    @PostMapping(value = "/backfill-search-group", produces = "application/json")
    public ResponseEntity<?> backfillSearchGroup() {
        String shopId = currentShop();
        if (shopId == null)
            return error(HttpStatus.UNAUTHORIZED, "MISSING_TOKEN");

        log.info("POST /backfill-search-group - shopId: {}", shopId);

        try {
            String result = dataMigrationService.backfillSearchGroupBeforeFeb8();
            return ResponseEntity.ok(java.util.Map.of(
                    "status", "success",
                    "message", result));
        } catch (Exception e) {
            log.error("Error during searchGroup backfill", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(java.util.Map.of(
                    "status", "error",
                    "message", "Backfill failed: " + e.getMessage()));
        }
    }

    @GetMapping(value = "/summary", produces = "application/json")
    public ResponseEntity<?> summary(
            @RequestParam(name = "fromMs") long fromMs,
            @RequestParam(name = "toMs") long toMs,
            @RequestParam(name = "shopId", required = false) String legacyShopId,
            @RequestParam(name = "searchGroup", required = false) Integer searchGroup) {
        String shopId = currentShop();
        if (shopId == null)
            return error(HttpStatus.UNAUTHORIZED, "MISSING_TOKEN");
        if (legacyShopId != null && !legacyShopId.equals(shopId))
            return error(HttpStatus.FORBIDDEN, "SHOP_ID_MISMATCH");
        if (fromMs > toMs)
            return error(HttpStatus.BAD_REQUEST, "INVALID_RANGE");
        log.info("GET /summary - shopId: {}, fromMs: {}, toMs: {}, searchGroup: {}", shopId, fromMs, toMs, searchGroup);
        return ResponseEntity.ok(analyticsService.summary(shopId, fromMs, toMs, searchGroup));
    }

    @GetMapping(value = "/compare", produces = "application/json")
    public ResponseEntity<?> compare(
            @RequestParam(name = "fromMs") long fromMs,
            @RequestParam(name = "toMs") long toMs,
            @RequestParam(name = "groupA") Integer groupA,
            @RequestParam(name = "groupB") Integer groupB,
            @RequestParam(name = "shopId", required = false) String legacyShopId) {
        String shopId = currentShop();
        if (shopId == null)
            return error(HttpStatus.UNAUTHORIZED, "MISSING_TOKEN");
        if (legacyShopId != null && !legacyShopId.equals(shopId))
            return error(HttpStatus.FORBIDDEN, "SHOP_ID_MISMATCH");
        if (fromMs > toMs)
            return error(HttpStatus.BAD_REQUEST, "INVALID_RANGE");
        if (groupA == null || groupB == null)
            return error(HttpStatus.BAD_REQUEST, "MISSING_GROUPS");

        log.info("GET /compare - shopId: {}, fromMs: {}, toMs: {}, groupA: {}, groupB: {}",
                shopId, fromMs, toMs, groupA, groupB);
        return ResponseEntity.ok(analyticsService.compareGroups(shopId, fromMs, toMs, groupA, groupB));
    }

    @GetMapping(value = "/full", produces = "application/json")
    public ResponseEntity<?> full(
            @RequestParam(name = "fromMs") long fromMs,
            @RequestParam(name = "toMs") long toMs,
            @RequestParam(name = "shopId", required = false) String legacyShopId) {
        String shopId = currentShop();
        if (shopId == null)
            return error(HttpStatus.UNAUTHORIZED, "MISSING_TOKEN");
        if (legacyShopId != null && !legacyShopId.equals(shopId))
            return error(HttpStatus.FORBIDDEN, "SHOP_ID_MISMATCH");
        if (fromMs > toMs)
            return error(HttpStatus.BAD_REQUEST, "INVALID_RANGE");
        log.info("GET /full - shopId: {}, fromMs: {}, toMs: {}", shopId, fromMs, toMs);
        // Guard: require terms agreement
        boolean agreed = termsRepo.findByShopId(shopId).isPresent();
        if (!agreed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Terms not agreed");
        }
        return ResponseEntity.ok(analyticsService.full(shopId, fromMs, toMs));
    }

    private String currentShop() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null)
            return null;
        Object p = a.getPrincipal();
        return (p instanceof String) ? (String) p : null;
    }

    @RequestMapping(value = "/full", method = RequestMethod.OPTIONS)
    public void corsHeadersFull(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");
        response.setStatus(HttpServletResponse.SC_OK);
    }

    @RequestMapping(value = "/summary", method = RequestMethod.OPTIONS)
    public void corsHeadersSummary(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");
        response.setStatus(HttpServletResponse.SC_OK);
    }

    private ResponseEntity<?> error(HttpStatus status, String code) {
        return ResponseEntity.status(status).body(java.util.Map.of("error", code));
    }
}