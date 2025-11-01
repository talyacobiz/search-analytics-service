package com.talya.searchanalytics.web;

import com.talya.searchanalytics.service.AnalyticsService;
import com.talya.searchanalytics.web.dto.AnalyticsSummaryResponse;
import com.talya.searchanalytics.web.dto.AnalyticsFullResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/analytics")
@CrossOrigin(
        originPatterns = {
                "https://searchwithai.myshopify.com",
                "http://localhost:*",
                "https://*.ngrok-free.app"
        },
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
        allowCredentials = "true",
        maxAge = 3600
)
@RequiredArgsConstructor
public class AnalyticsController {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsController.class);
    private final AnalyticsService analyticsService;

    @GetMapping(value = "/summary", produces = "application/json")
    public AnalyticsSummaryResponse summary(
            @RequestParam(name = "shopId") String shopId,
            @RequestParam(name = "fromMs") long fromMs,
            @RequestParam(name = "toMs") long toMs
    ) {
        log.info("GET /summary - shopId: {}, fromMs: {}, toMs: {}", shopId, fromMs, toMs);
        return analyticsService.summary(shopId, fromMs, toMs);
    }

    @GetMapping(value = "/full", produces = "application/json")
    public AnalyticsFullResponse full(
            @RequestParam(name = "shopId") String shopId,
            @RequestParam(name = "fromMs") long fromMs,
            @RequestParam(name = "toMs") long toMs
    ) {
        log.info("GET /full - shopId: {}, fromMs: {}, toMs: {}", shopId, fromMs, toMs);
        return analyticsService.full(shopId, fromMs, toMs);
    }
}
