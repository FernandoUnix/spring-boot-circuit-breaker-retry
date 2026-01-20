# Installing Docker in Ubuntu WSL

Since you're already inside Ubuntu WSL, follow these steps:

## Quick Installation (Easiest)

**Inside Ubuntu terminal**, run:

```bash
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh
```

That's it! This will install Docker automatically.

## Or Use the Script

1. **Copy the script to Ubuntu** (or create it):
   ```bash
   # In Ubuntu, create the file
   nano install-docker-ubuntu.sh
   # Paste the content from install-docker-ubuntu.sh
   # Save: Ctrl+X, then Y, then Enter
   ```

2. **Make it executable**:
   ```bash
   chmod +x install-docker-ubuntu.sh
   ```

3. **Run it**:
   ```bash
   ./install-docker-ubuntu.sh
   ```

## After Installation

1. **Start Docker service** (if not running):
   ```bash
   sudo service docker start
   ```

2. **Verify installation**:
   ```bash
   docker --version
   docker compose version
   ```

3. **Optional: Use Docker without sudo**:
   ```bash
   sudo usermod -aG docker $USER
   ```
   Then logout and login to Ubuntu again.

## Using Docker from PowerShell

After installation, you can use Docker from Windows PowerShell:

```powershell
# Start monitoring
wsl docker compose up -d

# Or use the script (it will detect WSL Docker)
.\start-monitoring.ps1
```

## Testing Docker

**Inside Ubuntu**, test with:
```bash
sudo docker run hello-world
```

If you see "Hello from Docker!", it's working!

## Troubleshooting

**Docker service not starting:**
```bash
sudo service docker start
sudo service docker status
```

**Permission denied:**
- Use `sudo` before docker commands
- Or add user to docker group: `sudo usermod -aG docker $USER` (then logout/login)

**Docker daemon not running:**
```bash
sudo service docker start
```

