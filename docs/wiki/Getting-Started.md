# Getting Started

From nothing to a running app on `http://localhost:8080` in ~5 minutes.

## 1. Prerequisites

| Tool | Why | Install (Windows, winget) |
|---|---|---|
| Git | clone the repo | `winget install Git.Git --accept-source-agreements --accept-package-agreements` |
| JDK 1.8 (Temurin) | build + run | `winget install EclipseAdoptium.Temurin.8.JDK --accept-source-agreements --accept-package-agreements` |
| IntelliJ IDEA (optional) | dev loop | `winget install JetBrains.IntelliJIDEA.Community --accept-source-agreements --accept-package-agreements` |
| Tomcat 9 (optional) | external-container deploy | download from <https://tomcat.apache.org/download-90.cgi> (9.0.x line) |

**Do not** use JDK 11/17/21 for building — the project targets Java 8 and using a newer JDK bypasses the compile-time guard against Java 9+ API references. See [Architecture](Architecture#design-principles-at-play).

## 2. Clone

```powershell
git clone https://github.com/mahfoudhEbkar/OAuth-2.0-JWT-Bearer-Client-.git
cd OAuth-2.0-JWT-Bearer-Client-
```

## 3. Generate a local dev keystore

Never commit a keystore. The repo has a script that produces one locally using the JDK's built-in `keytool`:

**Windows PowerShell / Terminal:**
```powershell
.\scripts\generate-dev-keys.bat
```

**Linux / macOS / Git Bash:**
```bash
bash scripts/generate-dev-keys.sh
```

Result:
```
keys\oauth2-client.p12   (the keystore the app loads)
keys\oauth2-client.crt   (public cert — safe to share, send to AS admin later)
```

Default keystore password: `changeit`. Fine for local dev; **must** be changed for any real environment.

## 4. Set the env vars for local dev

For a smoke test that doesn't hit a real AS, this is enough:

**PowerShell (current session):**
```powershell
$env:OAUTH2_CLIENT_ID           = "local-dev-client"
$env:OAUTH2_TOKEN_ENDPOINT      = "http://localhost:9999/token"     # dummy for smoke test
$env:OAUTH2_KEYSTORE_PATH       = "$PWD\keys\oauth2-client.p12"
$env:OAUTH2_KEYSTORE_PASSWORD   = "changeit"
$env:OAUTH2_KEY_ALIAS           = "oauth2-client"
$env:OAUTH2_KEY_PASSWORD        = "changeit"
$env:OAUTH2_API_BASE_URL        = "http://localhost:9999"
```

For a real integration, use values your AS admin gave you. Full table on [Configuration](Configuration).

## 5. Build

```powershell
mvn clean package
```

You should see `BUILD SUCCESS` and `Tests run: 23, Failures: 0, Errors: 0, Skipped: 0`. Output: `target\oauth2-jwt-client.war` (~22 MB).

Verify it's Java 8 bytecode:
```powershell
# Last byte of the class-file magic should be 34 (= major version 52 = Java 8)
& "$env:JAVA_HOME\bin\javap.exe" -v target\classes\com\odea\oauth2client\Application.class | Select-String "major version"
# Expect: major version: 52
```

## 6. Run

Pick one:

**Standalone (embedded Tomcat):**
```powershell
java -jar target\oauth2-jwt-client.war
# Reachable at http://localhost:8080/api/health
```

**Hot dev loop (`mvn spring-boot:run` with dev profile):**
```powershell
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

**IntelliJ Run Configuration:**
- Open `pom.xml` → let Maven import.
- Right-click `src/main/java/com/odea/oauth2client/Application.java` → **Run 'Application'**.
- The Run tab shows `Started Application in X.XXX seconds`.

## 7. Smoke test

```powershell
curl http://localhost:8080/api/health
# Expect: {"status":"UP","clientId":"loca****"}

curl.exe -X POST http://localhost:8080/api/crypto/encrypt `
  -H "Content-Type: application/json" `
  -d '{"data":{"firstname":"mahfoudh"}}'
# Expect: {"algorithm":"RSA/ECB/OAEPWithSHA-256AndMGF1Padding","encrypted":"...","ciphertextLength":344}
```

If both return 200 — you're set. Full request examples on [API Reference](API-Reference).

## What to read next

- [API Reference](API-Reference) — Bruno / Postman / curl bodies for every endpoint.
- [Cryptography](Cryptography) — how encrypt/decrypt actually work under the hood.
- [Architecture](Architecture) — the six-package layout and request flows.
