package com.talya.searchanalytics.config;

import com.talya.searchanalytics.repo.ShopRepository;
import com.talya.searchanalytics.service.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class JwtProtectedApiConfig {

    private final JwtService jwtService;
    private final ShopRepository shopRepository;

    @Bean
    @Order(1)
    public SecurityFilterChain jwtApiFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf().disable()
            .addFilterBefore(new JwtAuthenticationFilter(jwtService, shopRepository), UsernamePasswordAuthenticationFilter.class)
            .authorizeRequests()
            .antMatchers("/api/v1/shops/**").hasRole("OWNER")
            .antMatchers("/api/v1/analytics/**").authenticated();
        return http.build();
    }
}
