package com.talya.searchanalytics.service;

import com.talya.searchanalytics.model.Shop;
import com.talya.searchanalytics.repo.ShopRepository;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ShopService {
    private final ShopRepository shopRepository;
    private final PasswordEncoder passwordEncoder;

    public String generateRandomPassword() {
        SecureRandom sr = new SecureRandom();
        byte[] bytes = new byte[18];
        sr.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public ShopCreation createShopReturnPlain(String domain) {
        String pwd = generateRandomPassword();
        Shop shop = Shop.builder()
                .shopDomain(domain)
                .passwordHash(passwordEncoder.encode(pwd))
                .role(Shop.Role.SHOP)
                .status(Shop.Status.ACTIVE)
                .build();
        shopRepository.save(shop);
        return new ShopCreation(shop, pwd);
    }

    public Optional<Shop> findByDomain(String domain) { return shopRepository.findByShopDomain(domain); }

    public List<Shop> list() { return shopRepository.findAll(); }

    public Optional<String> rotatePassword(String domain) {
        return shopRepository.findByShopDomain(domain).map(s -> {
            String pwd = generateRandomPassword();
            s.setPasswordHash(passwordEncoder.encode(pwd));
            s.setUpdatedAt(Instant.now());
            shopRepository.save(s);
            return pwd;
        });
    }

    public boolean updateStatus(String domain, Shop.Status status) {
        return shopRepository.findByShopDomain(domain).map(s -> {
            s.setStatus(status);
            s.setUpdatedAt(Instant.now());
            shopRepository.save(s);
            return true;
        }).orElse(false);
    }

    public boolean disable(String domain) { return updateStatus(domain, Shop.Status.DISABLED); }

    public String hashPassword(String plaintext) {
        return passwordEncoder.encode(plaintext);
    }

    public Shop createShop(String domain) {
        // legacy wrapper: DO NOT use for new code (returns hashed password only)
        var creation = createShopReturnPlain(domain);
        return creation.getShop();
    }

    @Value
    public static class ShopCreation { Shop shop; String plaintextPassword; }
}
