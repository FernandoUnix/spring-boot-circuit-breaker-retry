# PowerShell script to install Docker in WSL 2
# Run as Administrator

Write-Host "Docker Installation via WSL 2" -ForegroundColor Green
Write-Host "================================" -ForegroundColor Green

# Check if running as Administrator
$isAdmin = ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    Write-Host "ERROR: This script must be run as Administrator" -ForegroundColor Red
    Write-Host "Right-click PowerShell and select 'Run as Administrator'" -ForegroundColor Yellow
    exit 1
}

# Check WSL
Write-Host "`nChecking WSL installation..." -ForegroundColor Cyan
$wslStatus = wsl --status 2>&1

if ($LASTEXITCODE -ne 0) {
    Write-Host "WSL is not installed. Installing WSL..." -ForegroundColor Yellow
    wsl --install
    Write-Host "`nWSL installation started. Please RESTART your computer and run this script again." -ForegroundColor Yellow
    Write-Host "After restart, open Ubuntu and run the installation commands." -ForegroundColor Yellow
    exit 0
} else {
    Write-Host "WSL is installed." -ForegroundColor Green
}

# Check if Docker is already installed in WSL
Write-Host "`nChecking Docker in WSL..." -ForegroundColor Cyan
$dockerCheck = wsl docker --version 2>&1

if ($LASTEXITCODE -eq 0) {
    Write-Host "Docker is already installed in WSL!" -ForegroundColor Green
    Write-Host "Version: $dockerCheck" -ForegroundColor Green
    exit 0
}

Write-Host "`nDocker is not installed in WSL." -ForegroundColor Yellow
Write-Host "Please follow these steps:" -ForegroundColor Yellow
Write-Host ""
Write-Host "1. Open Ubuntu (WSL) from Start Menu" -ForegroundColor Cyan
Write-Host "2. Run the following commands:" -ForegroundColor Cyan
Write-Host ""
Write-Host "# Update packages" -ForegroundColor White
Write-Host "sudo apt update && sudo apt upgrade -y" -ForegroundColor Gray
Write-Host ""
Write-Host "# Install Docker" -ForegroundColor White
Write-Host "curl -fsSL https://get.docker.com -o get-docker.sh" -ForegroundColor Gray
Write-Host "sudo sh get-docker.sh" -ForegroundColor Gray
Write-Host ""
Write-Host "# Start Docker service" -ForegroundColor White
Write-Host "sudo service docker start" -ForegroundColor Gray
Write-Host ""
Write-Host "# (Optional) Add user to docker group" -ForegroundColor White
Write-Host "sudo usermod -aG docker `$USER" -ForegroundColor Gray
Write-Host ""
Write-Host "3. After installation, verify:" -ForegroundColor Cyan
Write-Host "   docker --version" -ForegroundColor Gray
Write-Host "   docker compose version" -ForegroundColor Gray
Write-Host ""
Write-Host "4. Then you can use Docker from Windows PowerShell:" -ForegroundColor Cyan
Write-Host "   wsl docker compose up -d" -ForegroundColor Gray

