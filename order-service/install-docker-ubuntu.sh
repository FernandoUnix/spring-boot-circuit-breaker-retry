#!/bin/bash
# Install Docker in Ubuntu WSL
# Run this script INSIDE Ubuntu (not from PowerShell)

echo "Installing Docker in Ubuntu WSL..."
echo "=================================="

# Check if Docker is already installed
if command -v docker &> /dev/null; then
    echo "Docker is already installed: $(docker --version)"
    exit 0
fi

# Update packages
echo ""
echo "Step 1: Updating packages..."
sudo apt update

# Install prerequisites
echo ""
echo "Step 2: Installing prerequisites..."
sudo apt install -y ca-certificates curl gnupg lsb-release

# Add Docker's official GPG key
echo ""
echo "Step 3: Adding Docker's official GPG key..."
sudo mkdir -p /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg

# Set up Docker repository
echo ""
echo "Step 4: Setting up Docker repository..."
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# Install Docker
echo ""
echo "Step 5: Installing Docker Engine..."
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin

# Start Docker service
echo ""
echo "Step 6: Starting Docker service..."
sudo service docker start

# Verify installation
echo ""
echo "Step 7: Verifying installation..."
if docker --version; then
    echo ""
    echo "SUCCESS! Docker is installed!"
    echo ""
    echo "Optional: Add your user to docker group (to avoid sudo):"
    echo "  sudo usermod -aG docker \$USER"
    echo "  Then logout and login again"
    echo ""
    echo "You can now use Docker from PowerShell:"
    echo "  wsl docker compose up -d"
    echo "  Or: .\start-monitoring.ps1"
else
    echo ""
    echo "ERROR: Docker installation failed"
    exit 1
fi

