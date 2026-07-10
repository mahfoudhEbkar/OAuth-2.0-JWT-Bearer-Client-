# Architecture

The whole codebase is roughly 25 Java files under `src/main/java/com/odea/oauth2client/`, organized into six thin packages. Each layer has a single responsibility and depends only on the layer below it.

## The five layers (plus one utility)

```
     controller/    ── REST surface (Bruno / Postman / anyone with HTTP)
         │
         ▼
        api/        ── outgoing HTTP with bearer token, retry-on-401
         │
         ▼
       token/       ── acquire tokens from the AS, cache in memory
         │
         ▼
        jwt/        ── build + sign the client-assertion JWT (RS256)
         │
         ▼
      config/       ── boot-time wiring: keystore, env vars, HTTP timeouts

     crypto/        ── standalone RSA helper, shares the keystore
```

## Package walkthrough

### `config/` — startup wiring, runs once

| Class | Responsibility |
|---|---|
| `OAuth2ClientProperties` | `@ConfigurationProperties("oauth2")` — binds every `OAUTH2_*` env var into a typed POJO. **Only place** env vars are read. |
| `KeyStoreLoader` | `@PostConstruct` loads the PKCS12 file once. Exposes the RSA keypair as a `com.nimbusds.jose.jwk.RSAKey`. |
| `RestTemplateConfig` | `@Bean RestTemplate` with connect + read timeouts from properties. All outgoing HTTP uses this one bean. |
| `GlobalExceptionHandler` | `@ControllerAdvice` — turns every exception into a proper JSON error envelope. See [Troubleshooting → Error envelopes](Troubleshooting#error-envelopes). |

### `jwt/` — build a signed assertion for the AS

| Class | Responsibility |
|---|---|
| `ClientAssertionService` | On demand, builds a **fresh** JWT: header `{alg:RS256, typ:JWT, kid:<optional>}`, claims `iss=sub=clientId`, `aud=tokenEndpoint`, `iat=now`, `exp=now+300s`, `jti=UUID`. Signs with the RSA private key. Returns compact serialization. |
| `ClientAssertionException` | Runtime — mapped to HTTP 500 with `error=client_assertion_failure`. |

A new JWT is built for **every** token request (short-lived, unique `jti`).

### `token/` — get + cache access tokens

| Class | Responsibility |
|---|---|
| `TokenService` | `getAccessToken()` returns a cached token if it's still valid (>60 s from expiry), otherwise requests a new one. `invalidateAndRefresh()` forces a new request. Thread-safe. |
| `TokenResponse` | POJO for the AS's response body (`access_token`, `token_type`, `expires_in`, `scope`). |
| `TokenAcquisitionException` | Wraps AS failures — HTTP 502, `error=token_acquisition_failure`. |

### `api/` — call the third-party API

| Class | Responsibility |
|---|---|
| `ThirdPartyApiClient` | GET/POST helpers that add `Authorization: Bearer <token>` from `TokenService`. On 401 → `invalidateAndRefresh()` + retry once. |
| `ThirdPartyApiException` | Wraps upstream failures — HTTP 502, includes upstream status code. |

### `crypto/` — RSA encrypt/decrypt using the same keystore

| Class | Responsibility |
|---|---|
| `RsaCryptoService` | `encrypt(String)` / `decrypt(String)` using `RSA/ECB/OAEPWithSHA-256AndMGF1Padding`. Uses the keypair from `KeyStoreLoader`. Enforces the ~190-byte plaintext ceiling. |
| `EncryptRequest` | `{ "data": <any JSON> }`. `data` is a `JsonNode` — accepts objects, arrays, strings. |
| `DecryptRequest` | `{ "encrypted": "<base64>" }`. |
| `EncryptResponse` | `{ algorithm, encrypted, ciphertextLength }`. |
| `DecryptResponse` | `{ algorithm, data }` — `data` is a `JsonNode`, returned as native JSON when the plaintext was structured. |
| `CryptoException` | HTTP 400, `error=crypto_failure`. |

### `controller/` — HTTP endpoints

| Class | Route | Purpose |
|---|---|---|
| `HealthController` | `GET /api/health` | Uptime + masked `clientId`. Proves env vars loaded. |
| `ApiProxyController` | `GET /api/proxy/{*path}` | Thin pass-through — exercises `TokenService` + `ThirdPartyApiClient` end-to-end. |
| `CryptoController` | `POST /api/crypto/encrypt` + `/decrypt` | JSON-shape handling on top of `RsaCryptoService` — see [Cryptography](Cryptography). |

## Request flow — encrypt a JSON object

```
Bruno
  │  POST /api/crypto/encrypt   { "data": { "firstname": "mahfoudh" } }
  ▼
CryptoController.encrypt()
  │  data.isTextual()? no → data.toString() = '{"firstname":"mahfoudh"}'
  ▼
RsaCryptoService.encrypt("{\"firstname\":\"mahfoudh\"}")
  │  UTF-8 encode → 23 bytes
  │  Cipher.init(ENCRYPT_MODE, publicKey)
  │  doFinal → 256 bytes ciphertext
  │  Base64 → 344 chars
  ▼
EncryptResponse{ algorithm, encrypted, ciphertextLength: 344 }
  ▼
HTTP 200 back to Bruno
```

## Request flow — call the protected upstream API

```
Bruno
  │  GET /api/proxy/customers/42
  ▼
ApiProxyController.proxy()
  ▼
ThirdPartyApiClient.get()
  │  token = TokenService.getAccessToken()
  │     cache miss? →
  │       jwt = ClientAssertionService.buildAssertion()
  │       POST tokenEndpoint (grant_type=client_credentials & client_assertion=<jwt>)
  │       parse TokenResponse, cache it
  │  Set "Authorization: Bearer <token>"
  │  RestTemplate.getForEntity(apiBaseUrl + "/customers/42")
  │     on 401 → TokenService.invalidateAndRefresh() → retry ONCE
  ▼
whatever the upstream returned, straight back to Bruno
```

## Packaging choices (why the WAR is dual-mode)

The Spring Boot Maven plugin's `repackage` goal produces a WAR that runs two ways from the same file:

| Mode | Command | Path |
|---|---|---|
| Embedded Tomcat (standalone) | `java -jar oauth2-jwt-client.war` | `WEB-INF/lib-provided/tomcat-embed-*.jar` on classpath via Spring Boot launcher |
| External Tomcat 9 | drop into `webapps/oauth2-jwt-client.war` | External container ignores `WEB-INF/lib-provided/` and uses its own Tomcat libs |

`spring-boot-starter-tomcat` is at `<scope>provided</scope>` so the same artifact works in both modes. **Do not** change `<packaging>` to `jar` — you'd lose the external-Tomcat deploy path (see [Deployment](Deployment)).

## Design principles at play

- **Boundaries at env vars.** Every secret comes from an env var. `application.yml` never has defaults for sensitive values.
- **Fail closed on secrets.** No env var → the app refuses to start; it never silently uses a default keystore.
- **One artifact, two deployment modes.** Same WAR runs standalone or in an external Tomcat.
- **JVM-level API surface as the compiler enforcer.** Building on Temurin 8 makes Java 9+ APIs (`List.of`, `Optional.isEmpty`, etc.) uncallable — no runtime surprises.
- **Bounded retries.** 401 is retried exactly once. Never a loop.
- **Redis / DB avoided on purpose.** Token cache is in-memory. Adding a shared cache would be a scaling-out decision, not a correctness decision.
