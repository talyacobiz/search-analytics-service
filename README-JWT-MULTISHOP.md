# Multi-Shop JWT Security Summary

## JWT Algorithm
Using HS256 with secret configured via `security.jwt.secret` (default placeholder). Claims:
```
{
  "sub": "example-store.myshopify.com",
  "shop": "example-store.myshopify.com",
  "role": "SHOP",
  "iat": 1731170000,
  "exp": 1731173600
}
```

## Endpoints
- POST /api/v1/auth/login
- GET /api/v1/shops (OWNER only)
- POST /api/v1/shops (OWNER only, returns generatedPassword once)
- POST /api/v1/shops/{domain}/rotate (OWNER only)
- PUT  /api/v1/shops/{domain}/status {"status":"ACTIVE|DISABLED"}
- DELETE /api/v1/shops/{domain} (marks DISABLED)
- GET /api/v1/analytics/summary?fromMs=...&toMs=...
- GET /api/v1/analytics/full?fromMs=...&toMs=...

## Sample Flow
1. Owner creates a shop:
```
curl -X POST https://host/api/v1/shops -H "Authorization: Bearer <OWNER_TOKEN>" -d '{"shopDomain":"new-shop.myshopify.com"}'
```
Response includes generatedPassword.
2. Shop logs in:
```
curl -X POST https://host/api/v1/auth/login -H 'Content-Type: application/json' -d '{"username":"new-shop.myshopify.com","password":"<generatedPassword>"}'
```
Get `accessToken`.
3. Fetch analytics:
```
curl -H "Authorization: Bearer <SHOP_TOKEN>" "https://host/api/v1/analytics/summary?fromMs=1731000000000&toMs=1731086400000"
```

## Example Empty Analytics Response
```
{
  "totalSearches":0,
  "totalAddToCart":0,
  "totalPurchases":0,
  "totalRevenue":0.0,
  "conversionRate":0.0,
  "searchesChangePercent":0.0,
  "addToCartChangePercent":0.0,
  "purchasesChangePercent":0.0,
  "revenueChangePercent":0.0,
  "conversionRateChangePercent":0.0,
  "lastPeriod":null,
  "totalAddToCartAmount":0.0,
  "prevAddToCartAmount":0.0,
  "addToCartAmountChangePercent":0.0,
  "currency":"NIS",
  "timeSeries":[],
  "topQueries":[],
  "insights":[]
}
```

## Error Examples
401: `{"error":"MISSING_TOKEN"}`
403 shop mismatch: `{"error":"SHOP_ID_MISMATCH"}`
403 disabled shop: `{"error":"SHOP_DISABLED"}`
400 invalid range: `{"error":"INVALID_RANGE"}`

## Notes
- All analytics scoped server-side by `shopId` from JWT principal.
- Legacy `shopId` query param optional; if present and mismatched returns 403.
- Disable a shop sets status=DISABLED; future analytics calls return 403.

