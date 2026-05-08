@echo off
setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
set "REPO_ROOT=%SCRIPT_DIR%.."
set "KEYS_DIR=%REPO_ROOT%\keys"

if "%ALIAS%"=="" set "ALIAS=oauth2-client"
if "%PASSWORD%"=="" set "PASSWORD=changeit"
if "%DNAME%"=="" set "DNAME=CN=oauth2-jwt-client, O=Odea Integrations, C=US"
if "%DAYS%"=="" set "DAYS=365"

if not exist "%KEYS_DIR%" mkdir "%KEYS_DIR%"

set "KEYTOOL="
where keytool >nul 2>nul && set "KEYTOOL=keytool"

if "%KEYTOOL%"=="" if defined JAVA_HOME if exist "%JAVA_HOME%\bin\keytool.exe" set "KEYTOOL=%JAVA_HOME%\bin\keytool.exe"

if "%KEYTOOL%"=="" call :find_keytool "%USERPROFILE%\.jdks"
if "%KEYTOOL%"=="" call :find_keytool "C:\Program Files\Eclipse Adoptium"
if "%KEYTOOL%"=="" call :find_keytool "C:\Program Files\Java"
if "%KEYTOOL%"=="" call :find_keytool "C:\Program Files\Microsoft"
if "%KEYTOOL%"=="" call :find_keytool "C:\Program Files\BellSoft"
if "%KEYTOOL%"=="" call :find_keytool "C:\Program Files\Amazon Corretto"
if "%KEYTOOL%"=="" call :find_keytool "C:\Program Files\Zulu"
if "%KEYTOOL%"=="" call :find_keytool "C:\Program Files\JetBrains"

if "%KEYTOOL%"=="" goto :no_keytool

echo Using keytool at: %KEYTOOL%

echo Generating RSA private key + self-signed cert (PKCS12 keystore)...
"%KEYTOOL%" -genkeypair -keyalg RSA -keysize 2048 -alias "%ALIAS%" -keystore "%KEYS_DIR%\oauth2-client.p12" -storetype PKCS12 -storepass "%PASSWORD%" -keypass "%PASSWORD%" -dname "%DNAME%" -validity %DAYS%
if errorlevel 1 goto :error

echo Exporting public certificate (PEM)...
"%KEYTOOL%" -exportcert -alias "%ALIAS%" -keystore "%KEYS_DIR%\oauth2-client.p12" -storetype PKCS12 -storepass "%PASSWORD%" -rfc -file "%KEYS_DIR%\oauth2-client.crt"
if errorlevel 1 goto :error

echo.
echo Created files:
echo   %KEYS_DIR%\oauth2-client.p12   (PKCS12 keystore loaded by the app)
echo   %KEYS_DIR%\oauth2-client.crt   (public certificate, give to AS admin)
echo.
echo Environment variables to set:
echo   OAUTH2_KEYSTORE_PATH=%KEYS_DIR%\oauth2-client.p12
echo   OAUTH2_KEYSTORE_PASSWORD=%PASSWORD%
echo   OAUTH2_KEY_ALIAS=%ALIAS%
echo   OAUTH2_KEY_PASSWORD=%PASSWORD%
goto :eof

:find_keytool
if not exist "%~1" exit /b 0
for /r "%~1" %%F in (keytool.exe) do (
    if "!KEYTOOL!"=="" if exist "%%F" set "KEYTOOL=%%F"
)
exit /b 0

:error
echo.
echo Key generation failed.
exit /b 1

:no_keytool
echo.
echo Could not find keytool.exe. Either:
echo   - Install a JDK 17 ^(winget install EclipseAdoptium.Temurin.17.JDK^), OR
echo   - Open IntelliJ -^> Project Structure -^> SDKs -^> Download JDK -^> Temurin 17, OR
echo   - Set the KEYTOOL env var to its full path and rerun.
exit /b 1
