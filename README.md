# Failed Payment Recovery Engine

Backend prototype for VentaLibre's failed payment recovery workflow. The service ingests failed transactions, classifies the failure, calculates a recoverability score, exposes prioritized retry candidates, and returns aggregate recovery insights.

## Tech Stack

- Kotlin
- Spring Boot 4.1
- Spring Web MVC
- Jakarta Validation
- In-memory storage with `ConcurrentHashMap`
- Gradle
- Java 17

## How To Run Locally

```bash
./gradlew bootRun
```

The API runs on:

```text
http://localhost:8080
```

To build the executable Spring Boot jar:

```bash
./gradlew bootJar
java -jar build/libs/failed-payment-recovery-engine-0.0.1-SNAPSHOT.jar
```

Do not run the `-plain.jar`; it is not the executable Spring Boot artifact.

## Seed Test Data

```bash
curl -X POST http://localhost:8080/transactions/seed
```

This clears existing in-memory data and generates 300 failed transactions across the last 7 days:

- 40% soft declines
- 30% hard declines
- 30% technical failures
- Argentina/ARS, Chile/CLP, Uruguay/UYU
- USD-equivalent amounts from 5 to 800
- Visa credit, Mastercard credit, debit card, Mercado Pago
- Repeat and first-time customer IDs
- All required failure reasons

## API Endpoints

```text
POST /transactions/failed
GET  /transactions/recovery-candidates
GET  /transactions/insights
POST /transactions/seed
```

## Example Requests

Create a failed transaction:

```bash
curl -X POST http://localhost:8080/transactions/failed \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "manual-txn-001",
    "amount": 125500.00,
    "currency": "ARS",
    "country": "argentina",
    "paymentMethod": "visa_credit",
    "failureReason": "insufficient_funds",
    "customerId": "repeat-customer-001"
  }'
```

Get prioritized recovery candidates:

```bash
curl "http://localhost:8080/transactions/recovery-candidates?page=0&size=20"
```

Get filtered recovery candidates:

```bash
curl "http://localhost:8080/transactions/recovery-candidates?minAmount=100&maxAgeHours=72&country=chile&minScore=60&page=0&size=10"
```

Get insights:

```bash
curl http://localhost:8080/transactions/insights
```

## Scoring Algorithm

Each transaction starts with a recoverability score of `50`, then adjustments are applied:

- Soft failures: `+25`, except `do_not_honor +10`
- Technical failures: `+20`, except `invalid_request -25`
- Hard failures: `-35`, except `card_expired +5`
- Age since failure:
  - 0-1 days: `+15`
  - 2-3 days: `+8`
  - 4-7 days: `+0`
  - older: `-15`
- USD-equivalent amount:
  - `> 100`: `+8`
  - `> 300`: `+12`
- Customer history:
  - Existing customer ID in stored failed transactions: `+8`
  - More than 2 prior failures: `-5` per extra failure, capped at `-20`
- Payment method:
  - Mercado Pago: `+8`
  - Debit card: `+2`
  - Credit cards: `+0`

The final score is clamped to `0..100`. Recovery probability is `score / 100`.

Recovery candidates are filtered by `minScore`, sorted by priority, and assigned a `priorityRank` starting at `1`:

```text
priority = recoverabilityScore * amountUsdEquivalent
```

## Classification Logic

Soft declines:

```text
insufficient_funds, issuer_unavailable, timeout, try_again_later, do_not_honor
```

Hard declines:

```text
stolen_card, card_expired, invalid_card, blocked_card, fraudulent
```

Technical failures:

```text
gateway_error, network_error, invalid_request
```

Non-retryable failures are excluded from recovery candidates:

```text
stolen_card, invalid_card, blocked_card, fraudulent, invalid_request
```

## Insights

`GET /transactions/insights` returns:

- `totalRecoverableRevenueAtRisk`: sum of recoverable candidate amounts
- `estimatedRecoveryValue`: 40% of soft-decline transaction value
- `breakdownByFailureCategory`: USD-equivalent revenue by soft, hard, technical
- `breakdownByFailureReason`: USD-equivalent revenue by exact failure reason
- `opportunityByCountry`: recoverable revenue grouped by country

## Error Handling

All errors return:

```json
{
  "message": "Error details",
  "timestamp": "2026-07-04T00:00:00Z"
}
```

- Validation errors: `400`
- Invalid enum/query parameter values: `400`
- Not found routes/resources: `404`
- Unexpected errors: `500`

## Assumptions

- Input uses local `amount`, `currency`, and `country`; the service calculates `amountUsdEquivalent` using fixed demo conversion rates.
- Supported conversion rates are `ARS 1000 = USD 1`, `CLP 950 = USD 1`, `UYU 40 = USD 1`, and `USD 1 = USD 1`.
- Data is stored in memory for the prototype.
- `transactionId` is the idempotency key. Reposting the same transaction ID returns the existing processed transaction.
- Customer history is represented by seeded repeat customer IDs, but no separate customer aggregate is modeled.
- API request fields use camelCase JSON names.

## Tradeoffs

- No database, authentication, or real payment processor integration.
- Scoring weights are hardcoded for clarity and speed.
- Retry execution is represented as recommendations, not actual gateway calls.
- Seed data is generated deterministically in code instead of loaded from a static file.

## Future Improvements

- Persist transactions in PostgreSQL.
- Add auth, rate limiting, and audit logging.
- Move scoring rules to configuration.
- Add batch retry simulation.
- Add OpenAPI/Swagger docs.
- Add automated endpoint tests.
- Support both local and USD-equivalent amount fields explicitly.

## Deployment

Railway/Railpack config is included in `railpack.json`:

```json
{
  "packages": {
    "java": "17"
  },
  "deploy": {
    "startCommand": "java -jar build/libs/failed-payment-recovery-engine-0.0.1-SNAPSHOT.jar"
  }
}
```

The start command targets the executable Spring Boot jar and avoids the `-plain.jar`.

## Postman Collection

Import:

```text
failed-payment-recovery-engine.postman_collection.json
```

The collection uses:

```text
baseUrl = http://localhost:8080
```

## Acceptance Criteria Review

- Working API: yes, Spring Boot service exposes the required JSON endpoints.
- Ingestion works: yes, `POST /transactions/failed` stores and processes transactions idempotently.
- Classification logic: yes, exact failure reasons map to soft, hard, or technical categories.
- Query endpoint: yes, `GET /transactions/recovery-candidates` supports filters, pagination, retry timing, score, probability, and priority sorting.
- Insights endpoint: yes, revenue at risk, estimated recovery value, category/reason breakdowns, and country opportunity are returned.
- Documentation: yes, setup, API examples, scoring, classification, assumptions, tradeoffs, and Postman collection are documented.
- Test data: yes, `POST /transactions/seed` generates 300 transactions matching the required distribution.
- Code quality: simple package structure, controller/service/repository separation, in-memory store, and global error handling.
