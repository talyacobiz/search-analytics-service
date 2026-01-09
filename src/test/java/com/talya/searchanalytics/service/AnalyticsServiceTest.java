package com.talya.searchanalytics.service;

import com.talya.searchanalytics.model.*;
import com.talya.searchanalytics.repo.*;
import com.talya.searchanalytics.web.dto.AnalyticsFullResponse;
import com.talya.searchanalytics.web.dto.AnalyticsSummaryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

public class AnalyticsServiceTest {

    @Mock
    private SearchEventRepository searchRepo;
    @Mock
    private AddToCartEventRepository cartRepo;
    @Mock
    private PurchaseEventRepository purchaseRepo;
    @Mock
    private ProductClickEventRepository clickRepo;
    @Mock
    private BuyNowClickEventRepository buyNowRepo;
    @Mock
    private CurrencyService currencyService;

    private AnalyticsService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new AnalyticsService(searchRepo, cartRepo, purchaseRepo, clickRepo, buyNowRepo, currencyService);
    }

    @Test
    void testSummaryAndFull() {
        // Mock for summary
        when(searchRepo.countByShopIdAndTimestampMsBetween(anyString(), anyLong(), anyLong())).thenReturn(10L);
        when(cartRepo.countByShopIdAndTimestampMsBetween(anyString(), anyLong(), anyLong())).thenReturn(2L);
        when(purchaseRepo.countByShopIdAndTimestampMsBetween(anyString(), anyLong(), anyLong())).thenReturn(1L);
        when(purchaseRepo.sumTotalAmountByShopIdAndTimestampMsBetween(anyString(), anyLong(), anyLong())).thenReturn(100.0);
        when(searchRepo.topQueries(anyString(), anyLong(), anyLong())).thenReturn(Collections.emptyList());
        // Mock currency service
        when(currencyService.getExchangeRate(anyString())).thenReturn(1.0);
        when(currencyService.convertToEur(anyDouble(), anyString())).thenReturn(100.0);

        // Mock for filtering logic: search event and add-to-cart event with matching sessionId/productId
        SearchEvent searchEvent = new SearchEvent();
        searchEvent.setSessionId("sess1");
        searchEvent.setProductIds(List.of("prod1"));
        
        AddToCartEvent cartEvent = new AddToCartEvent();
        cartEvent.setSessionId("sess1");
        cartEvent.setProductId("prod1");
        cartEvent.setPrice(50.0);
        cartEvent.setCurrency("NIS");
        
        PurchaseEvent purchaseEvent = new PurchaseEvent();
        purchaseEvent.setSessionId("sess1");
        Product product = new Product("prod1", "Test Product", 100.0, 1);
        purchaseEvent.setProducts(List.of(product));
        purchaseEvent.setTotalAmount(100.0);
        purchaseEvent.setCurrency("EUR");
        
        when(searchRepo.findAllByShopIdAndTimestampMsBetween(anyString(), anyLong(), anyLong())).thenReturn(List.of(searchEvent));
        when(cartRepo.findAllByShopIdAndTimestampMsBetween(anyString(), anyLong(), anyLong())).thenReturn(List.of(cartEvent));
        when(purchaseRepo.findAllByShopIdAndTimestampMsBetween(anyString(), anyLong(), anyLong())).thenReturn(List.of(purchaseEvent));

        AnalyticsSummaryResponse s = service.summary("shop1", 0L, System.currentTimeMillis());
        assertNotNull(s);
        assertEquals(10L, s.getTotalSearches());
        assertEquals(1L, s.getTotalAddToCart());
        assertEquals(1L, s.getTotalPurchases());
        assertEquals(100.0, s.getTotalRevenue());

        // Mock for full
        when(clickRepo.findAllByShopIdAndTimestampMsBetween(anyString(), anyLong(), anyLong())).thenReturn(List.of(new ProductClickEvent()));
        when(purchaseRepo.findAllByShopIdAndTimestampMsBetween(anyString(), anyLong(), anyLong())).thenReturn(List.of(new PurchaseEvent()));
        when(buyNowRepo.findAllByShopIdAndTimestampMsBetween(anyString(), anyLong(), anyLong())).thenReturn(List.of(new BuyNowClickEvent()));

        AnalyticsFullResponse full = service.full("shop1", 0L, System.currentTimeMillis());
        assertNotNull(full);
        assertEquals(1, full.getSearchEvents().size());
    }

}

