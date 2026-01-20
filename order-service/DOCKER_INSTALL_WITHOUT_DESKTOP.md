# Installing Docker Without Docker Desktop on Windows

## Option 1: Docker Engine via WSL 2 (Recommended)

### Prerequisites
- Windows 10 version 2004 or higher, or Windows 11
- WSL 2 installed

### Step 1: Install WSL 2

1. Open PowerShell as Administrator
2. Run:
```powershell
wsl --install
```

3. Restart your computer when prompted

### Step 2: Install Docker in WSL 2

1. Open Ubuntu (or your WSL distribution)
2. Update packages:
```bash
sudo apt update
sudo apt upgrade -y
```

3. Install Docker:
```bash
# Remove old versions
sudo apt remove docker docker-engine docker.io containerd runc

# Install prerequisites
sudo apt install -y \
    ca-certificates \
    curl \
    gnupg \
    lsb-release

# Add Docker's official GPG key
sudo mkdir -p /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg

# Set up repository
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# Install Docker Engine
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin

# Start Docker
sudo service docker start

# Add your user to docker group (optional, to avoid sudo)
sudo usermod -aG docker $USER
```

4. Verify installation:
```bash
docker --version
docker compose version
```

### Step 3: Use Docker from Windows

You can use Docker from Windows PowerShell by accessing WSL:

```powershell
# Run commands through WSL
wsl docker --version
wsl docker compose up -d
```

Or create a wrapper script (see below).

## Option 2: Podman (Docker Alternative)

Podman is a Docker-compatible alternative that doesn't require a daemon.

### Installation

1. Download Podman Desktop or Podman CLI:
   - https://podman-desktop.io/
   - Or use Chocolatey: `choco install podman`

2. Verify:
```powershell
podman --version
```

3. Use Podman (Docker-compatible commands):
```powershell
podman compose up -d
```

**Note**: Podman uses `podman compose` instead of `docker compose`, but commands are the same.

## Option 3: Colima (Lightweight Docker Alternative)

Colima provides Docker runtime without Docker Desktop.

### Installation

1. Install via Chocolatey or Scoop:
```powershell
# Using Chocolatey
choco install colima

# Or using Scoop
scoop install colima
```

2. Start Colima:
```powershell
colima start
```

3. Use Docker commands:
```powershell
docker --version
docker compose up -d
```

## Option 4: Use Pre-built Scripts (Easiest)

I'll create wrapper scripts that work with any Docker installation method.

## Quick Setup Script

Run this PowerShell script to set up Docker in WSL 2:

```powershell
# Check if WSL is installed
wsl --status

# If not installed, install WSL
wsl --install

# After restart, open Ubuntu and run:
# (I'll create a script for this)
```

## Using Docker from Windows (After WSL Installation)

Create a PowerShell alias or wrapper:

```powershell
# Add to your PowerShell profile
function docker {
    wsl docker $args
}

function docker-compose {
    wsl docker compose $args
}
```

Or use the wrapper scripts I'll create below.

