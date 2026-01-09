package com.talya.searchanalytics.service;

import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;
import java.util.HashMap;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;

@Service
public class CurrencyService {
    
    private static final Logger log = LoggerFactory.getLogger(CurrencyService.class);
    private final Map<String, Double> rateCache = new HashMap<>();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private LocalDate lastUpdate = null;
    
    public CurrencyService() {
        // Don't set lastUpdate here, so it will fetch live rates on first use
        loadFallbackRates();
        lastUpdate = null; // Force live rate fetch on first call
    }
    
    public double convertToEur(double amount, String fromCurrency) {
        if ("EUR".equalsIgnoreCase(fromCurrency)) {
            return amount;
        }
        
        double rate = getExchangeRate(fromCurrency);
        return amount / rate;
    }
    
    public double getExchangeRate(String currency) {
        if ("EUR".equalsIgnoreCase(currency)) {
            return 1.0;
        }
        
        // Refresh rates daily
        if (lastUpdate == null || !lastUpdate.equals(LocalDate.now())) {
            refreshRates();
        }
        
        return rateCache.getOrDefault(currency.toUpperCase(), getStaticRate(currency));
    }
    
    private void refreshRates() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.exchangerate-api.com/v4/latest/EUR"))
                .GET()
                .build();
                
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                Map<String, Object> data = objectMapper.readValue(response.body(), Map.class);
                Map<String, Double> rates = (Map<String, Double>) data.get("rates");
                
                rateCache.clear();
                rateCache.putAll(rates);
                lastUpdate = LocalDate.now();
                log.info("Currency rates updated successfully from API");
            } else {
                log.warn("Failed to fetch rates, status: {}", response.statusCode());
                loadFallbackRates();
            }
        } catch (Exception e) {
            log.warn("Failed to fetch live exchange rates: {}", e.getMessage());
            loadFallbackRates();
        }
    }
    
    private void loadFallbackRates() {
        rateCache.put("USD", 1.1);
        rateCache.put("GBP", 0.85);
        rateCache.put("NIS", 4.0);
        rateCache.put("ILS", 4.0);
        rateCache.put("EUR", 1.0);
        // Don't set lastUpdate here - let it remain null to trigger API fetch
        log.info("Loaded fallback currency rates");
    }
    
    private double getStaticRate(String currency) {
        switch (currency.toUpperCase()) {
            case "USD": return 1.1;
            case "GBP": return 0.85;
            case "NIS":
            case "ILS": return 4.0;
            default: return 1.0;
        }
    }
}