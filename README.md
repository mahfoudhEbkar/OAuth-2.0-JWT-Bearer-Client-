# oauth2-jwt-client

Spring Boot 2.7.18 WAR that authenticates to an OAuth 2.0 Authorization Server using the **private_key_jwt** method (RFC 7523, `urn:ietf:params:oauth:client-assertion-type:jwt-bearer`), exchanges the assertion for an access token via the `client_credentials` grant, caches the token, and calls a protected third-party API with `Authorization: Bearer {token}`.

- **Owner:** Ebkar, Mahfoudh
- **Java:** builds on 17 today, source-compatible with 1.8 for production
- **Container target:** Apache Tomcat 9 (external)
- **Build tool:** Maven

For the full set of architectural rules, security constraints, and the Java 8 compatibility checklist, see [CLAUDE.md](CLAUDE.md).

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

## Switching to Java 1.8 for production

1. Edit `pom.xml`: change `<java.version>17</java.version>` to `<java.version>1.8</java.version>`.
2. `mvn clean compile` — any Java 9+ syntax that slipped in fails here.
3. `mvn clean verify` — all tests must pass.
4. Confirm the WAR runs on a Tomcat 9 instance backed by JDK 8.
5. Commit as: `chore: target Java 1.8 for production deployment`.

The Java 8 compatibility checklist lives in [CLAUDE.md](CLAUDE.md).

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
