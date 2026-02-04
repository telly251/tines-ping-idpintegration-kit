@echo off
REM ============================================
REM Tines IDP Adapter - Windows Deployment Script
REM ============================================
REM
REM This script builds and deploys the Tines IDP Adapter for PingFederate.
REM
REM IMPORTANT: The PingFederate SDK JAR (pf-sdk.jar) is required for compilation.
REM It is located at: <PF_HOME>\pingfederate\sdk\pf-sdk.jar
REM
REM Paths configured for your environment:
REM   PingFederate: C:\Program Files\Ping Identity\pingfederate-12.3.0
REM

setlocal EnableDelayedExpansion

REM ============================================
REM CONFIGURATION - Edit these paths as needed
REM ============================================
set PF_HOME=C:\Program Files\Ping Identity\pingfederate-12.3.0
set PF_VERSION=12.3.0

REM Derived paths - do not edit
set PF_SDK_JAR=%PF_HOME%\pingfederate\sdk\pf-sdk.jar
set PF_DEPLOY_DIR=%PF_HOME%\pingfederate\server\default\deploy
set PROJECT_DIR=%~dp0

echo =============================================
echo Tines IDP Adapter Deployment Script
echo =============================================
echo.
echo PingFederate Home: %PF_HOME%
echo PingFederate SDK:  %PF_SDK_JAR%
echo Deploy Directory:  %PF_DEPLOY_DIR%
echo Project Directory: %PROJECT_DIR%
echo.

REM ============================================
REM Pre-flight checks
REM ============================================
echo [CHECK] Verifying prerequisites...

REM Check if Maven is installed
where mvn >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo.
    echo [ERROR] Maven is not installed or not in PATH
    echo         Please install Maven from https://maven.apache.org/download.cgi
    echo         After installation, add Maven's bin directory to your PATH
    goto :error
)
echo         Maven: OK

REM Check if Java is installed
where java >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo.
    echo [ERROR] Java is not installed or not in PATH
    echo         Please install Java 11 or later
    goto :error
)
echo         Java:  OK

REM Check if PingFederate installation exists
if not exist "%PF_HOME%" (
    echo.
    echo [ERROR] PingFederate installation not found at:
    echo         %PF_HOME%
    echo.
    echo         Please edit this script and set the correct PF_HOME path.
    goto :error
)
echo         PingFederate Home: OK

REM Check if PingFederate SDK JAR exists
if not exist "%PF_SDK_JAR%" (
    echo.
    echo [ERROR] PingFederate SDK JAR not found at:
    echo         %PF_SDK_JAR%
    echo.
    echo         The SDK should be in the 'sdk' folder of your PingFederate installation.
    echo         If the SDK folder doesn't exist, you may need to download it separately
    echo         from Ping Identity's website or extract it from the PingFederate distribution.
    echo.
    echo         Alternative locations to check:
    echo           - %PF_HOME%\sdk\pf-sdk.jar
    echo           - %PF_HOME%\pingfederate\sdk\pf-sdk.jar
    goto :error
)
echo         PingFederate SDK:  OK

echo.
echo [CHECK] All prerequisites satisfied!
echo.

REM ============================================
REM Step 1: Clean Maven cache for PingFederate artifacts
REM ============================================
echo =============================================
echo Step 1: Cleaning Maven cache...
echo =============================================
echo.

if exist "%USERPROFILE%\.m2\repository\com\pingidentity" (
    echo Removing cached PingFederate artifacts...
    rmdir /s /q "%USERPROFILE%\.m2\repository\com\pingidentity" 2>nul
)
echo Done.
echo.

REM ============================================
REM Step 2: Install PingFederate SDK to Maven local repository
REM ============================================
echo =============================================
echo Step 2: Installing PingFederate SDK to Maven repository...
echo =============================================
echo.
echo Installing: %PF_SDK_JAR%
echo.

