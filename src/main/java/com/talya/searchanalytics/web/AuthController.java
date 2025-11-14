package com.talya.searchanalytics.web;

import com.talya.searchanalytics.model.Shop;
import com.talya.searchanalytics.repo.ShopRepository;
import com.talya.searchanalytics.service.JwtService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final ShopRepository shopRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        Shop shop = shopRepository.findByShopDomain(req.getUsername()).orElse(null);
        if (shop == null || shop.getStatus() == Shop.Status.DISABLED || !passwordEncoder.matches(req.getPassword(), shop.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "INVALID_CREDENTIALS"));
        }
        shop.setLastLoginAt(Instant.now());
        shopRepository.save(shop);
        String token = jwtService.generateToken(shop.getShopDomain(), shop.getRole());
        return ResponseEntity.ok(new LoginResponse(token, "Bearer", jwtService.getExpirySeconds(), shop.getShopDomain(), shop.getRole().name()));
    }

    @Data
    public static class LoginRequest { private String username; private String password; }
    @Data @AllArgsConstructor
    public static class LoginResponse { private String accessToken; private String tokenType; private long expiresIn; private String shopDomain; private String role; }
}

