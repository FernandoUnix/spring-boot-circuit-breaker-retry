# 📊 Monitoring Guide - Order Service

Complete guide on how to use the monitoring stack with Prometheus and Grafana.

## 🚀 Quick Start

### Step 1: Start Your Application

```bash
# Build and run your Spring Boot application
mvn clean spring-boot:run
```

Your application will start on **http://localhost:8080**

### Step 2: Start Monitoring Stack

**Option A: Using PowerShell Script (Easiest)**
```powershell
.\start-monitoring.ps1
```

**Option B: Using Docker Compose**
```powershell
# Try new syntax first
docker compose up -d

# Or old syntax
docker-compose up -d
```

**Option C: Manual Docker Commands**
If docker-compose is not available, see `DOCKER_SETUP.md` for manual commands.

This starts:
- **Prometheus** on http://localhost:9090
- **Grafana** on http://localhost:3000

### Step 3: Verify Everything is Working

**Check your application metrics:**
```bash
curl http://localhost:8080/actuator/prometheus
```

You should see metrics like:
```
order_processed_total{application="order-service"} 0.0
order_successful_total{application="order-service"} 0.0
http_server_requests_seconds_count{method="GET",uri="/orders",status="200"} 0.0
resilience4j_circuitbreaker_state{name="order-service",state="closed"} 1.0
```

**Check Prometheus targets:**
- Open http://localhost:9090/targets
- You should see `order-service` target as **UP**

**Check Grafana:**
- Open http://localhost:3000
- Login: `admin` / `admin`
- Go to Dashboards → "Order Service - Resilience4j & Application Metrics"

## 📈 Using Prometheus

### Access Prometheus UI
- **URL**: http://localhost:9090

### Useful Queries

**1. Request Rate (requests per second)**
```promql
rate(http_server_requests_seconds_count[5m])
```

**2. Success Rate**
```promql
rate(order_successful_total[5m]) / rate(order_processed_total[5m])
```

**3. Failure Rate by Reason**
```promql
rate(order_failed_total[5m]) by (reason)
```

**4. Circuit Breaker State**
```promql
resilience4j_circuitbreaker_state{name="order-service"}
```

**5. Average Processing Time**
```promql
rate(order_processing_duration_seconds_sum[5m]) / rate(order_processing_duration_seconds_count[5m])
```

**6. P95 Latency**
```promql
http_server_requests_seconds{quantile="0.95"}
```

**7. Retry Rate**
```promql
rate(resilience4j_retry_calls_total{kind="successful_with_retry"}[5m]) / rate(resilience4j_retry_calls_total[5m])
```

**8. Orders by Postal Code (Top 10)**
```promql
topk(10, rate(order_by_postal_code_total[5m]) by (postal_code))
```

### Viewing Alerts

1. Go to http://localhost:9090/alerts
2. You'll see all configured alerts
3. Alerts will show as:
   - **Inactive** (green) - Condition not met
   - **Pending** (yellow) - Condition met, waiting for duration
   - **Firing** (red) - Alert is active

## 📊 Using Grafana

### Access Grafana
- **URL**: http://localhost:3000
- **Username**: `admin`
- **Password**: `admin` (change on first login)

### Pre-configured Dashboard

1. Click **Dashboards** (left menu)
2. Select **Order Service - Resilience4j & Application Metrics**
3. You'll see:
   - HTTP Request Rate
   - HTTP Latency (p95)
   - Circuit Breaker State
   - Circuit Breaker Calls
   - Retry Statistics
   - Bulkhead Metrics
   - Rate Limiter Metrics
   - JVM Memory

### Creating Custom Queries

1. Click **+** → **Create** → **Dashboard**
2. Click **Add visualization**
3. Select **Prometheus** as data source
4. Enter your PromQL query
5. Example query:
   ```promql
   rate(order_processed_total[5m])
   ```

### Setting Up Alerts in Grafana

1. Create a panel with your metric
2. Click **Alert** tab
3. Configure:
   - **Condition**: When `avg()` of `query(A, 5m, now)` is `above` `threshold`
   - **Evaluate every**: `1m`
   - **For**: `5m`
4. Add notification channel (email, Slack, etc.)

## 🔍 Available Metrics

### Custom Business Metrics

| Metric | Description | Tags |
|--------|-------------|------|
| `order_processed_total` | Total orders processed | `application` |
| `order_successful_total` | Successful orders | `application` |
| `order_failed_total` | Failed orders | `application`, `reason` |
| `order_by_postal_code_total` | Orders by postal code | `application`, `postal_code` |
| `order_processing_duration_seconds` | Processing time | `application` (with percentiles) |

### Resilience4j Metrics

