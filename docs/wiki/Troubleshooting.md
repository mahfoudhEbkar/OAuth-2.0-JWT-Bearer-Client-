# Troubleshooting

## Error envelopes — what each shape means

`GlobalExceptionHandler` maps every exception to one of these:

| HTTP | `error` value | When |
|---|---|---|
| 400 | `validation_failure` | `@NotNull` / `@Valid` failure. Body includes `fields[]` with the offending field name(s). |
| 400 | `malformed_request_body` | Body isn't JSON, or doesn't match the expected schema. |
| 400 | `crypto_failure` | Base64 decode failed, plaintext too large, wrong keypair, missing private key. |
| 500 | `client_assertion_failure` | Building/signing the JWT failed. Usually a keystore load problem. |
| 502 | `token_acquisition_failure` | AS rejected the token request. Body includes `upstream_error` (e.g. `invalid_client`). |
| 502 | `upstream_api_failure` | Third-party API returned an error we surface. Body includes upstream `status`. |
| 500 | `internal_error` | Anything unexpected. Check the server log for the stack trace. |

The `message` field is always safe to show a user — no secrets, no stack traces, no JWTs.

## Symptoms → causes

### `keystore was tampered with, or password was incorrect`

- **Cause:** `OAUTH2_KEYSTORE_PASSWORD` doesn't match the actual keystore password.
- **Verify** on the server:
  ```powershell
  & "$env:JAVA_HOME\bin\keytool.exe" -list -keystore "$env:OAUTH2_KEYSTORE_PATH" -storepass "$env:OAUTH2_KEYSTORE_PASSWORD"
  ```
  If this fails with the same message, your env var is wrong.
- **Fix:** correct the env var in `setenv.bat` (Tomcat) or the systemd unit / env file. Restart.

### `KeyStore type 'pkcs12' not found`

- **Cause:** the file at `OAUTH2_KEYSTORE_PATH` is JKS, not PKCS12.
- **Fix:** regenerate with `scripts/generate-dev-keys.bat` or `.sh`. JKS is not supported by this codebase.

### `invalid_client` from the Authorization Server

- **Cause:** the public cert (`.crt`) has never been registered at the AS.
- **Fix:** send `keys/oauth2-client.crt` (or `<TomcatHome>\conf\oauth2\oauth2-client.crt` on a server) to the AS admin team. They register it against your `OAUTH2_CLIENT_ID`.

### `invalid_client_assertion` from the AS

- **Cause:** JWT claims don't match what the AS expects. Most commonly the `aud` claim.
- **Fix:** some AS implementations expect `aud=<AS issuer URL>`, not `<AS token endpoint URL>`. Adjust `ClientAssertionService.audience()` **with AS docs in hand** — do not guess.

### `connect timed out` on the token endpoint

- **Cause:** firewall, proxy, or wrong URL.
- **Verify** from the same host:
  ```bash
  curl -v https://as.example.com/oauth2/token
  ```
- **Fix:** either open the outbound rule, set `HTTPS_PROXY`, or correct `OAUTH2_TOKEN_ENDPOINT`.

### 404 on `http://server:8080/oauth2-jwt-client/api/health`

Two possible causes:

1. **WAR didn't deploy.** Check `$CATALINA_HOME/logs/catalina.<date>.log`. Look for a context-start stack trace — usually an env var was missing when the app tried to start.
2. **You're using standalone mode.** Standalone (`java -jar`) has **no context prefix** — the URL is `http://server:8080/api/health` (without `/oauth2-jwt-client`).

### 404 with an HTML "Not Found" page (not JSON)

You're hitting a port where the Spring Boot app isn't listening. It might be:
- Wrong port (`:80` instead of `:8080`)
- App not running at all
- Some other web server (IIS default page)

Test the port directly:
```powershell
Test-NetConnection <server-ip> -Port 8080
# TcpTestSucceeded should be True
```

### `ClassNotFoundException: javax.servlet.*` in `catalina.out`

- **Cause:** deployed to Tomcat 10 instead of Tomcat 9. Tomcat 10 moved to `jakarta.*` and doesn't work with Spring Boot 2.7.
- **Fix:** install Tomcat 9.x, redeploy.

### `UnsupportedClassVersionError: ... class file version 55.0`

- **Cause:** server's JDK is older than the WAR was built with. Class file 55 = Java 11.
- **This project targets Java 8** (class file 52), so this should never happen. If it does, either the WAR wasn't built by our CI, or someone accidentally re-added `<maven.compiler.release>`. See [Architecture → Design principles](Architecture#design-principles-at-play).

### `Address already in use: bind` on startup

- **Cause:** something else on port 8080.
- **Diagnose:**
  ```powershell
  Get-NetTCPConnection -LocalPort 8080 | Select-Object OwningProcess
  Get-Process -Id <PID>
  ```
- **Fix:** kill the offending process, or change `SERVER_PORT` for the app.

### Bruno / Postman: `Not Found` on decrypt but encrypt works

Nearly always a URL typo — missing `:8080`, or wrong context prefix. See [API Reference](API-Reference) for the exact URLs per deploy mode.

### Health endpoint returns 500

- **Cause:** app started but a `@PostConstruct` failed later, or an env var was missing but Spring's lazy binding surfaced the failure only on the first request.
- **Diagnose:** check the app log for the earliest ERROR line. It will name the missing property or the keystore path that failed to open.

### Ciphertext decrypts to garbage

- **Cause:** the ciphertext was encrypted with a **different** public key. Common when someone encrypts against the dev keystore and tries to decrypt on prod (or vice versa).
- **Fix:** re-encrypt against the target environment's keystore. Keystores are per-environment on purpose — see [Deployment → Why external](Deployment#why-the-keystore-lives-outside-the-war).

### `Plaintext is too large for direct RSA encryption`

- **Cause:** payload exceeds ~190 bytes.
- **Fix:** either shrink the payload or implement hybrid encryption (see [Cryptography → Size limit](Cryptography#size-limit)).

## Where to look

| Deployment | Log location |
|---|---|
| `java -jar` standalone | wherever you ran the command — stdout/stderr |
| systemd service | `journalctl -u oauth2-jwt-client -f` |
| Windows external Tomcat | `<TomcatHome>\logs\catalina.<date>.log` |
| IntelliJ Run window | Run tool window in the IDE |
| GitHub Actions CI | Actions tab on the repo |

## When all else fails

Grab the first ~50 lines of the log around the error, redact any `OAUTH2_*` values, and open an issue at:
<https://github.com/mahfoudhEbkar/OAuth-2.0-JWT-Bearer-Client-/issues>

Never paste JWTs, tokens, keystore passwords, or private keys — the [Security checklist](https://github.com/mahfoudhEbkar/OAuth-2.0-JWT-Bearer-Client-/blob/main/CLAUDE.md#security-checklist) in CLAUDE.md applies to bug reports too.
