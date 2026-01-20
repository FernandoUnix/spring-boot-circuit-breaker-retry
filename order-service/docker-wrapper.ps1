# Docker wrapper for Windows (works with WSL 2 Docker)
# Usage: .\docker-wrapper.ps1 compose up -d

param(
    [Parameter(ValueFromRemainingArguments=$true)]
    [string[]]$DockerArgs
)

# Check if Docker is available in WSL
$dockerCheck = wsl docker --version 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: Docker is not installed in WSL" -ForegroundColor Red
    Write-Host "Run .\install-docker-wsl.ps1 first" -ForegroundColor Yellow
    exit 1
}

# Convert arguments to string
$argsString = $DockerArgs -join " "

# Run Docker command through WSL
if ($argsString) {
    wsl docker $argsString
} else {
    wsl docker
}

