# Search Analytics Service v2 (Spring Boot)

Java/Spring service to collect events (search, product-click, add-to-cart, purchase, buy-now-click) and expose both summary and full datasets for your dashboard.

## Run
```bash
mvn spring-boot:run
```

## POST events
- `POST /api/v1/events/search`
- `POST /api/v1/events/product-click`
- `POST /api/v1/events/add-to-cart`
- `POST /api/v1/events/purchase`
- `POST /api/v1/events/buy-now-click`

## GET analytics
- `GET /api/v1/analytics/summary?shopId=...&fromMs=...&toMs=...`
- `GET /api/v1/analytics/full?shopId=...&fromMs=...&toMs=...`  ← returns all rows per table within time window

H2 console: `/h2`
Swagger UI: `/swagger-ui.html`

