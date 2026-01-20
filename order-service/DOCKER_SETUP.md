# Docker Setup Guide

## Option 1: Install Docker Desktop (Recommended)

### Windows

1. **Download Docker Desktop**:
   - Go to: https://www.docker.com/products/docker-desktop/
   - Download "Docker Desktop for Windows"
   - Install the `.exe` file

2. **After Installation**:
   - Restart your computer
   - Start Docker Desktop from Start Menu
   - Wait for Docker to start (whale icon in system tray)

3. **Verify Installation**:
   ```powershell
   docker --version
   docker compose version
   ```

4. **Start Monitoring Stack**:
   ```powershell
   docker compose up -d
   ```

### Alternative: Use `docker compose` (without hyphen)

If you have Docker Desktop installed, try:
```powershell
docker compose up -d
```

Instead of:
```powershell
docker-compose up -d
```

## Option 2: Manual Docker Commands (Without Docker Compose)

If you can't install Docker Desktop, you can run containers manually:

### Start Prometheus

```powershell
docker run -d `
  --name prometheus `
  -p 9090:9090 `
  -v ${PWD}/monitoring/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml `
  -v ${PWD}/monitoring/prometheus/alerts:/etc/prometheus/alerts `
  prom/prometheus:latest `
  --config.file=/etc/prometheus/prometheus.yml `
  --storage.tsdb.path=/prometheus `
  --storage.tsdb.retention.time=30d `
  --web.enable-lifecycle
```

### Start Grafana

```powershell
docker run -d `
  --name grafana `
  -p 3000:3000 `
  -v ${PWD}/monitoring/grafana/provisioning:/etc/grafana/provisioning `
  -v ${PWD}/monitoring/grafana/dashboards:/var/lib/grafana/dashboards `
  -e GF_SECURITY_ADMIN_USER=admin `
  -e GF_SECURITY_ADMIN_PASSWORD=admin `
  -e GF_USERS_ALLOW_SIGN_UP=false `
  grafana/grafana:latest
```

### Stop Containers

```powershell
docker stop prometheus grafana
docker rm prometheus grafana
```

## Option 3: PowerShell Script

I'll create a PowerShell script to make it easier.

## Troubleshooting

### Docker not in PATH
- Add Docker to your system PATH
- Or use full path: `C:\Program Files\Docker\Docker\resources\bin\docker.exe`

### Permission Issues
- Run PowerShell as Administrator
- Or enable WSL 2 backend in Docker Desktop settings

### Port Already in Use
- Check if ports 9090 or 3000 are already in use
- Change ports in docker-compose.yml if needed

