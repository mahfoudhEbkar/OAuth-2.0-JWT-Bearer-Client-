# windows-server-setup.ps1
#
# One-time setup of a Windows Server host for the oauth2-jwt-client WAR.
# Idempotent - safe to re-run. Designed to be the ONLY thing a server admin
# does before the first deploy.
#
# After this runs, every redeploy is just:
#     Stop-Service Tomcat9
#     <copy/download the new oauth2-jwt-client.war into webapps\>
#     Remove-Item -Recurse -Force <webapps>\oauth2-jwt-client\
#     Start-Service Tomcat9
#
# What this script does:
#   1. Verifies Temurin 8 is installed; installs via winget if not.
#   2. Creates a locked-down secrets folder under Tomcat\conf\oauth2\.
#   3. Generates a 2048-bit RSA PKCS12 keystore + public cert there (if missing).
#   4. Writes Tomcat\bin\setenv.bat with the env vars the app needs.
#   5. Opens TCP 8080 inbound in Windows Firewall (Domain + Private profiles).
#   6. Prints next steps - including the public cert path to hand to the AS team.
#
# Run from PowerShell as Administrator. RDP to the server, open PowerShell ISE
# or pwsh, paste, run.

[CmdletBinding()]
param(
    # --- Tomcat ---
    [string] $TomcatHome    = "C:\apache-tomcat-9.0.117",
    [string] $TomcatService = "Tomcat9",

    # --- App / AS configuration. Override every one of these for your environment. ---
    [string] $ClientId         = "REPLACE-WITH-YOUR-CLIENT-ID",
    [string] $TokenEndpoint    = "https://as.example.com/oauth2/token",
    [string] $Scope            = "api.read api.write",
    [string] $KeyId            = "prod-key-2026",
    [string] $ApiBaseUrl       = "https://api.example.com",
    [string] $SpringProfile    = "prod",

    # --- Keystore. Use a strong password. Do NOT keep 'changeit' in production. ---
    [string] $KeystorePassword = "REPLACE-WITH-STRONG-PASSWORD",
    [string] $KeyAlias         = "oauth2-client",
    [int]    $KeyValidityDays  = 730,
    [string] $CertSubject      = "CN=oauth2-jwt-client-prod, O=Odea, C=US",

    # --- JVM ---
    [string] $JvmHeapMin = "256m",
    [string] $JvmHeapMax = "512m"
)

$ErrorActionPreference = "Stop"

function Write-Step($msg) { Write-Host "==> $msg" -ForegroundColor Cyan }
function Write-Ok($msg)   { Write-Host "    $msg" -ForegroundColor Green }
function Write-Warn2($m)  { Write-Host "    $m" -ForegroundColor Yellow }