call mvn install:install-file ^
  -Dfile="%PF_SDK_JAR%" ^
  -DgroupId=com.pingidentity.pingfederate ^
  -DartifactId=pf-sdk ^
  -Dversion=%PF_VERSION% ^
  -Dpackaging=jar ^
  -DgeneratePom=true

if %ERRORLEVEL% neq 0 (
    echo.
    echo [ERROR] Failed to install PingFederate SDK to Maven repository
    echo         Check the error messages above for details.
    goto :error
)
echo.
echo [SUCCESS] PingFederate SDK installed to Maven repository
echo.

REM ============================================
REM Step 3: Build the adapter
REM ============================================
echo =============================================
echo Step 3: Building the Tines IDP Adapter...
echo =============================================
echo.

cd /d "%PROJECT_DIR%"

call mvn clean package -DskipTests -Dpf.home="%PF_HOME%"

if %ERRORLEVEL% neq 0 (
    echo.
    echo [ERROR] Build failed!
    echo         Check the error messages above for details.
    echo.
    echo Common issues:
    echo   - Missing dependencies: Run 'mvn dependency:tree' to check
    echo   - Compilation errors: Check that pf-sdk.jar contains required classes
    goto :error
)
echo.
echo [SUCCESS] Build completed successfully
echo.

REM ============================================
REM Step 4: Deploy to PingFederate
REM ============================================
echo =============================================
echo Step 4: Deploying to PingFederate...
echo =============================================
echo.

if not exist "%PF_DEPLOY_DIR%" (
    echo [ERROR] PingFederate deploy directory not found:
    echo         %PF_DEPLOY_DIR%
    goto :error
)

set JAR_FILE=%PROJECT_DIR%target\tines-idp-adapter-1.0.0.jar
if not exist "%JAR_FILE%" (
    echo [ERROR] Built JAR file not found:
    echo         %JAR_FILE%
    goto :error
)

echo Copying adapter JAR to PingFederate deploy directory...
copy /Y "%JAR_FILE%" "%PF_DEPLOY_DIR%\"

if %ERRORLEVEL% neq 0 (
    echo.
    echo [ERROR] Failed to copy JAR to deploy directory
    echo         You may need to run this script as Administrator
    goto :error
)

echo.
echo =============================================
echo SUCCESS! Deployment complete.
echo =============================================
echo.
echo The adapter JAR has been deployed to:
echo   %PF_DEPLOY_DIR%\tines-idp-adapter-1.0.0.jar
echo.
echo =============================================
echo NEXT STEPS:
echo =============================================
echo.
echo 1. RESTART PingFederate:
echo    - Using Windows Services:
echo        net stop PingFederate
echo        net start PingFederate
echo.
echo    - Or using batch files:
echo        "%PF_HOME%\pingfederate\bin\shutdown.bat"
echo        "%PF_HOME%\pingfederate\bin\run.bat"
echo.
echo 2. CONFIGURE THE ADAPTER:
echo    a. Log into the PingFederate Admin Console
echo       (typically https://localhost:9999/pingfederate/app)
echo.
echo    b. Navigate to: Authentication ^> Integration ^> IdP Adapters
echo.
echo    c. Click "Create New Instance"
echo.
echo    d. Select "Tines IDP Adapter" from the Type dropdown
echo.
echo    e. Configure the adapter settings:
echo       - Webhook URL: Your Tines webhook endpoint
echo       - Timeout: Request timeout in seconds (default: 30)
echo       - Secret Token: Optional authentication token
echo.
echo    f. Configure the attribute contract as needed
echo.
echo    g. Save and activate the adapter
echo.
echo 3. USE IN AUTHENTICATION POLICY:
echo    - Add the adapter to your authentication policy
echo    - Chain it with other adapters as needed
echo.
goto :end

:error
echo.
echo =============================================
echo DEPLOYMENT FAILED
echo =============================================
echo.
echo Please fix the errors above and try again.
echo.
pause
exit /b 1

:end
pause
exit /b 0
