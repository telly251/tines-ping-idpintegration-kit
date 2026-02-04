@echo off
REM ============================================
REM Tines IDP Adapter - Windows Deployment Script
REM ============================================
REM
REM Paths configured for your environment:
REM   Project: C:\Users\Administrator\Documents\tines-ping-idpintegration-kit-claude-tines-idp-adapter-cNt8n
REM   PingFederate: C:\Program Files\Ping Identity\pingfederate-12.3.0
REM

setlocal

REM Set your paths here
set PROJECT_DIR=C:\Users\Administrator\Documents\tines-ping-idpintegration-kit-claude-tines-idp-adapter-cNt8n
set PF_HOME=C:\Program Files\Ping Identity\pingfederate-12.3.0
set PF_SDK_JAR=%PF_HOME%\pingfederate\server\default\lib\pf-protocolengine.jar
set PF_DEPLOY_DIR=%PF_HOME%\pingfederate\server\default\deploy

echo =============================================
echo Tines IDP Adapter Deployment Script
echo =============================================
echo.
echo Project Directory: %PROJECT_DIR%
echo PingFederate Home: %PF_HOME%
echo.

REM Check if Maven is installed
where mvn >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo ERROR: Maven is not installed or not in PATH
    echo Please install Maven from https://maven.apache.org/download.cgi
    pause
    exit /b 1
)

REM Check if Java is installed
where java >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo ERROR: Java is not installed or not in PATH
    pause
    exit /b 1
)

REM Check if PingFederate SDK exists
if not exist "%PF_SDK_JAR%" (
    echo ERROR: PingFederate SDK not found at:
    echo   %PF_SDK_JAR%
    echo Please verify your PingFederate installation path.
    pause
    exit /b 1
)

echo Step 1: Installing PingFederate SDK to Maven repository...
echo.
call mvn install:install-file ^
  -Dfile="%PF_SDK_JAR%" ^
  -DgroupId=com.pingidentity.pingfederate ^
  -DartifactId=pf-protocolengine ^
  -Dversion=12.3.0 ^
  -Dpackaging=jar

if %ERRORLEVEL% neq 0 (
    echo ERROR: Failed to install PingFederate SDK
    pause
    exit /b 1
)

echo.
echo Step 2: Building the adapter...
echo.
cd /d "%PROJECT_DIR%"
call mvn clean package -DskipTests

if %ERRORLEVEL% neq 0 (
    echo ERROR: Build failed
    pause
    exit /b 1
)

echo.
echo Step 3: Deploying to PingFederate...
echo.

if not exist "%PF_DEPLOY_DIR%" (
    echo ERROR: PingFederate deploy directory not found:
    echo   %PF_DEPLOY_DIR%
    pause
    exit /b 1
)

copy /Y "%PROJECT_DIR%\target\tines-idp-adapter-1.0.0.jar" "%PF_DEPLOY_DIR%\"

if %ERRORLEVEL% neq 0 (
    echo ERROR: Failed to copy JAR to deploy directory
    pause
    exit /b 1
)

echo.
echo =============================================
echo SUCCESS! Deployment complete.
echo =============================================
echo.
echo The adapter JAR has been copied to:
echo   %PF_DEPLOY_DIR%\tines-idp-adapter-1.0.0.jar
echo.
echo NEXT STEPS:
echo   1. Restart PingFederate
echo   2. Log into the Admin Console
echo   3. Go to Authentication ^> Integration ^> IdP Adapters
echo   4. Create a new instance and select "Tines IDP Adapter"
echo.
echo To restart PingFederate, run:
echo   net stop PingFederate
echo   net start PingFederate
echo.
echo Or manually:
echo   "%PF_HOME%\pingfederate\bin\shutdown.bat"
echo   "%PF_HOME%\pingfederate\bin\run.bat"
echo.
pause
