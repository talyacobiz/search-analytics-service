package com.talya.searchanalytics.web;

import com.talya.searchanalytics.model.Shop;
import com.talya.searchanalytics.repo.ShopRepository;
import com.talya.searchanalytics.service.ShopService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/shops")
@RequiredArgsConstructor
public class ShopAdminController {

    private final ShopService shopService;
    private final ShopRepository shopRepository;

    private boolean isOwner() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null) return false;
        return a.getAuthorities().stream().anyMatch(auth -> "ROLE_OWNER".equals(auth.getAuthority()));
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateShopRequest req) {
        if (!isOwner()) return error(HttpStatus.FORBIDDEN, "FORBIDDEN");
        if (shopRepository.existsByShopDomain(req.getShopDomain())) return error(HttpStatus.CONFLICT, "ALREADY_EXISTS");
        ShopService.ShopCreation creation = shopService.createShopReturnPlain(req.getShopDomain());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "shopDomain", creation.getShop().getShopDomain(),
                "generatedPassword", creation.getPlaintextPassword(),
                "role", Shop.Role.SHOP.name()
        ));
    }

    @GetMapping
    public ResponseEntity<?> list() {
        if (!isOwner()) return error(HttpStatus.FORBIDDEN, "FORBIDDEN");
        return ResponseEntity.ok(Map.of("shops", shopService.list().stream().map(s -> Map.of(
                "shopDomain", s.getShopDomain(),
                "role", s.getRole().name(),
                "createdAt", s.getCreatedAt(),
                "status", s.getStatus().name()
        )).collect(Collectors.toList())));
    }

    @PostMapping("/{domain}/rotate")
    public ResponseEntity<?> rotate(@PathVariable String domain) {
        if (!isOwner()) return error(HttpStatus.FORBIDDEN, "FORBIDDEN");
        return shopService.rotatePassword(domain)
                .<ResponseEntity<?>>map(pwd -> ResponseEntity.ok(Map.of("shopDomain", domain, "generatedPassword", pwd)))
                .orElseGet(() -> error(HttpStatus.NOT_FOUND, "SHOP_NOT_FOUND"));
    }

    @PutMapping("/{domain}/status")
    public ResponseEntity<?> status(@PathVariable String domain, @RequestBody StatusRequest req) {
        if (!isOwner()) return error(HttpStatus.FORBIDDEN, "FORBIDDEN");
        Shop.Status status;
        try { status = Shop.Status.valueOf(req.getStatus()); } catch (Exception e) { return error(HttpStatus.BAD_REQUEST, "INVALID_STATUS"); }
        boolean ok = shopService.updateStatus(domain, status);
        if (!ok) return error(HttpStatus.NOT_FOUND, "SHOP_NOT_FOUND");
        return ResponseEntity.ok(Map.of("shopDomain", domain, "status", status.name()));
    }

    @DeleteMapping("/{domain}")
    public ResponseEntity<?> delete(@PathVariable String domain) {
        if (!isOwner()) return error(HttpStatus.FORBIDDEN, "FORBIDDEN");
        boolean ok = shopService.disable(domain);
        if (!ok) return error(HttpStatus.NOT_FOUND, "SHOP_NOT_FOUND");
        return ResponseEntity.ok(Map.of("shopDomain", domain, "status", "DISABLED"));
    }

    @PatchMapping("/{domain}")
    public ResponseEntity<?> updateShop(@PathVariable String domain, @RequestBody UpdateShopRequest req) {
        if (!isOwner()) return error(HttpStatus.FORBIDDEN, "FORBIDDEN");
        return shopRepository.findByShopDomain(domain).map(shop -> {
            if (req.getPassword() != null && !req.getPassword().isBlank()) {
                shop.setPasswordHash(shopService.hashPassword(req.getPassword()));
            }
            if (req.getRole() != null) {
                try {
                    shop.setRole(Shop.Role.valueOf(req.getRole()));
                } catch (Exception e) {
                    return error(HttpStatus.BAD_REQUEST, "INVALID_ROLE");
                }
            }
            shopRepository.save(shop);
            return ResponseEntity.ok(Map.of("shopDomain", domain, "updated", true));
        }).<ResponseEntity<?>>orElseGet(() -> error(HttpStatus.NOT_FOUND, "SHOP_NOT_FOUND"));
    }

    private ResponseEntity<?> error(HttpStatus status, String code) { return ResponseEntity.status(status).body(Map.of("error", code)); }

    @Data
    public static class CreateShopRequest { private String shopDomain; }
    @Data
    public static class StatusRequest { private String status; }
    @Data
    public static class UpdateShopRequest { private String password; private String role; }
}

