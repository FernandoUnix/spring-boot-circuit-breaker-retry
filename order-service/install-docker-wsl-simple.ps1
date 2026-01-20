# Simple Docker installation in WSL Ubuntu
# Run this script to install Docker in your existing WSL Ubuntu

Write-Host "Installing Docker in WSL Ubuntu" -ForegroundColor Green
Write-Host "===============================" -ForegroundColor Green

# Check if Docker is already installed
$dockerCheck = wsl docker --version 2>&1
if ($LASTEXITCODE -eq 0) {
    Write-Host "Docker is already installed: $dockerCheck" -ForegroundColor Green
    Write-Host "`nYou can use: wsl docker compose up -d" -ForegroundColor Cyan
    exit 0
}

Write-Host "`nInstalling Docker in WSL Ubuntu..." -ForegroundColor Yellow
Write-Host "This will use the official Docker installation script." -ForegroundColor Yellow
Write-Host ""

# Use the official Docker installation script (easiest method)
Write-Host "Downloading and running Docker installation script..." -ForegroundColor Cyan
wsl bash -c "curl -fsSL https://get.docker.com -o get-docker.sh && sudo sh get-docker.sh"

if ($LASTEXITCODE -eq 0) {
    Write-Host "`nStarting Docker service..." -ForegroundColor Cyan
    wsl sudo service docker start
    
    Write-Host "`nVerifying installation..." -ForegroundColor Cyan
    $version = wsl docker --version
    if ($LASTEXITCODE -eq 0) {
        Write-Host "`nSUCCESS! Docker installed: $version" -ForegroundColor Green
        
        Write-Host "`nOptional: To use Docker without 'sudo', run in Ubuntu:" -ForegroundColor Yellow
        Write-Host "  sudo usermod -aG docker `$USER" -ForegroundColor White
        Write-Host "  Then logout and login to Ubuntu again" -ForegroundColor White
        
        Write-Host "`nYou can now use Docker from PowerShell:" -ForegroundColor Cyan
        Write-Host "  wsl docker compose up -d" -ForegroundColor White
        Write-Host "  Or: .\start-monitoring.ps1" -ForegroundColor White
    } else {
        Write-Host "`nInstallation completed but verification failed." -ForegroundColor Yellow
        Write-Host "Try running: wsl docker --version" -ForegroundColor Yellow
    }
} else {
    Write-Host "`nERROR: Installation failed" -ForegroundColor Red
    Write-Host "Please check the error messages above" -ForegroundColor Yellow
}