# --- Sanity: must be Administrator ---
if (-not ([Security.Principal.WindowsPrincipal] `
        [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole(
            [Security.Principal.WindowsBuiltInRole] "Administrator")) {
    throw "This script must be run as Administrator (RDP, then 'Run as administrator' on PowerShell)."
}

# --- Sanity: Tomcat is installed at the expected path ---
if (-not (Test-Path $TomcatHome)) {
    throw "Tomcat not found at $TomcatHome. Install Tomcat 9.x from https://tomcat.apache.org/download-90.cgi or pass -TomcatHome <path>."
}
if (-not (Test-Path "$TomcatHome\bin\catalina.bat")) {
    throw "$TomcatHome does not look like a Tomcat install (catalina.bat missing)."
}

# === 1. Verify / install Temurin 8 ===
Write-Step "Checking for JDK 1.8"
$javaHome = $null
$candidates = @(
    "C:\Program Files\Eclipse Adoptium",
    "C:\Program Files\Java",
    "C:\Program Files\Microsoft\jdk-8*"
) | Where-Object { Test-Path $_ }
foreach ($base in $candidates) {
    $hit = Get-ChildItem $base -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match "jdk-?8" } | Select-Object -First 1
    if ($hit) { $javaHome = $hit.FullName; break }
}
if (-not $javaHome) {
    Write-Warn2 "JDK 8 not found. Installing Temurin 8 via winget..."
    winget install EclipseAdoptium.Temurin.8.JDK --accept-source-agreements --accept-package-agreements --silent
    $javaHome = (Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory |
        Where-Object { $_.Name -match "jdk-?8" } | Select-Object -First 1).FullName
    if (-not $javaHome) {
        throw "winget install completed but no jdk-8 folder found under C:\Program Files\Eclipse Adoptium."
    }
}
Write-Ok "Using JAVA_HOME = $javaHome"

$keytool = Join-Path $javaHome "bin\keytool.exe"
if (-not (Test-Path $keytool)) { throw "keytool.exe not found at $keytool" }

# === 2. Secrets folder with locked-down ACL ===
$secretsDir = Join-Path $TomcatHome "conf\oauth2"
Write-Step "Preparing secrets folder: $secretsDir"
New-Item -ItemType Directory -Force $secretsDir | Out-Null

$tomcatSvcAccount = "NT SERVICE\$TomcatService"
icacls $secretsDir /inheritance:r /grant:r "${tomcatSvcAccount}:(R)" "Administrators:(F)" "SYSTEM:(F)" | Out-Null
Write-Ok "ACL restricted to $tomcatSvcAccount (read), Administrators (full), SYSTEM (full)"

# === 3. Generate keystore + public cert (only if missing - never overwrite a real prod key) ===
$keystore = Join-Path $secretsDir "oauth2-client.p12"
$certFile = Join-Path $secretsDir "oauth2-client.crt"

if (Test-Path $keystore) {
    Write-Step "Keystore already exists at $keystore - skipping generation"
    Write-Warn2 "If you intended to rotate the key, delete the old .p12 and re-run."
} else {
    Write-Step "Generating 2048-bit RSA PKCS12 keystore (validity $KeyValidityDays days)"
    & $keytool -genkeypair -keyalg RSA -keysize 2048 -alias $KeyAlias `
        -keystore $keystore -storetype PKCS12 `
        -storepass $KeystorePassword -keypass $KeystorePassword `
        -dname $CertSubject -validity $KeyValidityDays
    if ($LASTEXITCODE -ne 0) { throw "keytool -genkeypair failed (exit $LASTEXITCODE)" }

    Write-Step "Exporting public certificate for AS admin team"
    & $keytool -exportcert -alias $KeyAlias `
        -keystore $keystore -storetype PKCS12 -storepass $KeystorePassword `
        -rfc -file $certFile
    if ($LASTEXITCODE -ne 0) { throw "keytool -exportcert failed (exit $LASTEXITCODE)" }

    icacls $keystore /inheritance:r /grant:r "${tomcatSvcAccount}:(R)" "Administrators:(F)" "SYSTEM:(F)" | Out-Null
    icacls $certFile /inheritance:r /grant:r "${tomcatSvcAccount}:(R)" "Administrators:(F)" "SYSTEM:(F)" "Everyone:(R)" | Out-Null
    Write-Ok "Keystore + cert created. Cert can be shared; .p12 must stay on this host only."
}

# === 4. Write Tomcat setenv.bat ===
$setenv = Join-Path $TomcatHome "bin\setenv.bat"
Write-Step "Writing $setenv"
$setenvContent = @"
@echo off
rem AUTO-GENERATED by windows-server-setup.ps1 - edit values as needed but
rem keep the variable names; the WAR reads these at startup.

set "JAVA_HOME=$javaHome"

set "CATALINA_OPTS=-Xms$JvmHeapMin -Xmx$JvmHeapMax -Dspring.profiles.active=$SpringProfile"

set "OAUTH2_CLIENT_ID=$ClientId"
set "OAUTH2_TOKEN_ENDPOINT=$TokenEndpoint"
set "OAUTH2_SCOPE=$Scope"
set "OAUTH2_KEY_ID=$KeyId"

set "OAUTH2_KEYSTORE_PATH=$keystore"
set "OAUTH2_KEYSTORE_PASSWORD=$KeystorePassword"
set "OAUTH2_KEY_ALIAS=$KeyAlias"
set "OAUTH2_KEY_PASSWORD=$KeystorePassword"

set "OAUTH2_API_BASE_URL=$ApiBaseUrl"
"@
[System.IO.File]::WriteAllText($setenv, $setenvContent, [System.Text.Encoding]::ASCII)
icacls $setenv /inheritance:r /grant:r "${tomcatSvcAccount}:(R)" "Administrators:(F)" "SYSTEM:(F)" | Out-Null
Write-Ok "setenv.bat written and locked to $tomcatSvcAccount"

# === 5. Windows Firewall - inbound 8080 ===
$ruleName = "Tomcat 9 (oauth2-jwt-client) HTTP 8080"
$existing = Get-NetFirewallRule -DisplayName $ruleName -ErrorAction SilentlyContinue
if (-not $existing) {
    Write-Step "Opening TCP 8080 inbound in Windows Firewall (Domain + Private)"
    New-NetFirewallRule -DisplayName $ruleName `
        -Direction Inbound -Action Allow -Protocol TCP -LocalPort 8080 `
        -Profile Domain,Private | Out-Null
    Write-Ok "Firewall rule created"
} else {
    Write-Step "Firewall rule already exists - leaving it alone"
}

# === 6. Print next steps ===
$ipv4 = (Get-NetIPAddress -AddressFamily IPv4 -PrefixOrigin Dhcp,Manual -ErrorAction SilentlyContinue |
    Where-Object { $_.IPAddress -notlike '169.254.*' -and $_.IPAddress -ne '127.0.0.1' } |
    Select-Object -First 1).IPAddress

Write-Host ""
Write-Host "============================================================" -ForegroundColor Green
Write-Host "  One-time setup complete." -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Green
Write-Host ""
Write-Host "Server IP for client access  : $ipv4"
Write-Host "Tomcat home                  : $TomcatHome"
Write-Host "Tomcat service               : $TomcatService"
Write-Host "Keystore (server-only)       : $keystore"
Write-Host "Public cert (share with AS)  : $certFile"
Write-Host "setenv.bat                   : $setenv"
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Cyan
Write-Host "  1. Send $certFile to the Authorization Server admin team to register the public key."
Write-Host "  2. Re-open setenv.bat and confirm every OAUTH2_* value is correct for this environment."
Write-Host "  3. Download the WAR from the GitHub Release and deploy:"
Write-Host ""
Write-Host "       Stop-Service $TomcatService" -ForegroundColor Gray
Write-Host "       Invoke-WebRequest \`" -ForegroundColor Gray
Write-Host "         -Uri https://github.com/mahfoudhEbkar/OAuth-2.0-JWT-Bearer-Client-/releases/download/v1.0.0/oauth2-jwt-client.war \`" -ForegroundColor Gray
Write-Host "         -OutFile $TomcatHome\webapps\oauth2-jwt-client.war" -ForegroundColor Gray
Write-Host "       Remove-Item -Recurse -Force $TomcatHome\webapps\oauth2-jwt-client\ -ErrorAction SilentlyContinue" -ForegroundColor Gray
Write-Host "       Start-Service $TomcatService" -ForegroundColor Gray
Write-Host ""
Write-Host "  4. From your laptop or Bruno/Postman, hit:"
Write-Host "       http://${ipv4}:8080/oauth2-jwt-client/api/health"
Write-Host ""
Write-Host "REMINDERS:" -ForegroundColor Yellow
Write-Host "  - The keystore ($keystore) NEVER leaves this server."
Write-Host "  - The .crt file is safe to share - it is the public half."
Write-Host "  - Rotate the keystore password and key well before the $KeyValidityDays-day cert expiry."
