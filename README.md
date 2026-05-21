# oauth2-jwt-client

Spring Boot 2.7.18 WAR that authenticates to an OAuth 2.0 Authorization Server using the **private_key_jwt** method (RFC 7523, `urn:ietf:params:oauth:client-assertion-type:jwt-bearer`), exchanges the assertion for an access token via the `client_credentials` grant, caches the token, and calls a protected third-party API with `Authorization: Bearer {token}`.

- **Owner:** Ebkar, Mahfoudh
- **Java:** builds on 17 today, source-compatible with 1.8 for production
- **Container target:** Apache Tomcat 9 (external)
- **Build tool:** Maven

For the full set of architectural rules, security constraints, and the Java 8 compatibility checklist, see [CLAUDE.md](CLAUDE.md).

## Prerequisites — install these first

Everything you need on a fresh Windows PC. Each tool has a winget one-liner **and** a direct-download fallback for restricted/corporate machines where winget is blocked.

> **About the winget flags.** Every command below includes `--accept-source-agreements --accept-package-agreements`. Without them, winget pauses for a `Y/N` confirmation that **cannot be answered from non-interactive terminals** (IntelliJ's Terminal, VS Code's, scripted runs, etc.) — your install hangs forever and `Y` does nothing. The flags pre-accept both prompts so the install runs straight through.

### Required for every path

#### 1. IntelliJ IDEA Community

| Method | Command / URL |
|---|---|
| winget | `winget install JetBrains.IntelliJIDEA.Community --accept-source-agreements --accept-package-agreements` |
| Direct | <https://www.jetbrains.com/idea/download/?section=windows> (Community, free) |

#### 2. A JDK to build with (recommended: Temurin 17)

