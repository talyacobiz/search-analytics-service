package com.talya.searchanalytics.web;

import com.talya.searchanalytics.service.AnalyticsService;
import com.talya.searchanalytics.web.dto.AnalyticsSummaryResponse;
import com.talya.searchanalytics.web.dto.AnalyticsFullResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMethod;

@RestController
@CrossOrigin(origins = "https://searchwithai.myshopify.com, https://localhost:5179, https://localhost:8081, https://260f3f5b2b50.ngrok-free.app")
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

    @RequestMapping(value = "/summary", method = RequestMethod.OPTIONS)
    public void corsHeadersSummary(HttpServletResponse response, @RequestHeader(value = "Origin", required = false) String origin) {
        log.info("OPTIONS /summary - CORS preflight from origin: {}", origin);
        // Allowed origins
        String[] allowedOrigins = {
            "https://searchwithai.myshopify.com",
            "http://localhost:5173",
            "http://localhost:5179",
                "http://localhost:8081",
            "https://260f3f5b2b50.ngrok-free.app"
        };
        boolean allowed = false;
        if (origin != null) {
            for (String o : allowedOrigins) {
                if (o.equals(origin)) {
                    allowed = true;
                    break;
                }
            }
        }
        if (allowed) {
            response.setHeader("Access-Control-Allow-Origin", origin);
        } else {
            response.setHeader("Access-Control-Allow-Origin", "https://searchwithai.myshopify.com"); // fallback
        }
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
        return analyticsService.full(shopId, fromMs, toMs);
    }

    @RequestMapping(value = "/full", method = RequestMethod.OPTIONS)
    public void corsHeadersFull(HttpServletResponse response, @RequestHeader(value = "Origin", required = false) String origin) {
        log.info("OPTIONS /full - CORS preflight from origin: {}", origin);
        String[] allowedOrigins = {
            "https://searchwithai.myshopify.com",
            "http://localhost:5173",
            "http://localhost:5179",
            "https://260f3f5b2b50.ngrok-free.app"
        };
        boolean allowed = false;
        if (origin != null) {
            for (String o : allowedOrigins) {
                if (o.equals(origin)) {
                    allowed = true;
                    break;
                }
            }
        }
        if (allowed) {
            response.setHeader("Access-Control-Allow-Origin", origin);
        } else {
            response.setHeader("Access-Control-Allow-Origin", "https://searchwithai.myshopify.com");
        }
        response.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");
        response.setStatus(HttpServletResponse.SC_OK);
    }
}
