# Configuration

All configuration flows through `@ConfigurationProperties("oauth2")` into `OAuth2ClientProperties`. That's the **only** place env vars are read.

## Env vars — required

| Env var | YAML path | Purpose |
|---|---|---|
| `OAUTH2_CLIENT_ID` | `oauth2.client-id` | Client identifier registered with the Authorization Server |
| `OAUTH2_TOKEN_ENDPOINT` | `oauth2.token-endpoint` | AS token endpoint URL |
| `OAUTH2_KEYSTORE_PATH` | `oauth2.keystore.path` | Absolute path to the PKCS12 `.p12` file |
| `OAUTH2_KEYSTORE_PASSWORD` | `oauth2.keystore.password` | Keystore password |
| `OAUTH2_KEY_ALIAS` | `oauth2.keystore.alias` | Key alias inside the `.p12` (`oauth2-client` in dev) |
| `OAUTH2_KEY_PASSWORD` | `oauth2.keystore.key-password` | Private key password (usually same as keystore password) |
| `OAUTH2_API_BASE_URL` | `oauth2.api.base-url` | Root URL of the protected third-party API |

If any is missing, the app **refuses to start**. There are no defaults for the sensitive ones in `application.yml`.

## Env vars — optional

| Env var | YAML path | Default | Purpose |
|---|---|---|---|
| `OAUTH2_SCOPE` | `oauth2.scope` | (empty) | Space-separated OAuth scopes appended to the token request |
| `OAUTH2_KEY_ID` | `oauth2.key-id` | (empty) | Sets the JWT `kid` header. Set this if the AS requires it to look up your public key. |
| — | `oauth2.assertion-ttl-seconds` | `300` | JWT lifetime. Do not exceed 600 (RFC 7523 §3 guidance). |
| — | `oauth2.send-client-id-in-form` | `false` | Whether to also send `client_id=...` in the token request form. Some AS implementations require it. |
| — | `oauth2.api.connect-timeout-ms` | `5000` | Outbound HTTP connect timeout for the upstream API |
| — | `oauth2.api.read-timeout-ms` | `15000` | Outbound HTTP read timeout for the upstream API |

## Spring properties (JVM args)

| Property | Purpose |
|---|---|
| `--server.port=9090` | Change HTTP listen port (default 8080) |
| `--spring.profiles.active=prod` | Activate a profile (loads `application-prod.yml` if present) |
| `-Xms256m -Xmx512m` | JVM heap tuning. For most deployments 512 MB is plenty. |

Set via `CATALINA_OPTS` in Tomcat's `setenv.bat` / `setenv.sh`.

## `application.yml` overrides

Non-sensitive tunables can live in `src/main/resources/application.yml`. Sensitive values must **not**.

Currently in `application.yml`:

```yaml
oauth2:
  assertion-ttl-seconds: 300
  send-client-id-in-form: false
  api:
    connect-timeout-ms: 5000
    read-timeout-ms: 15000
```

## Local dev overrides

For values you don't want to type every time, use `application-local.yml` in `src/main/resources/`. It is **git-ignored** — safe for personal shortcuts.

Activate with `--spring.profiles.active=local` or `mvn spring-boot:run -Dspring-boot.run.profiles=local`.

## Precedence (highest to lowest)

Standard Spring Boot order:

1. Command-line args (`--server.port=9090`)
2. `SPRING_APPLICATION_JSON` env
3. **OS env vars** (this is where all `OAUTH2_*` values normally live)
4. `application-{profile}.yml` (`application-prod.yml`, etc.)
5. `application.yml`
6. `@ConfigurationProperties` defaults

Higher wins. Env vars override YAML — that's why prod `OAUTH2_KEYSTORE_PASSWORD` in `setenv.bat` overrides any dev default.

## Verifying the effective config

```powershell
# Show what Spring is going to bind
curl http://localhost:8080/api/health
```

The masked `clientId` in the health response confirms the app read `OAUTH2_CLIENT_ID` correctly. If it shows the wrong prefix, the env var isn't reaching the JVM — check `setenv.bat` was placed in the right `bin/` folder and Tomcat was restarted.

For a full picture of what Maven / Spring resolved:
```bash
mvn help:effective-pom
```
