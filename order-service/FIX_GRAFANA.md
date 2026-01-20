# Fix Grafana Not Showing Data

## Quick Checks

### 1. Verify Grafana can reach Prometheus

**In Grafana UI (http://localhost:3000):**

1. Go to **Configuration** → **Data Sources** (or http://localhost:3000/connections/datasources)
2. Click on **Prometheus** datasource
3. Click **"Save & Test"** button
4. Check if it shows: ✅ **"Data source is working"**

If it shows ❌ **"Failed"**, the Grafana can't reach Prometheus.

### 2. Check Datasource URL

The datasource should be configured as:
- **URL**: `http://localhost:9090`
- **Access**: `proxy` (server-side)

### 3. Restart Grafana

If datasource was just configured, restart Grafana:

```powershell
wsl sudo docker restart grafana
```

Wait 10-15 seconds, then check again.

### 4. Check Dashboard

1. Go to **Dashboards** → **Browse** (or http://localhost:3000/dashboards)
2. Look for **"Order Service - Resilience4j & Application Metrics"**
3. Open it
4. Check if panels show "No data" or errors

### 5. Test Query in Grafana

1. Go to **Explore** (left menu, compass icon)
2. Select **Prometheus** as datasource
3. Try this query:
   ```promql
   order_processed_total
   ```
4. Click **Run query**
5. If you see data, datasource is working!

## Common Issues

### Issue 1: "Data source is not working"

**Cause**: Grafana can't reach Prometheus

**Fix**: 
- Verify Prometheus is running: `wsl sudo docker ps | grep prometheus`
- Check Prometheus URL: http://localhost:9090
- If using `network_mode: host`, both should be on same network

### Issue 2: Dashboard shows "No data"

**Cause**: 
- No metrics yet (need to make API calls)
- Time range is wrong
- Query is incorrect

**Fix**:
1. Make some API calls to generate metrics:
   ```powershell
   curl "http://localhost:8080/orders?orderNumber=ORDER-1"
   ```
2. Check time range (top right) - should be "Last 15 minutes" or "Last 1 hour"
3. Test query in Explore first

### Issue 3: "Datasource not found"

**Cause**: Datasource provisioning didn't work

**Fix**:
1. Manually add datasource:
   - Go to Configuration → Data Sources → Add data source
   - Select **Prometheus**
   - URL: `http://localhost:9090`
   - Click **Save & Test**

### Issue 4: Dashboard not loading

**Cause**: Dashboard file not found or invalid

**Fix**:
1. Check if dashboard file exists: `monitoring/grafana/dashboards/order-service-dashboard.json`
2. Check Grafana logs:
   ```powershell
   wsl sudo docker logs grafana | tail -50
   ```
3. Manually import dashboard:
   - Go to Dashboards → Import
   - Upload `order-service-dashboard.json`

## Step-by-Step Fix

### Step 1: Verify Services

```powershell
# Check if both are running
wsl sudo docker ps | grep -E "prometheus|grafana"
```

Both should be running.

### Step 2: Test Prometheus

Open http://localhost:9090/graph and run:
```promql
order_processed_total
```

If this works, Prometheus is fine.

### Step 3: Test Grafana Datasource

1. Open http://localhost:3000/connections/datasources
2. Click **Prometheus**
3. Click **"Save & Test"**
4. Should show ✅ **"Data source is working"**

### Step 4: Test Query in Grafana

1. Go to **Explore** (http://localhost:3000/explore)
2. Select **Prometheus**
3. Query: `order_processed_total`
4. Click **Run query**
5. Should show data

### Step 5: Open Dashboard

1. Go to **Dashboards** → **Browse**
2. Open **"Order Service - Resilience4j & Application Metrics"**
3. Should show metrics

## Manual Datasource Configuration

If automatic provisioning doesn't work:

1. Go to http://localhost:3000/connections/datasources
2. Click **"Add data source"**
3. Select **Prometheus**
4. Configure:
   - **Name**: `Prometheus`
   - **URL**: `http://localhost:9090`
   - **Access**: `Server (default)`
5. Click **"Save & Test"**
6. Should show ✅ **"Data source is working"**

## Still Not Working?

Check Grafana logs:
```powershell
wsl sudo docker logs grafana --tail 100
```

Look for errors related to:
- Datasource connection
- Dashboard loading
- Query execution

