package com.talya.searchanalytics.web;

import com.talya.searchanalytics.model.Shop;
import com.talya.searchanalytics.repo.ShopRepository;
import com.talya.searchanalytics.service.JwtService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
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
public class AuthController {
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final ShopRepository shopRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        log.debug("Login attempt for username: {}", req.getUsername());

        Shop shop = shopRepository.findByShopDomain(req.getUsername()).orElse(null);
        if (shop == null) {
            log.warn("Login failed - shop not found for domain: {}", req.getUsername());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "INVALID_CREDENTIALS"));
        }
        if (shop.getStatus() == Shop.Status.DISABLED) {
            log.warn("Login failed - shop is disabled: {}", req.getUsername());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "INVALID_CREDENTIALS"));
        }
        if (!passwordEncoder.matches(req.getPassword(), shop.getPasswordHash())) {
            log.warn("Login failed - invalid password for shop: {}", req.getUsername());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "INVALID_CREDENTIALS"));
        }

        log.info("Successful login for shop: {}", shop.getShopDomain());
        shop.setLastLoginAt(Instant.now());
        shopRepository.save(shop);
        String token = jwtService.generateToken(shop.getShopDomain(), shop.getRole());
        log.debug("JWT generated for shop: {} with role: {}", shop.getShopDomain(), shop.getRole());
        return ResponseEntity.ok(new LoginResponse(token, "Bearer", jwtService.getExpirySeconds(), shop.getShopDomain(), shop.getRole().name()));
    }

    @RequestMapping(value = "/login", method = RequestMethod.OPTIONS)
    public void corsHeadersFull(HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");
        response.setStatus(HttpServletResponse.SC_OK);
    }

    @Data
    public static class LoginRequest { private String username; private String password; }
    @Data @AllArgsConstructor
    public static class LoginResponse { private String accessToken; private String tokenType; private long expiresIn; private String shopDomain; private String role; }
}

