# Deployment

Same WAR, two run modes. Pick by what the target server has.

## Mode A — Standalone (embedded Tomcat)

For servers where the WAR itself brings its own Tomcat. Docker containers, systemd services, or one-app boxes.

```powershell
java -jar oauth2-jwt-client.war
```

App listens on **`:8080`** (no context prefix):
```
http://<server>:8080/api/health
```

Change the port with `--server.port=9090` or `SERVER_PORT=9090` env var.

## Mode B — External Tomcat 9 (production pattern)

For servers with an existing Tomcat installation and a sysadmin who manages the container. Drop the WAR into `webapps/`; Tomcat auto-explodes and starts it.

```powershell
Copy-Item oauth2-jwt-client.war $env:CATALINA_HOME\webapps\
Restart-Service Tomcat9
```

App listens on Tomcat's port with the **WAR filename as context prefix**:
```
http://<server>:8080/oauth2-jwt-client/api/health
```

**Tomcat 9 only.** Tomcat 10 moved to the `jakarta.*` namespace and breaks Spring Boot 2.7.

---

## Deploying to a Windows Server (production, step-by-step)

Fully automated by [`scripts/windows-server-setup.ps1`](https://github.com/mahfoudhEbkar/OAuth-2.0-JWT-Bearer-Client-/blob/main/scripts/windows-server-setup.ps1). Run it **once per host** as Administrator.

### One-time host setup

```powershell
# On the server, PowerShell as Administrator:
git clone https://github.com/mahfoudhEbkar/OAuth-2.0-JWT-Bearer-Client-.git
cd OAuth-2.0-JWT-Bearer-Client-

powershell -ExecutionPolicy Bypass -File .\scripts\windows-server-setup.ps1 `
  -TomcatHome        "C:\apache-tomcat-9.0.117" `
  -ClientId          "REPLACE-WITH-YOUR-CLIENT-ID" `
  -TokenEndpoint     "https://as.example.com/oauth2/token" `
  -ApiBaseUrl        "https://api.example.com" `
  -KeystorePassword  "REPLACE-WITH-STRONG-PASSWORD"
```

The script:

1. Verifies (or installs via winget) Temurin 8.
2. Creates `<TomcatHome>\conf\oauth2\` and locks its NTFS ACL to `NT SERVICE\Tomcat9` (read) + Administrators (full).
3. Generates a 2048-bit RSA PKCS12 keystore + public cert there — **only** if the keystore doesn't already exist. Idempotent.
4. Writes `<TomcatHome>\bin\setenv.bat` with every `OAUTH2_*` env var Tomcat picks up at startup.
5. Opens TCP 8080 inbound in Windows Firewall (Domain + Private).
6. Prints the server IPv4 and next-step commands.

Give the printed `.crt` file to the AS admin team to register the public key. **Never** send the `.p12`.

### Every subsequent deploy — three commands

```powershell
$tomcat = "C:\apache-tomcat-9.0.117"

Stop-Service Tomcat9
Invoke-WebRequest `
  -Uri https://github.com/mahfoudhEbkar/OAuth-2.0-JWT-Bearer-Client-/releases/download/v1.0.0/oauth2-jwt-client.war `
  -OutFile "$tomcat\webapps\oauth2-jwt-client.war"
Remove-Item -Recurse -Force "$tomcat\webapps\oauth2-jwt-client\" -ErrorAction SilentlyContinue
Start-Service Tomcat9
```

Watch the log until you see `Started Application`:
```powershell
Get-Content "$tomcat\logs\catalina.*.log" -Wait -Tail 30
```

### Verify

```powershell
curl http://<server-ip>:8080/oauth2-jwt-client/api/health
# Expect: {"status":"UP","clientId":"your****"}

curl.exe -X POST http://<server-ip>:8080/oauth2-jwt-client/api/crypto/encrypt `
  -H "Content-Type: application/json" `
  -d '{"data":{"firstname":"mahfoudh"}}'
# Expect: 200 + {"algorithm":"...","encrypted":"...","ciphertextLength":344}
```

---

## Why the keystore lives **outside** the WAR

A deliberate security boundary. Putting the `.p12` inside the WAR means anyone with the WAR file — a GitHub Release attachment, a backup, a Docker image layer, a Maven mirror, a USB stick — has the private key. Rotation becomes "rebuild the artifact" instead of "swap a file and restart." Per-environment keys (dev / staging / prod must be different) become three separate WAR builds.

External keystore + `OAUTH2_KEYSTORE_PATH` env var = one WAR per release, N keystores (one per host / environment), independent rotation. `windows-server-setup.ps1` makes external-keystore as low-friction as bundled — one command, done.

## Env vars the server needs

The complete list is on [Configuration](Configuration). Required at minimum:

| Env var | Example |
|---|---|
| `OAUTH2_CLIENT_ID` | your client id at the AS |
| `OAUTH2_TOKEN_ENDPOINT` | `https://as.example.com/oauth2/token` |
| `OAUTH2_KEYSTORE_PATH` | path on the server |
| `OAUTH2_KEYSTORE_PASSWORD` | strong password (not `changeit`) |
| `OAUTH2_KEY_ALIAS` | `oauth2-client` (default) |
| `OAUTH2_KEY_PASSWORD` | strong password |
| `OAUTH2_API_BASE_URL` | protected API root |

## GitHub Release as the artifact source

Each tagged release attaches the WAR — no need to build on the server:

<https://github.com/mahfoudhEbkar/OAuth-2.0-JWT-Bearer-Client-/releases>

Verify the downloaded WAR matches the release checksum:
```powershell
(Get-FileHash oauth2-jwt-client.war -Algorithm SHA256).Hash
# For v1.0.0: fc111de869e399906348963079bf50e78268bfe5bb42dad41188fa83c2af671f
```

## Rollback

Rollback = deploy the previous tag's WAR. Same three-command loop, different release URL. No code state on the server changes.
