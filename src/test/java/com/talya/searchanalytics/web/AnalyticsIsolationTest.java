package com.talya.searchanalytics.web;

import com.talya.searchanalytics.model.SearchEvent;
import com.talya.searchanalytics.model.Shop;
import com.talya.searchanalytics.repo.SearchEventRepository;
import com.talya.searchanalytics.repo.ShopRepository;
import com.talya.searchanalytics.service.JwtService;
import com.talya.searchanalytics.web.dto.AnalyticsSummaryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        })
public class AnalyticsIsolationTest {

    @LocalServerPort
    int port;
    @Autowired
    TestRestTemplate rest;
    @Autowired
    ShopRepository shopRepo;
    @Autowired
    SearchEventRepository searchRepo;
    @Autowired
    JwtService jwtService;

    BCryptPasswordEncoder enc = new BCryptPasswordEncoder(10);

    @BeforeEach
    void setup() {
        shopRepo.deleteAll();
        searchRepo.deleteAll();
        shopRepo.save(Shop.builder().shopDomain("shopA").passwordHash(enc.encode("passA!123$"))
                .role(Shop.Role.SHOP).status(Shop.Status.ACTIVE).build());
        shopRepo.save(Shop.builder().shopDomain("shopB").passwordHash(enc.encode("passB!123$"))
                .role(Shop.Role.SHOP).status(Shop.Status.ACTIVE).build());
        long now = Instant.now().toEpochMilli();
        searchRepo.save(SearchEvent.builder().shopId("shopA").searchId("s1").sessionId("sess1").query("hat").productIds(java.util.List.of()).timestampMs(now).build());
        searchRepo.save(SearchEvent.builder().shopId("shopB").searchId("s2").sessionId("sess2").query("shoes").productIds(java.util.List.of()).timestampMs(now).build());
    }

    @Test
    void analyticsSummaryReturnsOnlyOwnShopData() {
        String tokenA = jwtService.generateToken("shopA", Shop.Role.SHOP);
        HttpHeaders h = new HttpHeaders(); h.setBearerAuth(tokenA);
        String url = "http://localhost:" + port + "/api/v1/analytics/summary?fromMs=0&toMs=" + System.currentTimeMillis();
        ResponseEntity<AnalyticsSummaryResponse> resp = rest.exchange(url, HttpMethod.GET, new HttpEntity<>(h), AnalyticsSummaryResponse.class);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody().getTotalSearches()).isEqualTo(1);
    }

    @Test
    void analyticsRequiresToken() {
        String url = "http://localhost:" + port + "/api/v1/analytics/summary?fromMs=0&toMs=" + System.currentTimeMillis();
        ResponseEntity<String> resp = rest.getForEntity(url, String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(401); // should be unauthorized
    }
}