> **Why 17 to build but Java 8 to run?** The project compiles to **Java 8 bytecode** (`<java.version>1.8</java.version>` + `<maven.compiler.release>8</maven.compiler.release>` in `pom.xml`) so the WAR runs on the production Java 8 Tomcat 9 host. But Maven needs **JDK 9+** locally to use the `--release 8` flag (JDK 8 itself doesn't support `--release`). Temurin 17 LTS is the team standard — it's what CI uses and what every snippet in this README was tested against.

You don't need to install a JDK system-wide if IntelliJ is enough — pick the option that matches your situation:

- **Option A — Use IntelliJ's bundled JetBrains Runtime (JBR).** Already on disk at `C:\Program Files\JetBrains\IntelliJ IDEA <version>\jbr\` after installing IntelliJ. JBR is JDK 17-based. Includes `keytool`. No extra download.
- **Option B — Let IntelliJ download a JDK for you.** `File` → `Project Structure` → `Project` → `SDK` dropdown → `Add SDK` → **Download JDK** → Vendor: **Eclipse Temurin**, Version: **17**. Lands under `%USERPROFILE%\.jdks\`.
- **Option C — Install system-wide.**

  | Method | Command / URL |
  |---|---|
  | winget | `winget install EclipseAdoptium.Temurin.17.JDK --accept-source-agreements --accept-package-agreements` |
  | Direct | <https://adoptium.net/temurin/releases/?version=17&os=windows&arch=x64> (Temurin 17 LTS, `.msi`) |

For the **Run from IntelliJ** path below, any of A / B / C works. The keytool snippet auto-locates whichever you have. Any JDK ≥ 9 will technically build (11, 21, etc.), but pick 17 unless you have a reason not to — it's the only version CI exercises.

### Optional — only for specific paths

#### 3. Git (only if cloning instead of downloading the ZIP)

| Method | Command / URL |
|---|---|
| winget | `winget install Git.Git --accept-source-agreements --accept-package-agreements` |
| Direct | <https://git-scm.com/downloads/win> |

If you skip Git: download the repo ZIP from GitHub → Code → "Download ZIP" → extract.

#### 4. Apache Tomcat 9 (**only** for the standalone-Tomcat deploy path)

**Skip this** if you're using the **Run from IntelliJ** path — Spring Boot ships an embedded Tomcat 9 inside the JVM.

Tomcat 9 is **not on winget**. Download directly:

1. Go to <https://tomcat.apache.org/download-90.cgi>.
2. Under **9.0.x** → **Binary Distributions** → **Core**, click the **64-bit Windows zip** link.
3. Extract to a path you'll remember, for example `C:\apache-tomcat-9.0.117`.

> Use Tomcat **9**, not 10. Tomcat 10 moved to the `jakarta.*` namespace and is incompatible with Spring Boot 2.7.

> **You do not need OpenSSL.** The included `scripts\generate-dev-keys.bat` and `generate-dev-keys.sh` both use `keytool` (which ships with every JDK) to produce the PKCS12 keystore + the public PEM cert. No third-party crypto tooling required.

### Quick install matrix — what each path needs

| Path | IntelliJ | Build JDK (Temurin 17 rec.) | Tomcat 9 | Git |
|---|---|---|---|---|
| **Run from IntelliJ** (embedded Tomcat) | required | any of A/B/C | not needed | optional |
| **Standalone Tomcat deploy** | optional | required (system-wide) | required | optional |

> The artifact itself (the WAR Maven builds) runs on **Java 8+**. The "JDK to build" column is about the developer/CI machine, not where the WAR runs.

---

## Run from IntelliJ (zero external deps beyond the prereqs above)

Use this path for day-to-day development on any client PC that has IntelliJ IDEA installed. **Nothing else has to be installed first** — JDK is downloaded by IntelliJ, the keystore is generated by JDK's bundled `keytool`, and Tomcat 9 is the same one Spring Boot ships embedded.

End state: app running at `http://localhost:8080/api/health`, started inside IntelliJ's Run window, with hot-reload of code changes.

### 1. Get the project

IntelliJ Welcome screen → **Get from VCS** → URL: `https://github.com/mahfoudhEbkar/OAuth-2.0-JWT-Bearer-Client-.git` → pick a directory → **Clone**.

### 2. Set the Project SDK (Temurin 17) and Language level (8)

`File` → `Project Structure` (`Ctrl+Alt+Shift+S`) → `Project`:

- **SDK** → if empty or older than 17: click `Add SDK` → **Download JDK** → Vendor: **Eclipse Temurin**, Version: **17** → Download. (Any JDK ≥ 9 works, but 17 is the team standard.)
- **Language level:** **8 - Lambdas, type annotations etc.** This matches `<maven.compiler.release>8</maven.compiler.release>` in `pom.xml`. After a Maven reimport, IntelliJ should auto-sync this — if it shows 17 instead, set it manually so the editor flags Java 9+ syntax (like `var` or `List.of(...)`) before you even compile.
- **OK**.

Maven reimports automatically. Wait for the bottom-right progress bar to clear.

### 3. Generate the keystore (IntelliJ Terminal)

`View` → `Tool Windows` → `Terminal`. The default Win 11 shell is PowerShell — paste:

This snippet finds `keytool.exe` from **any** JDK on the machine (IntelliJ-downloaded, standalone Temurin/Java/Microsoft/etc., or IntelliJ's own bundled JetBrains Runtime), so it works even when no JDK is on the system `PATH`:

```powershell
$searchPaths = @(
    "$env:USERPROFILE\.jdks",
    "C:\Program Files\Eclipse Adoptium",
    "C:\Program Files\Java",
    "C:\Program Files\Microsoft",
    "C:\Program Files\BellSoft",
    "C:\Program Files\Amazon Corretto",
    "C:\Program Files\Zulu",
    "C:\Program Files\JetBrains"
)
$keytool = $null
foreach ($p in $searchPaths) {
    if (Test-Path $p) {
        $found = Get-ChildItem -Path $p -Recurse -Filter "keytool.exe" -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($found) { $keytool = $found.FullName; break }
    }
}
if (-not $keytool) { $cmd = Get-Command keytool -ErrorAction SilentlyContinue; if ($cmd) { $keytool = $cmd.Source } }
if (-not $keytool) { throw "keytool.exe not found - install a JDK or open Project Structure -> SDKs -> Download JDK first" }
Write-Host "Using: $keytool"

New-Item -ItemType Directory -Force keys | Out-Null
& $keytool -genkeypair -keyalg RSA -keysize 2048 -alias oauth2-client -keystore keys\oauth2-client.p12 -storetype PKCS12 -storepass changeit -keypass changeit -dname "CN=oauth2-jwt-client, O=Odea, C=US" -validity 365
& $keytool -exportcert -alias oauth2-client -keystore keys\oauth2-client.p12 -storetype PKCS12 -storepass changeit -rfc -file keys\oauth2-client.crt
```

`keytool` ships with every JDK — no OpenSSL needed. Produces the PKCS12 keystore the app expects (alias `oauth2-client`, password `changeit`).

> **If you see** `keytool : The term 'keytool' is not recognized…` from a different snippet, your JDK's `bin/` is not on system PATH. The auto-locating snippet above handles this — use it instead.

The `keys/` folder is `.gitignored`, so this stays local to the machine.

### 4. Note the project root path

In the **Project** pane (left), right-click the top folder → `Copy Path/Reference…` → **Absolute Path**. You'll paste this into the next step. Call this value `<PROJECT_ROOT>`.

### 5. Create the Spring Boot run configuration

`Run` → `Edit Configurations…` → **`+`** → **Spring Boot** (built-in, no plugin needed):

| Field | Value |
|---|---|
| **Name** | `oauth2-jwt-client` |
| **Main class** | `com.odea.oauth2client.Application` |
| **Active profiles** | `dev` |
| **Working directory** | leave default (project root) |

### 6. Set environment variables

In the same dialog, click the icon next to **Environment variables** and paste — replacing `<PROJECT_ROOT>` with the path from Step 4:

```
OAUTH2_CLIENT_ID=smoketest-client
OAUTH2_TOKEN_ENDPOINT=https://auth.example.com/oauth2/token
OAUTH2_SCOPE=api.read
OAUTH2_KEYSTORE_PATH=<PROJECT_ROOT>\keys\oauth2-client.p12
OAUTH2_KEYSTORE_PASSWORD=changeit
OAUTH2_KEY_ALIAS=oauth2-client
OAUTH2_KEY_PASSWORD=changeit
OAUTH2_API_BASE_URL=https://api.example.com
```

When you have real credentials from your AS admin, replace these values and re-run.

**Apply** → **OK**.

### 7. Run

Click the green ▶ in the top toolbar. In the **Run** window watch for:
```
Loaded RSA key from keystore: alias=oauth2-client, kid=oauth2-client
Tomcat started on port(s): 8080 (http) with context path ''
Started Application in N seconds
```

### 8. Verify

Browser: **`http://localhost:8080/api/health`**

Expected:
```json
{"status":"UP","clientId":"smok****"}
```

Stop with the red ■ in the Run window.

---

## Deploy to standalone Tomcat 9 via Smart Tomcat (IntelliJ)

Use this path when you have a separate Tomcat 9 installation and want IntelliJ to deploy the WAR into it (mimics production layout). End state: app running at `http://localhost:8080/oauth2-jwt-client/api/health`, served by the external Tomcat process, started and stopped from inside IntelliJ.

### Prereqs for this path

- **IntelliJ** + **build JDK (Temurin 17 recommended)** — see Prerequisites #1 and #2 above. The WAR itself targets Java 8 bytecode; 17 is just the JDK on your build machine.
- **Tomcat 9** extracted to a known folder — see Prerequisites #4. Example: `C:\apache-tomcat-9.0.117`.
- **SmartTomcat plugin** — `File` → `Settings` → `Plugins` → **Marketplace** tab → search **`SmartTomcat`** (one word) → **Install** → restart IDE.

### 1. Open the project

`File` → `New` → `Project from Version Control` → URL: `https://github.com/mahfoudhEbkar/OAuth-2.0-JWT-Bearer-Client-.git` → Clone. Or open the existing folder via `File` → `Open` and select `pom.xml`.

Wait for Maven to finish importing (bottom-right progress bar).

### 2. Generate the keystore (no script needed)

`View` → `Tool Windows` → `Terminal`. Paste this **whole block** into the terminal and press Enter — it auto-locates `keytool.exe` inside the Temurin JDK you installed, no script file or `git pull` needed:

```powershell
$searchPaths = @(
    "$env:USERPROFILE\.jdks",
    "C:\Program Files\Eclipse Adoptium",
    "C:\Program Files\Java",
    "C:\Program Files\Microsoft",
    "C:\Program Files\BellSoft",
    "C:\Program Files\Amazon Corretto",
    "C:\Program Files\Zulu",
    "C:\Program Files\JetBrains"
)
$keytool = $null
foreach ($p in $searchPaths) {
    if (Test-Path $p) {
        $found = Get-ChildItem -Path $p -Recurse -Filter "keytool.exe" -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($found) { $keytool = $found.FullName; break }
    }
}
if (-not $keytool) { throw "keytool.exe not found - install Temurin 17 or download a JDK via Project Structure first" }
Write-Host "Using: $keytool"

New-Item -ItemType Directory -Force keys | Out-Null
& $keytool -genkeypair -keyalg RSA -keysize 2048 -alias oauth2-client -keystore keys\oauth2-client.p12 -storetype PKCS12 -storepass changeit -keypass changeit -dname "CN=oauth2-jwt-client, O=Odea, C=US" -validity 365
& $keytool -exportcert -alias oauth2-client -keystore keys\oauth2-client.p12 -storetype PKCS12 -storepass changeit -rfc -file keys\oauth2-client.crt
```

Expected output (path will reflect your install):
```
Using: C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot\bin\keytool.exe
Generating 2,048 bit RSA key pair and self-signed certificate (SHA256withRSA) with a validity of 365 days
        for: CN=oauth2-jwt-client, O=Odea, C=US
Certificate stored in file <keys\oauth2-client.crt>
```

Two files created: `keys\oauth2-client.p12` (the keystore the app loads) and `keys\oauth2-client.crt` (the public certificate you hand to the AS admin once you go beyond local smoke testing).

> **You do not need OpenSSL or cryptotools.net or any other tool.** `keytool.exe` is part of the Temurin JDK you installed in Prereq #2. The snippet finds it automatically and uses it to produce the exact PKCS12 format the app's `KeyStoreLoader` expects, with the alias `oauth2-client` set correctly.

### 3. Build the exploded WAR

Smart Tomcat deploys a directory layout, not a `.war` file. Build it once with Maven:

In the **Maven** tool window (right edge → "Maven" tab) → `oauth2-jwt-client` → **Lifecycle** → double-click **`package`**.

Wait for **BUILD SUCCESS**. This creates `target\oauth2-jwt-client\` (with `WEB-INF/classes/` and `WEB-INF/lib/` inside) — that directory is what Smart Tomcat deploys.

### 4. Get the absolute path to the keystore file

You'll paste this into the env var in Step 6. In the IntelliJ Terminal (which is PowerShell on Windows by default), run:

```powershell
"$PWD\keys\oauth2-client.p12"
```

It prints the full absolute path to the keystore file you just created. **Copy that line exactly** — including the `\oauth2-client.p12` at the end. Example output:

```
C:\Users\you\git\OAuth-2.0-JWT-Bearer-Client-\keys\oauth2-client.p12
```

> Don't use `echo %CD%` — that's `cmd` syntax and prints the literal `%CD%` in PowerShell. Use `$PWD` instead.

> **About placeholders in this README.** Anywhere you see something in angle brackets like `<PROJECT_ROOT>`, `<your-path>`, or `<vendor>`, that's a placeholder you must **replace entirely** (brackets and all) with the real value on your machine. Pasting the literal text `<PROJECT_ROOT>` as a value is the most common failure on this path.

### 5. Create the Smart Tomcat run config

`Run` → `Edit Configurations…` → click **`+`** → choose **Smart Tomcat**. Fill these fields:

| Field | Value |
|---|---|
| **Name** | `oauth2-jwt-client` |
| **Tomcat Server** | Click `Configure` → **`+`** → Name: `Tomcat 9`, Tomcat Home: your Tomcat install dir (e.g. `C:\apache-tomcat-9.0.117`) → OK. Pick it from the dropdown. |
| **Deployment directory** | `<PROJECT_ROOT>\target\oauth2-jwt-client` |
| **Context path** | `/oauth2-jwt-client` |
| **Server port** | `8080` |
| **Admin port** | `8005` (default) |

### 6. Set environment variables

Same dialog → find **Environment variables** → click the small **`…`** icon next to the field to open the multi-row editor.

Add these 8 rows. **For `OAUTH2_KEYSTORE_PATH`, paste the exact absolute path you got in Step 4** — not the placeholder:

| Name | Value |
|---|---|
| `OAUTH2_CLIENT_ID` | `smoketest-client` |
| `OAUTH2_TOKEN_ENDPOINT` | `https://auth.example.com/oauth2/token` |
| `OAUTH2_SCOPE` | `api.read` |
| `OAUTH2_KEYSTORE_PATH` | (paste Step 4 output here — ends in `\oauth2-client.p12`) |
| `OAUTH2_KEYSTORE_PASSWORD` | `changeit` |
| `OAUTH2_KEY_ALIAS` | `oauth2-client` |
| `OAUTH2_KEY_PASSWORD` | `changeit` |
| `OAUTH2_API_BASE_URL` | `https://api.example.com` |

> **Critical**: the `OAUTH2_KEYSTORE_PATH` value must end with `\oauth2-client.p12` (the filename), **not** with `\keys` (the folder). Drag the Value column wider in the dialog to verify the full path is what you intend — IntelliJ truncates long values in the display.

When you have real credentials from your AS admin, edit these values and re-run.

### 7. Auto-rebuild the WAR on every Run

Same dialog → bottom **Before launch** section → **`+`** → **Run Maven Goal** → Command line: `package -DskipTests` → OK.

Without this step, code changes won't appear in subsequent runs (Smart Tomcat would keep deploying the stale exploded WAR from Step 3).

### 8. Apply → OK → click the green ▶

In the **Run** tool window watch for:
```
Loaded RSA key from keystore: alias=oauth2-client, kid=oauth2-client
Server startup in [N] milliseconds
```

### 9. Verify

Open in browser: **`http://localhost:8080/oauth2-jwt-client/api/health`**

Expected response:
```json
{"status":"UP","clientId":"smok****"}
```

Stop the server with the red ■ in the Run window.

### Common failures on this path

| Symptom | Fix |
|---|---|
| `Could not find openssl.exe` from `scripts\generate-dev-keys.bat` | Your local copy of the script is stale. Use the PowerShell snippet in Step 2 above instead — don't run the `.bat`. |
| `Keystore file not found: ${OAUTH2_KEYSTORE_PATH}` | You never set the env var. Spring left the literal placeholder. Open Run → Edit Configurations and add the env vars (Step 6). |
| `Keystore file not found: <PROJECT_ROOT>\keys\oauth2-client.p12` | You pasted the literal placeholder `<PROJECT_ROOT>` as the env var value. Replace it with the actual absolute path from Step 4. |
| `Keystore file not found: C:\...\keys` (path ends at folder, no filename) | Your env var ends at `\keys` instead of `\keys\oauth2-client.p12`. Append `\oauth2-client.p12` to the value. |
| `keytool error: Key pair not generated, alias <oauth2-client> already exists` | A leftover keystore from a previous run is in the way. Delete the `keys` folder (`Remove-Item -Recurse -Force keys` in PowerShell) and re-run Step 2. |
| `keystore was tampered with, or password was incorrect` | The keystore was created with a different password than what's in your env vars. Defaults are `changeit` everywhere — delete `keys` folder, re-run Step 2. |
| Smart Tomcat: `Deployment directory does not exist` | You skipped Step 3. Build with `mvn package` (or the Maven panel's `package` lifecycle) first, then re-open the run config. |
| Browser shows 404 at `/oauth2-jwt-client/api/health` | Either the WAR didn't deploy (check the Run window for stack traces) or the context path in Step 5 doesn't match what you typed in the URL. |
| `localhost:8080/api/health` returns 404 but `localhost:8080/oauth2-jwt-client/api/health` would have worked | The Smart Tomcat (standalone) path uses the WAR filename as context root. Always include `/oauth2-jwt-client/` in the URL on this path. |
| Tomcat refuses to start: port 8080 in use | Another process owns 8080. Run `netstat -ano \| findstr :8080` in the terminal, kill the PID with `taskkill /F /PID <pid>`, then click ▶ again. |
| `echo %CD%` prints the literal `%CD%` | You're in PowerShell, which doesn't use `%VAR%` syntax. Use `$PWD` or `$PWD.Path` instead. |

---

## Quick start

### 1. Generate development keys

```bash
# Linux / macOS / Git Bash on Windows
bash scripts/generate-dev-keys.sh

# Windows cmd
scripts\generate-dev-keys.bat
```

Creates `keys/oauth2-client.p12` (default password `changeit`, alias `oauth2-client`) and `keys/oauth2-client.crt`. Hand the `.crt` to the AS admin so they can register it against your `client_id`.

### 2. Set environment variables

| Variable | Required | Example |
|---|---|---|
| `OAUTH2_CLIENT_ID` | yes | `acme-prod-client-001` |
| `OAUTH2_TOKEN_ENDPOINT` | yes | `https://auth.acme.com/oauth2/token` |
| `OAUTH2_SCOPE` | no | `api.read api.write` |
| `OAUTH2_KEY_ID` | no | `oauth2-client-2026q2` |
| `OAUTH2_KEYSTORE_PATH` | yes | `./keys/oauth2-client.p12` |
| `OAUTH2_KEYSTORE_PASSWORD` | yes | `changeit` |
| `OAUTH2_KEY_ALIAS` | yes | `oauth2-client` |
| `OAUTH2_KEY_PASSWORD` | yes | `changeit` |
| `OAUTH2_API_BASE_URL` | yes | `https://api.acme.com` |

#### macOS / Linux

```bash
export OAUTH2_CLIENT_ID=your-client-id
export OAUTH2_TOKEN_ENDPOINT=https://auth.example.com/oauth2/token
export OAUTH2_SCOPE=api.read
export OAUTH2_KEYSTORE_PATH=./keys/oauth2-client.p12
export OAUTH2_KEYSTORE_PASSWORD=changeit
export OAUTH2_KEY_ALIAS=oauth2-client
export OAUTH2_KEY_PASSWORD=changeit
export OAUTH2_API_BASE_URL=https://api.example.com
```

#### Windows PowerShell

```powershell
$env:OAUTH2_CLIENT_ID = "your-client-id"
$env:OAUTH2_TOKEN_ENDPOINT = "https://auth.example.com/oauth2/token"
$env:OAUTH2_SCOPE = "api.read"
$env:OAUTH2_KEYSTORE_PATH = ".\keys\oauth2-client.p12"
$env:OAUTH2_KEYSTORE_PASSWORD = "changeit"
$env:OAUTH2_KEY_ALIAS = "oauth2-client"
$env:OAUTH2_KEY_PASSWORD = "changeit"
$env:OAUTH2_API_BASE_URL = "https://api.example.com"
```

### 3. Run with embedded Tomcat (dev)

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

App listens on `http://localhost:8080`.

### 4. Build the WAR

```bash
mvn clean package
```

Output: `target/oauth2-jwt-client.war`.

### 5. Deploy to standalone Apache Tomcat 9

1. Set the same env vars in `$CATALINA_HOME/bin/setenv.sh` (Linux/Mac) or `setenv.bat` (Windows).
2. Place the keystore at `$CATALINA_HOME/conf/oauth2-client.p12` and `chmod 600` it (Linux/Mac).
3. Copy the WAR: `cp target/oauth2-jwt-client.war $CATALINA_HOME/webapps/`
4. Restart Tomcat: `$CATALINA_HOME/bin/shutdown.sh && $CATALINA_HOME/bin/startup.sh`

Verify: `curl http://localhost:8080/oauth2-jwt-client/api/health`

## Smoke test

```bash
# 1. App is up, config bound, keystore loaded
curl http://localhost:8080/api/health
# -> {"status":"UP","clientId":"abcd****"}

# 2. Triggers JWT assertion -> token fetch -> upstream API call
curl http://localhost:8080/api/proxy/your/api/path

# 3. Repeat within 5 minutes - logs should NOT show "Acquired access token" again
curl http://localhost:8080/api/proxy/your/api/path
```

## HTTP API

| Method | Path | Description |
|---|---|---|
| GET | `/api/health` | `{"status":"UP","clientId":"<masked>"}` |
| GET | `/api/proxy/{*path}` | Forwards to `OAUTH2_API_BASE_URL/{path}` with bearer token |
| POST | `/api/crypto/encrypt` | RSA-encrypts a JSON string field with the keystore's public key; returns base64 ciphertext |
| POST | `/api/crypto/decrypt` | RSA-decrypts a base64 ciphertext with the keystore's private key; returns the original plaintext |

### RSA encrypt / decrypt — Postman or curl

Endpoint: **`POST http://localhost:8080/api/crypto/encrypt`** (embedded) or **`http://localhost:8080/oauth2-jwt-client/api/crypto/encrypt`** (standalone Tomcat).

Headers:
```
Content-Type: application/json
```

Request body:
```json
{
  "data": "hello world {\"x\":42}"
}
```

Response (HTTP 200):
```json
{
  "algorithm": "RSA/ECB/OAEPWithSHA-256AndMGF1Padding",
  "encrypted": "F/ik9nsEpCzWvQVocTFCDrlzso12hfGVId13DaWdDvKSCAMygXFFJkEmKinJZmp0ETipsgsdH7lXlUuwANg3Gw...",
  "ciphertextLength": 344
}
```

Roundtrip with `POST /api/crypto/decrypt`:
```json
{
  "encrypted": "<paste the value from /encrypt here>"
}
```

Returns:
```json
{
  "algorithm": "RSA/ECB/OAEPWithSHA-256AndMGF1Padding",
  "data": "hello world {\"x\":42}"
}
```

curl equivalents:
```bash
curl -X POST http://localhost:8080/api/crypto/encrypt \
  -H "Content-Type: application/json" \
  -d '{"data":"hello world"}'

curl -X POST http://localhost:8080/api/crypto/decrypt \
  -H "Content-Type: application/json" \
  -d '{"encrypted":"<base64>"}'
```

**Algorithm:** `RSA/ECB/OAEPWithSHA-256AndMGF1Padding` (modern OAEP, IND-CCA2 secure). The "ECB" token in the JCE transformation name is a historical misnomer — it does not mean ECB block-cipher mode (RSA is not a block cipher).

**Size limit:** With a 2048-bit RSA key + OAEP-SHA-256 padding, the maximum plaintext is ~190 bytes. Inputs larger than that return HTTP 400:
```json
{"error":"crypto_failure","message":"Plaintext is too large for direct RSA encryption (300 bytes). Max ~190 bytes for RSA-2048 with OAEP-SHA-256. For larger payloads use a hybrid scheme (AES + RSA-wrapped key)."}
```

For arbitrary-length payloads (entire JSON files, large records), use **hybrid encryption** — generate a random AES-256 key, encrypt the data with AES-GCM, wrap the AES key with RSA, return both. Easy extension to this controller if needed; not implemented today.

## Java 8 target (already configured)

The project compiles to **Java 8 bytecode** by default. No flag flipping needed before production. In `pom.xml`:

```xml
<properties>
    <java.version>1.8</java.version>
    <maven.compiler.release>8</maven.compiler.release>
    ...
</properties>
```

What this gets you:

| Aspect | Value | Why |
|---|---|---|
| Bytecode major version | 52 (Java 8) | runs on the production Tomcat 9 host's Java 8 JVM |
| Compile-time API surface | Java 8 only | `--release 8` blocks `List.of`, `Optional.isEmpty`, `Stream.toList`, text blocks, etc. at compile time, not at runtime |
| Build JDK | 9+ (Temurin 17 recommended) | `--release` requires JDK 9+. JDK 8 itself cannot build this project |
| Source syntax | Java 8 only | `var`, records, sealed classes, switch expressions, etc. are blocked |
| Runtime JVM (where WAR runs) | Java 8 or higher | works on production Java 8; also works on 11, 17, 21 |

Verify the bytecode version on any WAR:
```powershell
# Class files start with magic ca fe ba be then 2 bytes for minor + 2 for major version.
# Java 8 = 52 = 0x34.
unzip -p target/oauth2-jwt-client.war WEB-INF/classes/com/odea/oauth2client/Application.class | head -c 8 | Format-Hex
# expected last byte: 34
```

The full "do not use these Java 9+ features" checklist lives in [CLAUDE.md](CLAUDE.md#java-8-compatibility-checklist).

## Building the WAR

```powershell
mvn clean package
# produces: target/oauth2-jwt-client.war
```

The same `.war` runs **two ways**:

**Mode A — standalone (no external Tomcat needed)**
```powershell
java -jar target/oauth2-jwt-client.war
# Spring Boot launcher boots embedded Tomcat 9 from WEB-INF/lib-provided
```

**Mode B — drop into an existing Tomcat 9**
```powershell
Copy-Item target/oauth2-jwt-client.war $env:CATALINA_HOME/webapps/
# external Tomcat ignores WEB-INF/lib-provided and uses its own server libs
```

If a downstream tool insists on a `.jar` extension, just copy:
```powershell
Copy-Item target/oauth2-jwt-client.war target/oauth2-jwt-client.jar
# Spring Boot launcher doesn't care about extension
```
Do **not** change `<packaging>` to `jar` in `pom.xml` — that breaks the external-Tomcat deploy path (see CLAUDE.md hard constraints).

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `keystore was tampered with, or password was incorrect` | Wrong `OAUTH2_KEYSTORE_PASSWORD` | Default is `changeit` (see `scripts/generate-dev-keys.sh`) |
| `KeyStore type 'pkcs12' not found` | Wrong keystore format | Regenerate with the script. JKS not supported by this code |
| `invalid_client` from AS | Public cert not registered | Send `keys/oauth2-client.crt` to AS admin |
| `invalid_client_assertion` from AS | `aud` claim mismatch | Some servers expect the AS issuer URL, not the token endpoint URL |
| `connect timed out` to token endpoint | Firewall / proxy / wrong URL | Test with `curl -v` from the same host |
| Tomcat 404 on `/oauth2-jwt-client/api/health` | WAR didn't deploy | Check `catalina.out` for missing env vars during context start |
| `ClassNotFoundException: javax.servlet.*` | Servlet API conflict | `spring-boot-starter-tomcat` must be `provided` scope |
| Spring Boot won't start: `Servlet API 5.0` | Tomcat 10 in use | Use Tomcat 9. Tomcat 10 moved to `jakarta.*`, incompatible with Spring Boot 2.7 |
