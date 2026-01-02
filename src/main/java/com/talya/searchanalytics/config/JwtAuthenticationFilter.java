package com.talya.searchanalytics.config;

import com.talya.searchanalytics.model.Shop;
import com.talya.searchanalytics.repo.ShopRepository;
import com.talya.searchanalytics.service.JwtService;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final ShopRepository shopRepository;

    public JwtAuthenticationFilter(JwtService jwtService, ShopRepository shopRepository) {
        this.jwtService = jwtService;
        this.shopRepository = shopRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        // Allow unauthenticated access to login and event ingestion endpoints
        if (path.startsWith("/api/v1/auth/login") || isPublicEventEndpoint(path)) {
            filterChain.doFilter(request, response);
            return;
        }
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            unauthorized(response, "MISSING_TOKEN");
            return;
        }
        String token = auth.substring(7);
        try {
            var claims = jwtService.parse(token);
            String shopDomain = (String) claims.get("shop");
            String role = (String) claims.get("role");
            Shop shop = shopRepository.findByShopDomain(shopDomain).orElse(null);
            if (shop == null) {
                unauthorized(response, "UNKNOWN_SHOP");
                return;
            }
            if (shop.getStatus() == Shop.Status.DISABLED) {
                forbidden(response, "SHOP_DISABLED");
                return;
            }
            Authentication authObj = new UsernamePasswordAuthenticationToken(shopDomain, null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + role)));
            SecurityContextHolder.getContext().setAuthentication(authObj);
        } catch (Exception e) {
            unauthorized(response, "INVALID_TOKEN");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isPublicEventEndpoint(String path) {
        return path.startsWith("/api/v1/events/");
    }

    private void unauthorized(HttpServletResponse resp, String code) throws IOException {
        resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        resp.setContentType(MediaType.APPLICATION_JSON_VALUE);
        resp.getWriter().write("{\"error\":\"" + code + "\"}");
    }

    private void forbidden(HttpServletResponse resp, String code) throws IOException {
        resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
        resp.setContentType(MediaType.APPLICATION_JSON_VALUE);
        resp.getWriter().write("{\"error\":\"" + code + "\"}");
    }
}
