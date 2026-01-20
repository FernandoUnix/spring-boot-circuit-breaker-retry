# PowerShell script to install Podman (Docker alternative)
# Run as Administrator

Write-Host "Podman Installation Script" -ForegroundColor Green
Write-Host "=========================" -ForegroundColor Green

# Check if running as Administrator
$isAdmin = ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    Write-Host "ERROR: This script must be run as Administrator" -ForegroundColor Red
    Write-Host "Right-click PowerShell and select 'Run as Administrator'" -ForegroundColor Yellow
    exit 1
}

# Check if Chocolatey is installed
Write-Host "`nChecking for Chocolatey..." -ForegroundColor Cyan
$chocoInstalled = Get-Command choco -ErrorAction SilentlyContinue

if (-not $chocoInstalled) {
    Write-Host "Chocolatey is not installed. Installing Chocolatey..." -ForegroundColor Yellow
    Set-ExecutionPolicy Bypass -Scope Process -Force
    [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.ServicePointManager]::SecurityProtocol -bor 3072
    iex ((New-Object System.Net.WebClient).DownloadString('https://community.chocolatey.org/install.ps1'))
    
    # Refresh environment
    $env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
    
    Write-Host "Chocolatey installed! Please close and reopen PowerShell, then run this script again." -ForegroundColor Green
    exit 0
} else {
    Write-Host "Chocolatey is installed." -ForegroundColor Green
}

# Check if Podman is already installed
Write-Host "`nChecking for Podman..." -ForegroundColor Cyan
$podmanInstalled = Get-Command podman -ErrorAction SilentlyContinue

if ($podmanInstalled) {
    $version = podman --version
    Write-Host "Podman is already installed: $version" -ForegroundColor Green
    Write-Host "You can now use: podman compose up -d" -ForegroundColor Cyan
    exit 0
}

# Install Podman
Write-Host "`nInstalling Podman..." -ForegroundColor Yellow
Write-Host "This may take a few minutes..." -ForegroundColor Yellow

try {
    choco install podman -y
    Write-Host "`nPodman installed successfully!" -ForegroundColor Green
    
    # Refresh PATH
    $env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")
    
    # Verify installation
    Start-Sleep -Seconds 2
    $podmanCheck = podman --version 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "`nVerification: $podmanCheck" -ForegroundColor Green
        Write-Host "`nYou can now use Podman!" -ForegroundColor Green
        Write-Host "Run: .\start-monitoring.ps1" -ForegroundColor Cyan
    } else {
        Write-Host "`nPodman installed but not in PATH yet." -ForegroundColor Yellow
        Write-Host "Please close and reopen PowerShell, then run: podman --version" -ForegroundColor Yellow
    }
} catch {
    Write-Host "`nERROR: Failed to install Podman" -ForegroundColor Red
    Write-Host "Error: $_" -ForegroundColor Red
    Write-Host "`nYou can try manual installation:" -ForegroundColor Yellow
    Write-Host "1. Download from: https://podman-desktop.io/download/windows" -ForegroundColor White
    Write-Host "2. Install the .exe file" -ForegroundColor White
    exit 1
}

