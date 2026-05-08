@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
set "REPO_ROOT=%SCRIPT_DIR%.."
set "KEYS_DIR=%REPO_ROOT%\keys"

if "%ALIAS%"=="" set "ALIAS=oauth2-client"
if "%PASSWORD%"=="" set "PASSWORD=changeit"
if "%SUBJECT%"=="" set "SUBJECT=/CN=oauth2-jwt-client/O=Odea Integrations/C=US"
if "%DAYS%"=="" set "DAYS=365"

if not exist "%KEYS_DIR%" mkdir "%KEYS_DIR%"
cd /d "%KEYS_DIR%"

set "OPENSSL_EXE="
where openssl >nul 2>nul
if %errorlevel%==0 set "OPENSSL_EXE=openssl"
if "%OPENSSL_EXE%"=="" if exist "C:\Program Files\OpenSSL-Win64\bin\openssl.exe" set "OPENSSL_EXE=C:\Program Files\OpenSSL-Win64\bin\openssl.exe"
if "%OPENSSL_EXE%"=="" if exist "C:\Program Files (x86)\OpenSSL-Win32\bin\openssl.exe" set "OPENSSL_EXE=C:\Program Files (x86)\OpenSSL-Win32\bin\openssl.exe"
if "%OPENSSL_EXE%"=="" goto :no_openssl

echo Using OpenSSL at: %OPENSSL_EXE%

echo Generating RSA private key (2048-bit)...
"%OPENSSL_EXE%" genrsa -out oauth2-client.key 2048
if errorlevel 1 goto :error

echo Generating CSR...
"%OPENSSL_EXE%" req -new -key oauth2-client.key -out oauth2-client.csr -subj "%SUBJECT%"
if errorlevel 1 goto :error

echo Self-signing certificate (%DAYS% days)...
"%OPENSSL_EXE%" x509 -req -days %DAYS% -in oauth2-client.csr -signkey oauth2-client.key -out oauth2-client.crt
if errorlevel 1 goto :error

echo Building PKCS12 keystore...
"%OPENSSL_EXE%" pkcs12 -export -in oauth2-client.crt -inkey oauth2-client.key -name "%ALIAS%" -out oauth2-client.p12 -password pass:%PASSWORD%
if errorlevel 1 goto :error

echo.
echo Created files:
echo   %KEYS_DIR%\oauth2-client.key   (private key, do not share)
echo   %KEYS_DIR%\oauth2-client.csr   (certificate request)
echo   %KEYS_DIR%\oauth2-client.crt   (public certificate, give to AS admin)
echo   %KEYS_DIR%\oauth2-client.p12   (PKCS12 keystore loaded by the app)
echo.
echo Environment variables to set:
echo   OAUTH2_KEYSTORE_PATH=%KEYS_DIR%\oauth2-client.p12
echo   OAUTH2_KEYSTORE_PASSWORD=%PASSWORD%
echo   OAUTH2_KEY_ALIAS=%ALIAS%
echo   OAUTH2_KEY_PASSWORD=%PASSWORD%
goto :eof

:error
echo.
echo Key generation failed.
exit /b 1

:no_openssl
echo.
echo Could not find openssl.exe. Install OpenSSL or set OPENSSL_EXE to its full path.
echo Tried: PATH lookup, "C:\Program Files\OpenSSL-Win64\bin\openssl.exe"
exit /b 1
