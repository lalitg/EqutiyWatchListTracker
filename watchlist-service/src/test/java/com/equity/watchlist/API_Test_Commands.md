# Watchlist Service - API Test Commands

Base URL: `http://localhost:8080/api/v1/watchlist`

## 1. Add Company to Watchlist

```bash
curl -X POST http://localhost:8080/api/v1/watchlist/addCompany \
  -H "Content-Type: application/json" \
  -d '{
    "companyCode": "TCS",
    "week52Low": 3200.00,
    "week52High": 4200.00,
    "allTimeLow": 1500.00,
    "allTimeHigh": 4200.00,
    "currentValue": 3950.00,
    "trendSentiment": "Bullish",
    "peRatio": 30.2,
    "eps": 120.50
  }'
```

Expected: `201 Created`

## 2. Get All Companies in Watchlist

```bash
curl http://localhost:8080/api/v1/watchlist/getAllCompanies
```

Expected: `200 OK` with list of watchlist entries

## 3. Update a Company in Watchlist

```bash
curl -X PUT http://localhost:8080/api/v1/watchlist/updateCompany/TCS \
  -H "Content-Type: application/json" \
  -d '{
    "companyCode": "TCS",
    "currentValue": 4000.00,
    "trendSentiment": "Strong Bullish",
    "peRatio": 31.5,
    "eps": 125.00
  }'
```

Expected: `200 OK` with updated entry

## 4. Get Watchlist Count

```bash
curl http://localhost:8080/api/v1/watchlist/getCount
```

Expected: `200 OK` with `{"count": N}`

## 5. Delete a Company from Watchlist

```bash
curl -X DELETE http://localhost:8080/api/v1/watchlist/deleteCompany/TCS
```

Expected: `204 No Content`
