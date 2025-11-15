package com.talya.searchanalytics.web;

import com.talya.searchanalytics.service.AnalyticsService;
import com.talya.searchanalytics.web.dto.AnalyticsSummaryResponse;
import com.talya.searchanalytics.web.dto.AnalyticsFullResponse;
import com.talya.searchanalytics.repo.TermsAgreementRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import javax.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/v1/analytics")
@CrossOrigin(
        originPatterns = {
                "https://searchwithai.myshopify.com",
                "http://localhost:*",
                "https://*.ngrok-free.app",
                "https://dashboard.searchaiengine.com"
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
    private final TermsAgreementRepository termsRepo;

    @GetMapping(value = "/summary", produces = "application/json")
    public AnalyticsSummaryResponse summary(
            @RequestParam(name = "shopId") String shopId,
            @RequestParam(name = "fromMs") long fromMs,
            @RequestParam(name = "toMs") long toMs
    ) {
        log.info("GET /summary - shopId: {}, fromMs: {}, toMs: {}", shopId, fromMs, toMs);
        // Guard: require terms agreement
        boolean agreed = termsRepo.findByShopId(shopId).isPresent();
        if (!agreed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Terms not agreed");
        }
        return analyticsService.summary(shopId, fromMs, toMs);
    }

    @RequestMapping(value = "/summary", method = RequestMethod.OPTIONS)
    public void corsHeadersSummary(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "https://dashboard.searchaiengine.com");
        response.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");
        response.setStatus(HttpServletResponse.SC_OK);
    }

    @GetMapping(value = "/full", produces = "application/json")
    public AnalyticsFullResponse full(
            @RequestParam(name = "shopId") String shopId,
            @RequestParam(name = "fromMs") long fromMs,
            @RequestParam(name = "toMs") long toMs
    ) {
        log.info("GET /full - shopId: {}, fromMs: {}, toMs: {}", shopId, fromMs, toMs);
        // Guard: require terms agreement
        boolean agreed = termsRepo.findByShopId(shopId).isPresent();
        if (!agreed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Terms not agreed");
        }
        return analyticsService.full(shopId, fromMs, toMs);
    }

    @RequestMapping(value = "/full", method = RequestMethod.OPTIONS)
    public void corsHeadersFull(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "https://dashboard.searchaiengine.com");
        response.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");
        response.setStatus(HttpServletResponse.SC_OK);
    }
}       
