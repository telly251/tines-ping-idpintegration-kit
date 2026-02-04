# ============================================
# Tines IDP Adapter - Windows Deployment Script (PowerShell)
# ============================================
#
# Run this script in PowerShell as Administrator:
#   .\deploy-windows.ps1
#

$ErrorActionPreference = "Stop"

# Set your paths here
$PROJECT_DIR = "C:\Users\Administrator\Documents\tines-ping-idpintegration-kit-claude-tines-idp-adapter-cNt8n"
$PF_HOME = "C:\Program Files\Ping Identity\pingfederate-12.3.0"
$PF_SDK_JAR = "$PF_HOME\pingfederate\server\default\lib\pf-protocolengine.jar"
$PF_DEPLOY_DIR = "$PF_HOME\pingfederate\server\default\deploy"

Write-Host "=============================================" -ForegroundColor Cyan
Write-Host "Tines IDP Adapter Deployment Script" -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Project Directory: $PROJECT_DIR"
Write-Host "PingFederate Home: $PF_HOME"
Write-Host ""

# Check if Maven is installed
try {
    $mvnVersion = mvn -version 2>&1 | Select-Object -First 1
    Write-Host "Maven found: $mvnVersion" -ForegroundColor Green
} catch {
    Write-Host "ERROR: Maven is not installed or not in PATH" -ForegroundColor Red
    Write-Host "Please install Maven from https://maven.apache.org/download.cgi"
    exit 1
}

# Check if Java is installed
try {
    $javaVersion = java -version 2>&1 | Select-Object -First 1
    Write-Host "Java found: $javaVersion" -ForegroundColor Green
} catch {
    Write-Host "ERROR: Java is not installed or not in PATH" -ForegroundColor Red
    exit 1
}

# Check if PingFederate SDK exists
if (-not (Test-Path $PF_SDK_JAR)) {
    Write-Host "ERROR: PingFederate SDK not found at:" -ForegroundColor Red
    Write-Host "  $PF_SDK_JAR"
    Write-Host "Please verify your PingFederate installation path."
    exit 1
}
Write-Host "PingFederate SDK found" -ForegroundColor Green
Write-Host ""

# Step 1: Install PingFederate SDK to Maven
Write-Host "Step 1: Installing PingFederate SDK to Maven repository..." -ForegroundColor Yellow
Write-Host ""

mvn install:install-file `
  "-Dfile=$PF_SDK_JAR" `
  "-DgroupId=com.pingidentity.pingfederate" `
  "-DartifactId=pf-protocolengine" `
  "-Dversion=12.3.0" `
  "-Dpackaging=jar"

if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: Failed to install PingFederate SDK" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "Step 2: Building the adapter..." -ForegroundColor Yellow
Write-Host ""

Set-Location $PROJECT_DIR
mvn clean package -DskipTests

if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: Build failed" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "Step 3: Deploying to PingFederate..." -ForegroundColor Yellow
Write-Host ""

if (-not (Test-Path $PF_DEPLOY_DIR)) {
    Write-Host "ERROR: PingFederate deploy directory not found:" -ForegroundColor Red
    Write-Host "  $PF_DEPLOY_DIR"
    exit 1
}

Copy-Item "$PROJECT_DIR\target\tines-idp-adapter-1.0.0.jar" -Destination $PF_DEPLOY_DIR -Force

Write-Host ""
Write-Host "=============================================" -ForegroundColor Green
Write-Host "SUCCESS! Deployment complete." -ForegroundColor Green
Write-Host "=============================================" -ForegroundColor Green
Write-Host ""
Write-Host "The adapter JAR has been copied to:"
Write-Host "  $PF_DEPLOY_DIR\tines-idp-adapter-1.0.0.jar"
Write-Host ""
Write-Host "NEXT STEPS:" -ForegroundColor Yellow
Write-Host "  1. Restart PingFederate"
Write-Host "  2. Log into the Admin Console"
Write-Host "  3. Go to Authentication > Integration > IdP Adapters"
Write-Host "  4. Create a new instance and select 'Tines IDP Adapter'"
Write-Host ""
Write-Host "To restart PingFederate, run:" -ForegroundColor Cyan
Write-Host "  net stop PingFederate"
Write-Host "  net start PingFederate"
Write-Host ""

Read-Host "Press Enter to exit"