| Metric | Description |
|--------|-------------|
| `resilience4j_circuitbreaker_state` | Circuit breaker state (0=CLOSED, 1=OPEN, 2=HALF_OPEN) |
| `resilience4j_circuitbreaker_calls_total` | Circuit breaker calls by kind and state |
| `resilience4j_retry_calls_total` | Retry calls by kind |
| `resilience4j_bulkhead_available_concurrent_calls` | Available bulkhead slots |
| `resilience4j_ratelimiter_calls_total` | Rate limiter calls by kind |

### HTTP Metrics

| Metric | Description |
|--------|-------------|
| `http_server_requests_seconds_count` | Total HTTP requests |
| `http_server_requests_seconds_sum` | Total request duration |
| `http_server_requests_seconds{quantile="0.95"}` | Latency percentiles |

### JVM Metrics

| Metric | Description |
|--------|-------------|
| `jvm_memory_used_bytes` | Memory usage by area |
| `jvm_threads_live_threads` | Live threads |
| `jvm_gc_pause_seconds` | GC pause time |

## 🧪 Testing the Metrics

### Generate Some Traffic

```bash
# Make some successful requests
curl "http://localhost:8080/orders?orderNumber=ORDER-1"

# Check metrics
curl http://localhost:8080/actuator/prometheus | grep order_processed_total
```

### Test Failure Scenarios

**1. Test Retry Exhausted:**
```bash
# Enable chaos fault injector (connection error)
# Then make a request - you'll see RETRY_EXHAUSTED failures
curl "http://localhost:8080/orders?orderNumber=ORDER-1"
```

**2. Test Circuit Breaker:**
- Make multiple failing requests
- Circuit breaker will open
- Check metrics: `resilience4j_circuitbreaker_state{state="open"}`

**3. Test Rate Limiter:**
- Make many rapid requests
- Rate limiter will reject some
- Check: `resilience4j_ratelimiter_calls_total{kind="not_permitted"}`

## 📱 Monitoring in Action

### Real-time Monitoring

1. **Start your app and monitoring stack**
2. **Open Grafana dashboard** (http://localhost:3000)
3. **Generate traffic** to your API
4. **Watch metrics update in real-time** (refresh every 10s)

### Example Workflow

```bash
# Terminal 1: Start application
mvn spring-boot:run

# Terminal 2: Start monitoring
docker-compose up -d

# Terminal 3: Generate traffic
for i in {1..100}; do
  curl "http://localhost:8080/orders?orderNumber=ORDER-1"
  sleep 0.5
done

# Now check Grafana dashboard to see:
# - Request rate increasing
# - Processing time metrics
# - Success/failure counts
```

## 🎯 Key Use Cases

### 1. Monitor Service Health
- Check `/actuator/health` endpoint
- View circuit breaker state in Grafana
- Monitor error rates

### 2. Performance Analysis
- Track p95/p99 latencies
- Identify slow endpoints
- Monitor processing duration

### 3. Capacity Planning
- Track request rates
- Monitor bulkhead usage
- Analyze rate limiter rejections

### 4. Troubleshooting
- Check failure reasons: `order_failed_total by (reason)`
- Analyze retry patterns
- Monitor circuit breaker transitions

### 5. Business Metrics
- Orders processed per hour/day
- Success rate trends
- Popular postal codes

## 🔧 Troubleshooting

### Prometheus can't scrape metrics

**Problem**: Target shows as DOWN in http://localhost:9090/targets

**Solutions**:
1. Verify app is running: `curl http://localhost:8080/actuator/health`
2. Check endpoint: `curl http://localhost:8080/actuator/prometheus`
3. For Windows/Mac Docker: Use `host.docker.internal:8080` in prometheus.yml
4. For Linux: Use `172.17.0.1:8080` or your host IP

### Grafana shows "No Data"

**Solutions**:
1. Check Prometheus datasource: http://localhost:3000/connections/datasources
2. Test query in Prometheus first: http://localhost:9090/graph
3. Verify time range in Grafana (top right)
4. Check if metrics exist: `curl http://localhost:8080/actuator/metrics`

### Metrics not appearing

**Solutions**:
1. Ensure endpoint is exposed: Check `application.yaml` has `include: prometheus`
2. Restart application after adding metrics
3. Check application logs for Micrometer errors
4. Verify dependency is in `pom.xml`

## 📚 Next Steps

1. **Customize Alerts**: Edit `monitoring/prometheus/alerts/circuit-breaker-alerts.yml`
2. **Create Custom Dashboards**: Add JSON files to `monitoring/grafana/dashboards/`
3. **Add More Metrics**: Use `OrderMetrics` class to add business-specific metrics
4. **Set Up Notifications**: Configure email/Slack in Grafana for alerts
5. **Production Setup**: Use proper authentication and secure endpoints

## 🛑 Stopping Everything

```bash
# Stop monitoring stack
docker-compose down

# Stop application
# Press Ctrl+C in the terminal running the app
```

To remove all data:
```bash
docker-compose down -v
```

---

**Happy Monitoring! 📊**

