# Spring Boot Resilience Patterns with Observability

> **Note:** This project is for **study purposes** only. It demonstrates comprehensive resilience patterns, metrics, and monitoring in Spring Boot microservices.

## Overview

A complete Spring Boot microservices example implementing **4 resilience patterns** together with full observability stack:

1. **Order Service** (Port `8080`)
   - **Resilience Patterns**: Rate Limiter, Bulkhead, Retry, Circuit Breaker
   - **Observability**: Micrometer, Prometheus, Grafana
   - **Chaos Engineering**: Fault injection capabilities
   - Calls Address Service to fetch shipping addresses

2. **Address Service** (Port `9093`)
   - Provides address information by postal code
   - Simulates external dependency failures

## Features

### Resilience Patterns (All Combined)
- **Rate Limiter**: 10 requests per 10s window
- **Bulkhead**: Max 2 concurrent calls (semaphore-based)
- **Retry**: 3 attempts with exponential backoff (1s → 2s → 4s)
- **Circuit Breaker**: TIME_BASED sliding window, opens at 50% failure rate

### Observability Stack
- **Micrometer**: Custom business metrics (order processing, success/failure rates, duration)
- **Prometheus**: Metrics scraping with alerting rules
- **Grafana**: Pre-configured dashboards for visualization
- **Spring Actuator**: Health checks, metrics, and Prometheus endpoints

### Chaos Engineering
- Configurable fault injection (timeout, connection errors, HTTP 500, latency)
- Feature-flag driven chaos testing

## Architecture

```
Client
  ↓
Order Service (Port 8080)
  ├─ @RateLimiter (outermost)
  ├─ @Bulkhead
  ├─ @Retry
  ├─ @CircuitBreaker (innermost)
  ├─ Micrometer Metrics
  └─ Chaos Fault Injector
  ↓
Address Service (Port 9093)

Monitoring Stack:
  ├─ Prometheus (Port 9090)
  └─ Grafana (Port 3000)
```

## Getting Started

### Prerequisites
- Java 17+
- Maven 3.6+
- Docker & Docker Compose

### Running the Application

1. **Start Address Service:**
```bash
cd address-service
./mvnw spring-boot:run
```

2. **Start Order Service:**
```bash
cd order-service
./mvnw spring-boot:run
```

3. **Start Monitoring Stack:**
```bash
cd order-service
docker-compose up -d
```

**Access Points:**
- Order Service: `http://localhost:8080`
- Address Service: `http://localhost:9093`
- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000` (admin/admin)

### Testing

```bash
curl "http://localhost:8080/orders?orderNumber=0c70c0c2"
```

**Success Response:**
```json
{
  "orderNumber": "0c70c0c2",
  "postalCode": "1000001",
  "shippingCity": "Tokyo",
  "shippingState": "Tokyo",
  "orderDate": "2024-01-15"
}
```

**Failure Responses:**
- Circuit OPEN: `{"msg": "...", "reason": "CIRCUIT_OPEN"}`
- Retry Exhausted: `{"msg": "...", "reason": "RETRY_EXHAUSTED"}`
- Rate Limited: `{"msg": "...", "reason": "RATE_LIMIT"}`
- Bulkhead Full: `{"msg": "...", "reason": "BULKHEAD_FULL"}`

## Configuration

### Resilience4j Patterns

**Rate Limiter:**
- `limit-for-period: 10` requests per `10s`
- `timeout-duration: 0` (fail-fast)

**Bulkhead:**
- `max-concurrent-calls: 2`
- `max-wait-duration: 0` (fail-fast)

**Retry:**
- `max-attempts: 3` (1 initial + 2 retries)
- Exponential backoff: 1s → 2s → 4s (max 5s)
- Retries: `ResourceAccessException`, `HttpServerErrorException`, `ConnectException`, `TimeoutException`

**Circuit Breaker:**
- `sliding-window-type: TIME_BASED` (30 seconds)
- `failure-rate-threshold: 50%`
- `minimum-number-of-calls: 5`
- `wait-duration-in-open-state: 5s`
- `permitted-number-of-calls-in-half-open-state: 3`

### Custom Metrics (Micrometer)

- `order.processed.total` - Total orders processed
- `order.successful.total` - Successful orders
- `order.failed.total` - Failed orders (tagged by reason: CIRCUIT_OPEN, RETRY_EXHAUSTED, RATE_LIMIT, BULKHEAD_FULL, ORDER_NOT_FOUND)
- `order.processing.duration` - Processing time (p50, p95, p99 percentiles)
- `order.by.postal.code` - Orders by postal code

### Chaos Engineering

Configure in `application.yaml`:
```yaml
fault:
  enabled: true
  timeout: false
  connection-error: false
  http500: false
  latency: false
  latencyMS: 0
