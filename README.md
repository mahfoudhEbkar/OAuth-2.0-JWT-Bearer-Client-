# oauth2-jwt-client

Spring Boot 2.7.18 WAR that authenticates to an OAuth 2.0 Authorization Server using the **private_key_jwt** method (RFC 7523, `urn:ietf:params:oauth:client-assertion-type:jwt-bearer`), exchanges the assertion for an access token via the `client_credentials` grant, caches the token, and calls a protected third-party API with `Authorization: Bearer {token}`.

- **Owner:** Ebkar, Mahfoudh
- **Java:** JDK 1.8 end-to-end (build, bytecode, runtime)
- **Container target:** Apache Tomcat 9 (external) **or** embedded (`java -jar`)
- **Build tool:** Maven

For the full set of architectural rules, security constraints, and the Java 8 compatibility checklist, see [CLAUDE.md](CLAUDE.md).

## Recent changes (read this before re-cloning on a client PC)

If you cloned an older version of this repo and are pulling for the first time, two things changed and you may need to react.

### 1. The project now targets JDK 1.8 end-to-end (was JDK 17 build / Java 8 runtime)

- `pom.xml` now has `<java.version>1.8</java.version>` and **no** `<maven.compiler.release>` entry. Source, bytecode, build JDK, and runtime are all Java 8.
- CI builds on **Temurin 8** (was Temurin 17). The previous separate "Java 8 source-compat check" job is gone — building on Temurin 8 inherently enforces the Java 8 API surface, so the second job became redundant.
- The build JDK on **your machine** should be Temurin 8 to exactly match CI. If you still have Temurin 17 installed, the build will still succeed locally (Maven happily produces Java 8 bytecode from JDK 17), but a Java 9+ API reference would slip past your local build and only get caught when you push to CI. To install Temurin 8 alongside any existing JDK on Windows: `winget install EclipseAdoptium.Temurin.8.JDK --accept-source-agreements --accept-package-agreements`.
- In IntelliJ: `File` → `Project Structure` → `Project` → **SDK = Temurin 8**, **Language level = 8**.
- The previous "Switching to Java 1.8 for production" section in this README is gone — there is nothing to switch anymore.

### 2. The built WAR is now a dual-mode artifact (Tomcat-deployable **and** runnable as `java -jar`)

- `mvn clean package` produces a single file: `target/oauth2-jwt-client.war`.
- This **same** WAR runs two ways:
  - **(a) Standalone, no external server:** `java -jar target/oauth2-jwt-client.war`. Spring Boot's launcher boots an embedded Tomcat 9 from `WEB-INF/lib-provided/` inside the WAR.
  - **(b) Deployed to a real Tomcat 9:** `cp target/oauth2-jwt-client.war $CATALINA_HOME/webapps/`. External Tomcat ignores `WEB-INF/lib-provided/` and uses its own server libraries — no conflict.
- We did **not** add a separate JAR build. One artifact, two deployment modes. If a downstream tool wants a `.jar` filename, just copy: `cp target/oauth2-jwt-client.war target/oauth2-jwt-client.jar`. The Spring Boot launcher does not care about the extension.
- Do **not** change `<packaging>war</packaging>` to `jar` in `pom.xml` — it breaks the Tomcat-deploy path. CLAUDE.md flags this as a hard constraint.

### 3. Crypto endpoints now accept and return native JSON (no `\"` escaping)

