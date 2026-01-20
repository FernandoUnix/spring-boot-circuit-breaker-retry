# Monitoring Setup - Order Service

This directory contains the complete monitoring stack configuration for the Order Service using Prometheus and Grafana.

## Architecture

```
Order Service (Port 8080)
    ↓ (scrapes metrics)
Prometheus (Port 9090)
    ↓ (queries)
Grafana (Port 3000)
```

## Quick Start

### 1. Start Monitoring Stack

```bash
docker-compose up -d
```

This will start:
- **Prometheus** on http://localhost:9090
- **Grafana** on http://localhost:3000 (admin/admin)

### 2. Start Your Application

```bash
mvn spring-boot:run
```

Your application should be running on http://localhost:8080

### 3. Verify Metrics Endpoint

```bash
curl http://localhost:8080/actuator/prometheus
```

## Access Points

### Prometheus
- **URL**: http://localhost:9090
- **Query Examples**:
  - `rate(http_server_requests_seconds_count[5m])` - Request rate
  - `resilience4j_circuitbreaker_state{name="order-service"}` - Circuit breaker state
  - `order_processed_total` - Custom business metrics

### Grafana
- **URL**: http://localhost:3000
- **Username**: admin
- **Password**: admin
- **Dashboard**: "Order Service - Resilience4j & Application Metrics" (auto-provisioned)

## Available Metrics

### Application Metrics (Custom)
- `order.processed.total` - Total orders processed
- `order.successful.total` - Successful orders
- `order.failed.total{reason="..."}` - Failed orders by reason
- `order.by.postal.code{postal_code="..."}` - Orders by postal code
- `order.processing.duration` - Order processing time (with percentiles)

### Resilience4j Metrics
- `resilience4j_circuitbreaker_*` - Circuit breaker metrics
- `resilience4j_retry_*` - Retry metrics
- `resilience4j_bulkhead_*` - Bulkhead metrics
- `resilience4j_ratelimiter_*` - Rate limiter metrics

### HTTP Metrics
- `http_server_requests_seconds_count` - Request count
- `http_server_requests_seconds_sum` - Total request time
- `http_server_requests_seconds{quantile="0.95"}` - Latency percentiles

### JVM Metrics
- `jvm_memory_*` - Memory usage
- `jvm_threads_*` - Thread statistics
- `jvm_gc_*` - Garbage collection metrics

## Alerts

Alerts are configured in `monitoring/prometheus/alerts/circuit-breaker-alerts.yml`:

1. **CircuitBreakerOpen** - Circuit breaker is OPEN
2. **CircuitBreakerHighFailureRate** - Failure rate > 50%
3. **HighRetryRate** - Retry rate > 30%
4. **RetryExhausted** - Retries are being exhausted
5. **HighLatency** - p95 latency > 1s
6. **HighErrorRate** - HTTP error rate > 10%
7. **BulkheadFull** - Bulkhead is at capacity
8. **RateLimitExceeded** - Rate limiter is rejecting requests

## Prometheus Configuration

The Prometheus configuration is in `monitoring/prometheus/prometheus.yml`:

- **Scrape Interval**: 10s for order-service, 15s global
- **Retention**: 30 days
- **Target**: `host.docker.internal:8080` (adjust if needed)

### Adjusting Target Host

If your application runs on a different host, edit `monitoring/prometheus/prometheus.yml`:

```yaml
static_configs:
  - targets: ['your-host:8080']  # Change this
```

For Docker networks, use service name:
```yaml
- targets: ['order-service:8080']
```

## Grafana Dashboards

### Pre-configured Dashboard
- **Order Service - Resilience4j & Application Metrics**
  - HTTP request rate and latency
  - Circuit breaker state and calls
  - Retry statistics
  - Bulkhead and rate limiter metrics
  - JVM memory usage

### Adding Custom Dashboards

1. Create JSON file in `monitoring/grafana/dashboards/`
2. Restart Grafana: `docker-compose restart grafana`
3. Dashboard will be auto-imported

## Useful Prometheus Queries

### Success Rate
```promql
rate(order_successful_total[5m]) / rate(order_processed_total[5m])
```

### Failure Rate by Reason
```promql
rate(order_failed_total[5m]) by (reason)
```

### Circuit Breaker Failure Rate
```promql
rate(resilience4j_circuitbreaker_calls_total{kind="failed"}[5m])
/
rate(resilience4j_circuitbreaker_calls_total[5m])
```

### Average Processing Time
```promql
rate(order_processing_duration_seconds_sum[5m])
/
rate(order_processing_duration_seconds_count[5m])
```

### Orders by Postal Code (Top 10)
```promql
topk(10, rate(order_by_postal_code_total[5m]) by (postal_code))
```

## Troubleshooting

### Prometheus can't scrape metrics
- Check if application is running: `curl http://localhost:8080/actuator/health`
- Verify endpoint: `curl http://localhost:8080/actuator/prometheus`
- Check Prometheus targets: http://localhost:9090/targets
- For Docker: Use `host.docker.internal` or service name

### Grafana shows "No Data"
- Verify Prometheus datasource: http://localhost:3000/connections/datasources
- Check if Prometheus is accessible from Grafana
- Verify metrics exist: http://localhost:9090/graph

### Metrics not appearing
- Ensure `/actuator/prometheus` endpoint is accessible
- Check application logs for Micrometer errors
- Verify `management.endpoints.web.exposure.include=prometheus` in application.yaml

## Stopping the Stack

```bash
docker-compose down
```

To remove volumes (clears all data):
```bash
docker-compose down -v
```

