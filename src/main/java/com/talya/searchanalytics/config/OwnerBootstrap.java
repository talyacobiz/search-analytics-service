package com.talya.searchanalytics.config;

import com.talya.searchanalytics.model.Shop;
import com.talya.searchanalytics.repo.ShopRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

@Component
@RequiredArgsConstructor
public class OwnerBootstrap implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(OwnerBootstrap.class);
    private final ShopRepository shopRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${security.owner.domain:owner}")
    private String ownerDomain;
    @Value("${security.owner.password:}")
    private String ownerPassword;

    @Override
    public void run(String... args) {
        boolean exists = shopRepository.findByShopDomain(ownerDomain).filter(s -> s.getRole() == Shop.Role.OWNER).isPresent();
        if (exists) return;
        String pwd = ownerPassword;
        if (pwd == null || pwd.isBlank()) {
            pwd = generatePassword();
            log.warn("Generated OWNER password (store safely, will not be shown again): {}", pwd);
        } else {
            log.info("Using configured OWNER password for domain '{}'.", ownerDomain);
        }
        Shop owner = Shop.builder()
                .shopDomain(ownerDomain)
                .passwordHash(passwordEncoder.encode(pwd))
                .role(Shop.Role.OWNER)
                .status(Shop.Status.ACTIVE)
                .build();
        shopRepository.save(owner);
        log.info("OWNER bootstrap complete for domain '{}'.", ownerDomain);
    }

    private String generatePassword() {
        SecureRandom sr = new SecureRandom();
        byte[] bytes = new byte[18];
        sr.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
