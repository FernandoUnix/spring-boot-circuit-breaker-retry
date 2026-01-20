# Fix Prometheus Not Showing Data

## Quick Diagnosis

Your application is running and metrics are available at `http://localhost:8080/actuator/prometheus`.

The issue is likely that **Prometheus (running in Docker) can't reach your application** (running on Windows host).

## Solution 1: Check Prometheus Targets

1. Open **http://localhost:9090/targets**
2. Check if `order-service` target shows:
   - **UP** (green) = Working! ✅
   - **DOWN** (red) = Connection issue ❌

## Solution 2: Fix Network Connection (Windows)

The Prometheus config uses `host.docker.internal:8080` which should work on Windows, but let's verify:

### Option A: If using Docker Desktop
- `host.docker.internal:8080` should work automatically

### Option B: If using Docker in WSL
- May need to use your Windows host IP instead

**Find your Windows IP:**
```powershell
ipconfig | findstr IPv4
```

Then update `monitoring/prometheus/prometheus.yml`:
```yaml
static_configs:
  - targets: ['YOUR_WINDOWS_IP:8080']  # Replace with your IP
```

### Option C: Use host network mode (Linux only)
If on Linux, you can use:
```yaml
static_configs:
  - targets: ['172.17.0.1:8080']  # Docker bridge gateway
```

## Solution 3: Test Connection from Prometheus Container

Test if Prometheus can reach your app:

```powershell
# If using Docker Desktop
docker exec prometheus wget -qO- http://host.docker.internal:8080/actuator/health

# If using WSL Docker
wsl docker exec prometheus wget -qO- http://host.docker.internal:8080/actuator/health
```

If this fails, Prometheus can't reach your app.

## Solution 4: Restart Prometheus After Config Change

After changing `prometheus.yml`:

```powershell
# Restart Prometheus
docker restart prometheus

# Or reload config (if lifecycle enabled)
curl -X POST http://localhost:9090/-/reload
```

## Solution 5: Check Prometheus Logs

```powershell
# Check Prometheus logs for errors
docker logs prometheus

# Or with WSL
wsl docker logs prometheus
```

Look for errors like:
- `connection refused`
- `no such host`
- `timeout`

## Solution 6: Verify Application is Listening on All Interfaces

Make sure your Spring Boot app is accessible from Docker:

**Check application.yaml:**
```yaml
server:
  address: 0.0.0.0  # Listen on all interfaces (not just localhost)
  port: 8080
```

If it's set to `localhost` or `127.0.0.1`, Docker won't be able to reach it.

## Quick Test Steps

1. **Check if app is running:**
   ```powershell
   curl http://localhost:8080/actuator/health
   ```

2. **Check if metrics are available:**
   ```powershell
   curl http://localhost:8080/actuator/prometheus
   ```

3. **Check Prometheus targets:**
   - Open http://localhost:9090/targets
   - Should show `order-service` as UP

4. **Test query in Prometheus:**
   - Open http://localhost:9090/graph
   - Try: `up{job="order-service"}`
   - Should return `1` if working

5. **Generate some metrics:**
   ```powershell
   curl "http://localhost:8080/orders?orderNumber=ORDER-1"
   ```

6. **Wait 10-15 seconds** for Prometheus to scrape

7. **Check metrics in Prometheus:**
   - Query: `order_processed_total`
   - Should show data if working

## Common Issues

### Issue: Target shows DOWN
**Cause:** Prometheus can't reach the application
**Fix:** 
- Verify `host.docker.internal` works (Windows/Mac)
- Or use your host IP address
- Check firewall settings

### Issue: Target is UP but no metrics
**Cause:** No data has been generated yet
**Fix:**
- Make some API requests to generate metrics
- Wait for Prometheus to scrape (10-15 seconds)
- Check if metrics exist: `curl http://localhost:8080/actuator/prometheus`

### Issue: "connection refused" in logs
**Cause:** Application not running or not accessible
**Fix:**
- Start your application: `mvn spring-boot:run`
- Verify it's listening on `0.0.0.0:8080` (not just localhost)

## Still Not Working?

Run the troubleshooting script:
```powershell
.\troubleshoot-prometheus.ps1
```

This will check all common issues automatically.