```

## Monitoring

### Actuator Endpoints
- Health: `http://localhost:8080/actuator/health`
- Metrics: `http://localhost:8080/actuator/metrics`
- Prometheus: `http://localhost:8080/actuator/prometheus`

### Grafana Dashboard
Pre-configured dashboard (`order-service-dashboard.json`) includes:
- Circuit Breaker state and metrics
- Retry statistics (successful, failed, exhausted)
- Rate Limiter metrics
- Bulkhead metrics
- Custom order processing metrics
- HTTP request metrics
- Failure rates by reason

### Prometheus Alerts
Configured alert rules (`circuit-breaker-alerts.yml`):
- **CircuitBreakerOpen**: Circuit OPEN for >1 minute
- **CircuitBreakerHighFailureRate**: Failure rate >50% for >2 minutes
- **HighRetryRate**: Retry rate >30% for >5 minutes
- **RetryExhausted**: Retries exhausted
- **HighLatency**: p95 latency >1s for >5 minutes
- **HighErrorRate**: HTTP error rate >10% for >2 minutes
- **BulkheadFull**: All concurrent slots occupied for >1 minute
- **RateLimitExceeded**: Rate limit exceeded

## Implementation Details

### Annotation Order (Critical)

```java
@RateLimiter(name = SERVICE_NAME, fallbackMethod = "rateLimitFallback")
@Bulkhead(name = SERVICE_NAME, type = Bulkhead.Type.SEMAPHORE, fallbackMethod = "bulkheadFallback")
@Retry(name = SERVICE_NAME, fallbackMethod = "retryFallbackMethod")
@CircuitBreaker(name = SERVICE_NAME)
public Type getOrderByPostCode(String orderNumber) { ... }
```

**Execution Flow:**
1. Rate Limiter checks request rate (outermost)
2. Bulkhead checks concurrent capacity
3. Retry handles transient failures
4. Circuit Breaker prevents cascading failures (innermost)
5. Method execution

**Why this order?**
- Rate Limiter and Bulkhead protect at the entry point
- Retry handles transient failures before Circuit Breaker sees them
- Circuit Breaker records final outcomes after retries
- Prevents circuit from opening too aggressively

### Fallback Strategy

Single Retry fallback handles all scenarios:
```java
private Type retryFallbackMethod(String orderNumber, Exception e) {
    if (e instanceof CallNotPermittedException) {
        // Circuit is OPEN
        return new Failure("Circuit breaker is OPEN", "CIRCUIT_OPEN", true);
    }
    // Other failures after retries exhausted
    return new Failure("Service failed after retries", "RETRY_EXHAUSTED", false);
}
```

Separate fallbacks for Rate Limiter and Bulkhead provide specific error messages.

## Testing Scenarios

1. **Normal Operation**: Both services running → Success
2. **Transient Failure**: Stop Address Service briefly → Retry succeeds
3. **Service Down**: Stop Address Service, make 5+ requests → Circuit opens
4. **Recovery**: Wait 5s after circuit opens, start Address Service → Circuit closes
5. **Rate Limiting**: Make 11+ requests in 10s → Rate limit fallback
6. **Bulkhead Full**: Make 3+ concurrent requests → Bulkhead fallback

## Project Structure

```
order-service/
├── monitoring/
│   ├── prometheus/
│   │   ├── prometheus.yml
│   │   └── alerts/
│   │       └── circuit-breaker-alerts.yml
│   └── grafana/
│       ├── dashboards/
│       │   └── order-service-dashboard.json
│       └── provisioning/
│           ├── datasources/prometheus.yml
│           └── dashboards/dashboard.yml
├── docker-compose.yml
└── src/main/java/
    ├── chaos/
    │   └── ChaosFaultInjector.java
    ├── config/
    │   ├── MetricsConfig.java
    │   └── OrderMetrics.java
    └── service/impl/
        └── OrderServiceImpl.java
```

## Technologies

- **Spring Boot 3.0.5**
- **Resilience4j** (Circuit Breaker, Retry, Rate Limiter, Bulkhead)
- **Micrometer** (Metrics)
- **Prometheus** (Metrics collection)
- **Grafana** (Visualization)
- **Spring Actuator** (Health & Metrics)
- **H2 Database** (In-memory)
- **Lombok**

## References

- [Resilience4j Documentation](https://resilience4j.readme.io/)
- [Circuit Breaker Pattern](https://martinfowler.com/bliki/CircuitBreaker.html)
- [Micrometer Documentation](https://micrometer.io/)
- [Prometheus Documentation](https://prometheus.io/docs/)
- [Grafana Documentation](https://grafana.com/docs/)

---

**Built with Spring Boot, Resilience4j, Micrometer, Prometheus & Grafana**
