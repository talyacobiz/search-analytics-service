package com.talya.searchanalytics.web;

import com.talya.searchanalytics.model.TermsAgreement;
import com.talya.searchanalytics.repo.TermsAgreementRepository;
import com.talya.searchanalytics.web.dto.TermsDTOs.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/terms")
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
        methods = { RequestMethod.GET, RequestMethod.POST, RequestMethod.OPTIONS },
        allowCredentials = "true",
        maxAge = 3600
)
@RequiredArgsConstructor
public class TermsController {

    private static final Logger log = LoggerFactory.getLogger(TermsController.class);
    private final TermsAgreementRepository termsRepo;

    @PostMapping(value = "/agree", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> agree(@RequestBody AgreeRequest req) {
        if (req.getShopId() == null || req.getShopId().isEmpty()) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", "shopId is required"));
        }
        String shopId = req.getShopId();
        String version = req.getTermsVersion();
        Instant acceptedAt = (req.getAcceptedAt() != null) ? req.getAcceptedAt() : Instant.now();

        TermsAgreement agreement = termsRepo.findByShopId(shopId)
                .orElse(TermsAgreement.builder().shopId(shopId).build());
        agreement.setTermsVersion(version);
        agreement.setAcceptedAt(acceptedAt);
        agreement = termsRepo.save(agreement);

        log.info("POST /api/v1/terms/agree - shopId: {}, version: {}", shopId, version);
        return ResponseEntity.ok(AgreeResponse.builder()
                .ok(true)
                .shopId(agreement.getShopId())
                .termsVersion(agreement.getTermsVersion())
                .acceptedAt(agreement.getAcceptedAt())
                .build());
    }

    @GetMapping(value = "/status", produces = "application/json")
    public ResponseEntity<?> status(@RequestParam("shopId") String shopId) {
        return termsRepo.findByShopId(shopId)
                .<ResponseEntity<?>>map(a -> ResponseEntity.ok(StatusResponse.builder()
                        .agreed(true)
                        .shopId(a.getShopId())
                        .termsVersion(a.getTermsVersion())
                        .acceptedAt(a.getAcceptedAt())
                        .build()))
                .orElse(ResponseEntity.ok(StatusResponse.builder()
                        .agreed(false)
                        .shopId(shopId)
                        .build()));
    }
}
