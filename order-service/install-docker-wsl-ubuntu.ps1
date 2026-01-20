# Install Docker in WSL Ubuntu
# This script helps you install Docker in your existing WSL Ubuntu

Write-Host "Docker Installation in WSL Ubuntu" -ForegroundColor Green
Write-Host "=================================" -ForegroundColor Green

# Check if WSL Ubuntu is available
Write-Host "`nChecking WSL Ubuntu..." -ForegroundColor Cyan
$wslCheck = wsl -l -v 2>&1

if ($wslCheck -match "Ubuntu") {
    Write-Host "Ubuntu found in WSL!" -ForegroundColor Green
} else {
    Write-Host "WARNING: Ubuntu not found in WSL" -ForegroundColor Yellow
    Write-Host "Available distributions:" -ForegroundColor Yellow
    wsl -l -v
    Write-Host "`nPlease make sure Ubuntu is installed in WSL" -ForegroundColor Yellow
    exit 1
}

# Check if Docker is already installed
Write-Host "`nChecking if Docker is already installed..." -ForegroundColor Cyan
$dockerCheck = wsl docker --version 2>&1

if ($LASTEXITCODE -eq 0) {
    Write-Host "Docker is already installed!" -ForegroundColor Green
    Write-Host "Version: $dockerCheck" -ForegroundColor Green
    Write-Host "`nYou can now use:" -ForegroundColor Cyan
    Write-Host "  wsl docker compose up -d" -ForegroundColor White
    Write-Host "  Or: .\start-monitoring.ps1" -ForegroundColor White
    exit 0
}

Write-Host "`nDocker is not installed in WSL Ubuntu." -ForegroundColor Yellow
Write-Host "`nInstalling Docker in WSL Ubuntu..." -ForegroundColor Cyan
Write-Host "This will run commands in your Ubuntu WSL..." -ForegroundColor Yellow

# Install Docker using the official script
Write-Host "`nStep 1: Updating packages..." -ForegroundColor Cyan
wsl sudo apt update

Write-Host "`nStep 2: Installing prerequisites..." -ForegroundColor Cyan
wsl sudo apt install -y ca-certificates curl gnupg lsb-release

Write-Host "`nStep 3: Adding Docker's official GPG key..." -ForegroundColor Cyan
wsl sudo mkdir -p /etc/apt/keyrings
wsl curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg

Write-Host "`nStep 4: Setting up Docker repository..." -ForegroundColor Cyan
wsl echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

Write-Host "`nStep 5: Installing Docker Engine..." -ForegroundColor Cyan
wsl sudo apt update
wsl sudo apt install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin

Write-Host "`nStep 6: Starting Docker service..." -ForegroundColor Cyan
wsl sudo service docker start

Write-Host "`nStep 7: Testing Docker installation..." -ForegroundColor Cyan
$dockerVersion = wsl docker --version
if ($LASTEXITCODE -eq 0) {
    Write-Host "`nSUCCESS! Docker is installed!" -ForegroundColor Green
    Write-Host "Version: $dockerVersion" -ForegroundColor Green
    
    Write-Host "`nOptional: Add your user to docker group (to avoid sudo)..." -ForegroundColor Yellow
    Write-Host "Run in Ubuntu: sudo usermod -aG docker `$USER" -ForegroundColor White
    Write-Host "Then logout and login again to Ubuntu" -ForegroundColor White
    
    Write-Host "`nYou can now use Docker from PowerShell:" -ForegroundColor Cyan
    Write-Host "  wsl docker compose up -d" -ForegroundColor White
    Write-Host "  Or: .\start-monitoring.ps1" -ForegroundColor White
} else {
    Write-Host "`nERROR: Docker installation may have failed" -ForegroundColor Red
    Write-Host "Please check the error messages above" -ForegroundColor Yellow
}

