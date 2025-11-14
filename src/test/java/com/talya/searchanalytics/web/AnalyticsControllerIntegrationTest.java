package com.talya.searchanalytics.web;

import com.talya.searchanalytics.model.Shop;
import com.talya.searchanalytics.repo.ShopRepository;
import com.talya.searchanalytics.service.JwtService;
import com.talya.searchanalytics.web.dto.AnalyticsSummaryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        })
public class AnalyticsControllerIntegrationTest {

    @LocalServerPort
    private int port;
    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private ShopRepository shopRepo;
    @Autowired
    private JwtService jwtService;

    @BeforeEach
    void setup() {
        shopRepo.deleteAll();
        shopRepo.save(Shop.builder().shopDomain("shop1").passwordHash(new BCryptPasswordEncoder(10).encode("Password!123"))
                .role(Shop.Role.SHOP).status(Shop.Status.ACTIVE).build());
    }

    @Test
    public void summaryEndpointResponds() {
        String token = jwtService.generateToken("shop1", Shop.Role.SHOP);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        String url = "http://localhost:" + port + "/api/v1/analytics/summary?fromMs=0&toMs=1";
        ResponseEntity<AnalyticsSummaryResponse> resp = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, new HttpEntity<>(headers), AnalyticsSummaryResponse.class);
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody()).isNotNull();
    }
}
