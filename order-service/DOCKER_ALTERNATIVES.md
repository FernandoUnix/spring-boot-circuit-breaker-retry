# Docker Alternatives - No WSL Required

Since WSL installation is failing, here are alternatives that don't require WSL or Docker Desktop.

## Option 1: Podman (Best Alternative - No Daemon Required)

Podman is a Docker-compatible tool that works without a daemon.

### Installation via Chocolatey

1. **Install Chocolatey** (if not installed):
   ```powershell
   Set-ExecutionPolicy Bypass -Scope Process -Force; [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.ServicePointManager]::SecurityProtocol -bor 3072; iex ((New-Object System.Net.WebClient).DownloadString('https://community.chocolatey.org/install.ps1'))
   ```

2. **Install Podman**:
   ```powershell
   choco install podman
   ```

3. **Verify**:
   ```powershell
   podman --version
   ```

4. **Use Podman** (Docker-compatible):
   ```powershell
   # Start monitoring
   podman compose up -d
   
   # Or use the script (it will detect Podman)
   .\start-monitoring.ps1
   ```

### Manual Podman Installation

1. Download from: https://podman-desktop.io/download/windows
2. Install the `.exe` file
3. Restart PowerShell
4. Use: `podman compose up -d`

## Option 2: Use Pre-built Docker Images (Manual)

If you can't install Docker/Podman, you can download and run containers manually.

### Download Prometheus

1. Download Prometheus binary:
   - Go to: https://prometheus.io/download/
   - Download `prometheus-windows-amd64.zip`
   - Extract to a folder (e.g., `C:\prometheus`)

2. Create `prometheus.yml` in that folder (copy from `monitoring/prometheus/prometheus.yml`)

3. Run Prometheus:
   ```powershell
   cd C:\prometheus
   .\prometheus.exe --config.file=prometheus.yml
   ```

### Download Grafana

1. Download Grafana:
   - Go to: https://grafana.com/grafana/download?platform=windows
   - Download Windows installer
   - Install Grafana

2. Configure Grafana:
   - Edit `C:\Program Files\GrafanaLabs\grafana\conf\grafana.ini`
   - Add Prometheus datasource
   - Or use Grafana UI at http://localhost:3000

## Option 3: Use Online Monitoring Tools

### Option 3a: Grafana Cloud (Free Tier)

1. Sign up at: https://grafana.com/auth/sign-up/create-user
2. Create a Prometheus data source
3. Use Grafana Cloud's hosted Grafana
4. Push metrics from your app to Grafana Cloud

### Option 3b: Local Prometheus + Remote Grafana

1. Run Prometheus locally (see Option 2)
2. Use Grafana Cloud for visualization
3. Connect Grafana Cloud to your local Prometheus

## Option 4: Fix WSL Network Issue

If you want to try fixing WSL installation:

### Check Network/Proxy

```powershell
# Check if you're behind a proxy
$env:HTTP_PROXY
$env:HTTPS_PROXY

# If behind proxy, set it:
$env:HTTP_PROXY = "http://your-proxy:port"
$env:HTTPS_PROXY = "http://your-proxy:port"

# Then try WSL install again
wsl --install
```

### Manual WSL Installation

1. Enable WSL feature manually:
   ```powershell
   dism.exe /online /enable-feature /featurename:Microsoft-Windows-Subsystem-Linux /all /norestart
   dism.exe /online /enable-feature /featurename:VirtualMachinePlatform /all /norestart
   ```

2. Restart computer

3. Download WSL2 kernel update:
   - https://wslstorestorage.blob.core.windows.net/wslblob/wsl_update_x64.msi
   - Install it

4. Set WSL 2 as default:
   ```powershell
   wsl --set-default-version 2
   ```

5. Download Ubuntu manually:
   - Go to Microsoft Store
   - Search "Ubuntu"
   - Install Ubuntu

6. Then install Docker in Ubuntu (see main guide)

## Recommended: Use Podman

**Podman is the easiest solution** - it's Docker-compatible and doesn't need WSL or Docker Desktop.

```powershell
# Install via Chocolatey
choco install podman

# Then use it exactly like Docker
podman compose up -d
```

The `start-monitoring.ps1` script will automatically detect and use Podman if Docker is not available.