- `POST /api/crypto/encrypt` — the `data` field now accepts a JSON **object**, **array**, or **string**. Sending `{"data":{"firstname":"mahfoudh","id":42}}` works directly; you no longer have to encode it as `{"data":"{\"firstname\":\"mahfoudh\"}"}`.
- `POST /api/crypto/decrypt` — when the plaintext was a JSON object or array, the response field `data` comes back as a real object/array (no `\"` in sight). When the plaintext was a plain string, `data` is still a string. Round-trips preserve shape.
- Validation failures (e.g. missing `data`) now return **HTTP 400** with a structured `{ "error": "validation_failure", "fields": [...] }` body instead of a generic 500.
- The underlying crypto and the keystore behavior are unchanged — same RSA-OAEP-SHA-256 algorithm, same ~190-byte plaintext limit. Only the controller's request/response shape changed.
- See [Recent changes #4 below](#api-endpoints) for the full updated payload examples.

### What did **not** change

- All OAuth 2.0 logic, JWT claim building, token caching, and retry-on-401 are unchanged.
- The RSA algorithm, key handling, and size limits (`/api/crypto/*` business logic) are unchanged — only the HTTP-layer JSON shape changed.
- The keystore format (PKCS12), keystore generation script, and env-var configuration surface are unchanged.
- Spring Boot stays on 2.7.18, Nimbus JOSE on 9.37.3, Tomcat target stays at 9.x (not 10).
- The location of secrets (env vars, not the repo) is unchanged. `keys/` is still git-ignored.

### What you must do on a client PC after pulling

1. `git pull origin main`.
2. Install Temurin 8 if you don't already have it (see Prereq #2 below).
3. In IntelliJ, set Project SDK to Temurin 8 and Language level to 8 (one-time, see "Set the Project SDK" step below).
4. `mvn clean package` — you should see `BUILD SUCCESS` and 16 tests passing.
5. (Optional) confirm the WAR is Java 8 bytecode: `unzip -p target/oauth2-jwt-client.war WEB-INF/classes/com/odea/oauth2client/Application.class | head -c 8 | od -An -tx1` — last byte should be `34` (= class major version 52 = Java 8).
6. Run it either way: `java -jar target/oauth2-jwt-client.war` OR drop into your Tomcat 9 `webapps/` directory.

## Prerequisites — install these first

Everything you need on a fresh Windows PC. Each tool has a winget one-liner **and** a direct-download fallback for restricted/corporate machines where winget is blocked.

> **About the winget flags.** Every command below includes `--accept-source-agreements --accept-package-agreements`. Without them, winget pauses for a `Y/N` confirmation that **cannot be answered from non-interactive terminals** (IntelliJ's Terminal, VS Code's, scripted runs, etc.) — your install hangs forever and `Y` does nothing. The flags pre-accept both prompts so the install runs straight through.

### Required for every path

#### 1. IntelliJ IDEA Community

| Method | Command / URL |
|---|---|
| winget | `winget install JetBrains.IntelliJIDEA.Community --accept-source-agreements --accept-package-agreements` |
| Direct | <https://www.jetbrains.com/idea/download/?section=windows> (Community, free) |

#### 2. JDK 1.8 (Temurin 8)

> **Why JDK 8 specifically?** The project is **Java 8 end-to-end** — `<java.version>1.8</java.version>` in `pom.xml`, Java 8 bytecode out, runs on the production Java 8 Tomcat 9 host. We use JDK 8 to build too (not 11/17/21) so that any accidental use of a Java 9+ API (`List.of`, `Optional.isEmpty`, `Stream.toList`, text blocks, etc.) fails at `mvn compile`, not at runtime on the production JVM. CI uses Temurin 8 for the same reason.

You don't need to install a JDK system-wide if IntelliJ can download one for the project — pick the option that matches your situation:

- **Option A — Let IntelliJ download a JDK for you (recommended for the IntelliJ path).** `File` → `Project Structure` → `Project` → `SDK` dropdown → `Add SDK` → **Download JDK** → Vendor: **Eclipse Temurin**, Version: **8**. Lands under `%USERPROFILE%\.jdks\`. Includes `keytool`.
- **Option B — Install system-wide.**

  | Method | Command / URL |
  |---|---|
  | winget | `winget install EclipseAdoptium.Temurin.8.JDK --accept-source-agreements --accept-package-agreements` |
  | Direct | <https://adoptium.net/temurin/releases/?version=8&os=windows&arch=x64> (Temurin 8 LTS, `.msi`) |

> **Note on IntelliJ's bundled JetBrains Runtime (JBR).** JBR is JDK 17-based and is what runs IntelliJ itself — it is **not** suitable as this project's build JDK. Use Option A or B above; the snippet finds whichever you install.

For the **Run from IntelliJ** path below, either A or B works. The keytool snippet auto-locates whichever you have.

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

| Path | IntelliJ | JDK 1.8 (Temurin 8) | Tomcat 9 | Git |
|---|---|---|---|---|
| **Run from IntelliJ** (embedded Tomcat) | required | A or B from Prereq #2 | not needed | optional |
| **Standalone Tomcat deploy** | optional | required (system-wide, Option B) | required | optional |

> The same Java 8 JDK is used for **both** building and running. There is no separate build-vs-runtime distinction here — production runs Java 8, developer machines build with Java 8.

---

## Run from IntelliJ (zero external deps beyond the prereqs above)

Use this path for day-to-day development on any client PC that has IntelliJ IDEA installed. **Nothing else has to be installed first** — JDK is downloaded by IntelliJ, the keystore is generated by JDK's bundled `keytool`, and Tomcat 9 is the same one Spring Boot ships embedded.

End state: app running at `http://localhost:8080/api/health`, started inside IntelliJ's Run window, with hot-reload of code changes.

### 1. Get the project

IntelliJ Welcome screen → **Get from VCS** → URL: `https://github.com/mahfoudhEbkar/OAuth-2.0-JWT-Bearer-Client-.git` → pick a directory → **Clone**.

### 2. Set the Project SDK and Language level to Java 1.8

`File` → `Project Structure` (`Ctrl+Alt+Shift+S`) → `Project`:

- **SDK** → if empty or not Java 8: click `Add SDK` → **Download JDK** → Vendor: **Eclipse Temurin**, Version: **8** → Download.
- **Language level:** **8 - Lambdas, type annotations etc.** This matches `<java.version>1.8</java.version>` in `pom.xml`. Maven reimport auto-syncs this — if it shows 11 or 17 instead, set it manually so the editor flags Java 9+ syntax (like `var` or `List.of(...)`) before you even compile.
- **OK**.

Maven reimports automatically. Wait for the bottom-right progress bar to clear.

> Do **not** point the Project SDK at IntelliJ's bundled JBR (which is JDK 17). The build will succeed, but you'll lose the compile-time guard against Java 9+ APIs sneaking in.

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

- **IntelliJ** + **JDK 1.8 (Temurin 8)** — see Prerequisites #1 and #2 above. Same JDK is used to build and to run the WAR inside Tomcat.
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
if (-not $keytool) { throw "keytool.exe not found - install Temurin 8 or download a JDK via Project Structure first" }
Write-Host "Using: $keytool"

New-Item -ItemType Directory -Force keys | Out-Null
& $keytool -genkeypair -keyalg RSA -keysize 2048 -alias oauth2-client -keystore keys\oauth2-client.p12 -storetype PKCS12 -storepass changeit -keypass changeit -dname "CN=oauth2-jwt-client, O=Odea, C=US" -validity 365
& $keytool -exportcert -alias oauth2-client -keystore keys\oauth2-client.p12 -storetype PKCS12 -storepass changeit -rfc -file keys\oauth2-client.crt
```

Expected output (path will reflect your install):
```
Using: C:\Program Files\Eclipse Adoptium\jdk-8.0.412.8-hotspot\bin\keytool.exe
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
| POST | `/api/crypto/encrypt` | RSA-encrypts the `data` field — accepts a string OR a JSON object/array directly; returns base64 ciphertext |
| POST | `/api/crypto/decrypt` | RSA-decrypts a base64 ciphertext; returns `data` as native JSON when the plaintext was an object/array, as a string otherwise |

### RSA encrypt / decrypt — Postman / Bruno / curl

Endpoint: **`POST http://localhost:8080/api/crypto/encrypt`** (embedded) or **`http://localhost:8080/oauth2-jwt-client/api/crypto/encrypt`** (standalone Tomcat).

Headers:
```
Content-Type: application/json
```

The `data` field accepts any JSON value — string, object, array. No backslash escaping required when sending JSON.

**Request body — JSON object (recommended for structured payloads):**
```json
{
  "data": { "firstname": "mahfoudh", "id": 42 }
}
```

**Request body — plain string (still works, back-compat):**
```json
{
  "data": "hello world"
}
```

**Request body — array:**
```json
{
  "data": [1, 2, 3]
}
```

**Response (HTTP 200) — same shape for all three above:**
```json
{
  "algorithm": "RSA/ECB/OAEPWithSHA-256AndMGF1Padding",
  "encrypted": "F/ik9nsEpCzWvQVocTFCDrlzso12hfGVId13DaWdDvKSCAMygXFFJkEmKinJZmp0ETipsgsdH7lXlUuwANg3Gw...",
  "ciphertextLength": 344
}
```

Server-side: structured `data` is serialized to canonical JSON (`{"firstname":"mahfoudh","id":42}`) before being passed to the RSA cipher. The serialization is deterministic — same input bytes every time — so identical payloads encrypt to ciphertexts of identical length.

**Roundtrip with `POST /api/crypto/decrypt`:**
```json
{
  "encrypted": "<paste the value from /encrypt here>"
}
```

**Returns (when the plaintext was a JSON object) — `data` is a real object, no `\"` anywhere:**
```json
{
  "algorithm": "RSA/ECB/OAEPWithSHA-256AndMGF1Padding",
  "data": { "firstname": "mahfoudh", "id": 42 }
}
```

**Returns (when the plaintext was a plain string):**
```json
{
  "algorithm": "RSA/ECB/OAEPWithSHA-256AndMGF1Padding",
  "data": "hello world"
}
```

Only JSON **objects and arrays** are deserialized back to native JSON in the response. A bare number like `42` is kept as the string `"42"` to avoid silently changing a caller's text payload into a JSON number on the way back.

**curl equivalents:**
```bash
# Encrypt a JSON object directly (no escaping)
curl -X POST http://localhost:8080/api/crypto/encrypt \
  -H "Content-Type: application/json" \
  -d '{"data":{"firstname":"mahfoudh","id":42}}'

# Encrypt a plain string
curl -X POST http://localhost:8080/api/crypto/encrypt \
  -H "Content-Type: application/json" \
  -d '{"data":"hello world"}'

# Decrypt
curl -X POST http://localhost:8080/api/crypto/decrypt \
  -H "Content-Type: application/json" \
  -d '{"encrypted":"<base64>"}'
```

**Validation errors** (e.g. missing `data` field): the server returns HTTP 400 with a structured field list, not a 500:
```json
{
  "error": "validation_failure",
  "fields": [
    { "field": "data", "message": "must not be null" }
  ]
}
```

**Algorithm:** `RSA/ECB/OAEPWithSHA-256AndMGF1Padding` (modern OAEP, IND-CCA2 secure). The "ECB" token in the JCE transformation name is a historical misnomer — it does not mean ECB block-cipher mode (RSA is not a block cipher).

**Size limit:** With a 2048-bit RSA key + OAEP-SHA-256 padding, the maximum plaintext is ~190 bytes. Inputs larger than that return HTTP 400:
```json
{"error":"crypto_failure","message":"Plaintext is too large for direct RSA encryption (300 bytes). Max ~190 bytes for RSA-2048 with OAEP-SHA-256. For larger payloads use a hybrid scheme (AES + RSA-wrapped key)."}
```

For arbitrary-length payloads (entire JSON files, large records), use **hybrid encryption** — generate a random AES-256 key, encrypt the data with AES-GCM, wrap the AES key with RSA, return both. Easy extension to this controller if needed; not implemented today.

## Java 8 end-to-end (already configured)

The project is **Java 8 source, Java 8 bytecode, Java 8 build JDK, Java 8 runtime**. No flag flipping needed before production. In `pom.xml`:

```xml
<properties>
    <java.version>1.8</java.version>
    ...
</properties>
```

What this gets you:

| Aspect | Value | Why |
|---|---|---|
| Bytecode major version | 52 (Java 8) | runs on the production Tomcat 9 host's Java 8 JVM |
| Compile-time API surface | Java 8 only | the build JDK is itself Java 8, so `List.of`, `Optional.isEmpty`, `Stream.toList`, text blocks, etc. simply do not exist and any reference fails to compile |
| Build JDK | Temurin 8 (CI uses Temurin 8) | the same JDK we ship to. No `--release` flag needed, and `maven.compiler.release` is deliberately not set |
| Source syntax | Java 8 only | `var`, records, sealed classes, switch expressions, etc. are blocked by the build JDK |
| Runtime JVM (where WAR runs) | Java 8 or higher | works on production Java 8; also works on 11, 17, 21 if you ever migrate |

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
