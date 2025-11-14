package com.talya.searchanalytics.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class JwtService {
    @Value("${security.jwt.secret:dev-secret-key}")
    private String jwtSecret;
    @Value("${security.jwt.expirySeconds:3600}")
    private long expirySeconds;

    public String generateToken(String shopDomain, Enum<?> role) {
        long now = System.currentTimeMillis();
        Map<String, Object> claims = new HashMap<>();
        claims.put("sub", shopDomain);
        claims.put("shop", shopDomain);
        claims.put("role", role.name());
        claims.put("iat", now / 1000);
        claims.put("exp", (now / 1000) + expirySeconds);
        return Jwts.builder()
                .setClaims(claims)
                .signWith(SignatureAlgorithm.HS256, jwtSecret.getBytes())
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .setSigningKey(jwtSecret.getBytes())
                .parseClaimsJws(token)
                .getBody();
    }

    public long getExpirySeconds() {
        return expirySeconds;
    }
}

