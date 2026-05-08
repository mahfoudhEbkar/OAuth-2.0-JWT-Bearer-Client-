# CLAUDE.md — oauth2-jwt-client

Future Claude Code sessions: read this first. Every decision in this project is captured here. Do not relitigate them. If you must change one of these constraints, update this file in the same commit.

## Project identity

- Name: `oauth2-jwt-client`
- groupId: `com.odea`
- artifactId: `oauth2-jwt-client`
- version: `1.0.0`
- Packaging: `war`
- Owner: Ebkar, Mahfoudh

## What it does

OAuth 2.0 client that:

1. Builds a signed JWT client assertion (RFC 7523, `urn:ietf:params:oauth:client-assertion-type:jwt-bearer`).
2. Exchanges it at the Authorization Server's token endpoint via the `client_credentials` grant.
3. Caches the access token in memory; refreshes when within 60 seconds of expiry.
4. Calls a protected third-party API with `Authorization: Bearer {token}`.
5. On 401 from the upstream API, force-refreshes the token and retries once.

## Hard constraints — do not change without permission

- **Java**: source/target compiles on JDK 17 today (`<java.version>17</java.version>`). The codebase MUST stay source-compatible with Java 1.8 for production deployment. Switch the property to `1.8` before shipping; it must still build.
- **Spring Boot**: 2.7.18 (last 2.7.x release).
- **Packaging**: `war`. `spring-boot-starter-tomcat` MUST be `provided` scope. `ServletInitializer` extends `SpringBootServletInitializer`.
- **JWT library**: `com.nimbusds:nimbus-jose-jwt:9.37.3` (last 9.x line that supports Java 8). DO NOT upgrade to 10.x.
- **Keystore**: PKCS12 (`.p12`) only. Loaded at runtime from a file path. Never hard-code keys. Never commit keystores. The `keys/` directory and `*.p12 *.jks *.pem *.key *.crt *.csr` are in `.gitignore`.
- **Build tool**: Maven only.
- **Servlet container**: Tomcat 9.x. NOT Tomcat 10 (jakarta namespace breaks Spring Boot 2.7).

## Java 8 compatibility checklist

When adding code, do not use any of these (they will break the production Java 8 target):

- `var` (local variable type inference)
- `record` types
- Sealed classes
- Pattern matching (`instanceof Foo f`, switch patterns)
- Switch expressions (`->` arms, `yield`)
- Text blocks (`"""..."""`)
- `Stream.toList()`
- `List.of(...)`, `Map.of(...)`, `Set.of(...)` (use `Collections.singletonList`, `new HashMap<>()`, etc.)
- `Optional.isEmpty()`
- `String.lines()`, `String.repeat()`, `String.strip()`
- `Files.readString`, `Files.writeString`
- Any `java.net.http.HttpClient` (added in Java 11)

When you mean a list literal, use `Collections.singletonList(x)` or `Arrays.asList(x, y)`. When you mean a map literal, build it with `new LinkedHashMap<String, Object>()` and `put(...)`.

## Security checklist

- Never log JWTs, access tokens, keystore passwords, client secrets, or private key material.
- Allowed at INFO: token acquisition events with **masked** clientId (first 4 chars + `****`) and `expires_in`; refresh triggers; upstream API status codes.
- Allowed at DEBUG: JWT header (without payload, without signature); token endpoint URL.
- All secrets come from environment variables. No defaults for `OAUTH2_KEYSTORE_PASSWORD` / `OAUTH2_KEY_PASSWORD` in `application.yml`.
- The keystore file lives outside the WAR. In production it goes in `$CATALINA_HOME/conf/oauth2-client.p12` with mode 0600.
- `application-local.yml`, `.env`, `.env.local` are git-ignored.

## Configuration surface

All values come from environment variables (or defaults where shown). Bound to `OAuth2ClientProperties` via `@ConfigurationProperties("oauth2")`.

| Property | Env var | Required |
|---|---|---|
| `oauth2.client-id` | `OAUTH2_CLIENT_ID` | yes |
| `oauth2.token-endpoint` | `OAUTH2_TOKEN_ENDPOINT` | yes |
| `oauth2.scope` | `OAUTH2_SCOPE` | no |
| `oauth2.key-id` | `OAUTH2_KEY_ID` | no (sets JWT `kid` header) |
| `oauth2.assertion-ttl-seconds` | — | no, default 300 |
| `oauth2.send-client-id-in-form` | — | no, default false |
| `oauth2.keystore.path` | `OAUTH2_KEYSTORE_PATH` | yes |
| `oauth2.keystore.password` | `OAUTH2_KEYSTORE_PASSWORD` | yes |
| `oauth2.keystore.alias` | `OAUTH2_KEY_ALIAS` | yes |
| `oauth2.keystore.key-password` | `OAUTH2_KEY_PASSWORD` | yes |
| `oauth2.api.base-url` | `OAUTH2_API_BASE_URL` | yes |
| `oauth2.api.connect-timeout-ms` | — | no, default 5000 |
| `oauth2.api.read-timeout-ms` | — | no, default 15000 |

## JWT claims (do not change without AS admin sign-off)

| Claim | Value |
|---|---|
| `iss` | `${oauth2.client-id}` |
| `sub` | `${oauth2.client-id}` |
| `aud` | `${oauth2.token-endpoint}` |
| `exp` | now + `assertionTtlSeconds` |
| `iat` | now |
| `jti` | random UUID per assertion |

Header: `alg=RS256`, `typ=JWT`. `kid` only when `oauth2.key-id` is set.

> If the AS rejects with `invalid_client_assertion`, check the `aud` claim. Some servers expect the issuer URL of the AS, not the token endpoint URL. Adjust `audience()` in `ClientAssertionService` only with AS docs in hand.

## Token request format

```
POST {token-endpoint}
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials
client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer
client_assertion={signed_jwt}
[scope={scope}]                    # only if oauth2.scope set
[client_id={clientId}]             # only if oauth2.send-client-id-in-form=true
```

## Code layout

```
src/main/java/com/odea/oauth2client/
  Application.java                                   # @SpringBootApplication
  ServletInitializer.java                            # WAR bootstrap
  config/
    OAuth2ClientProperties.java                      # @ConfigurationProperties("oauth2")
    KeyStoreLoader.java                              # @PostConstruct loads PKCS12 -> RSAKey
    RestTemplateConfig.java                          # @Bean RestTemplate with timeouts
    GlobalExceptionHandler.java                      # @ControllerAdvice
  jwt/
    ClientAssertionService.java                      # builds + signs the JWT
    ClientAssertionException.java
  token/
    TokenService.java                                # caches; getAccessToken() / invalidateAndRefresh()
    TokenResponse.java                               # access_token, token_type, expires_in, scope
    TokenAcquisitionException.java
  api/
    ThirdPartyApiClient.java                         # GET/POST with bearer auth, 401 retry
    ThirdPartyApiException.java
  controller/
    HealthController.java                            # GET /api/health
    ApiProxyController.java                          # GET /api/proxy/{*path}

src/main/resources/
  application.yml
  application-dev.yml
  logback-spring.xml

src/test/java/com/odea/oauth2client/
  jwt/ClientAssertionServiceTest.java
  token/TokenServiceTest.java

scripts/
  generate-dev-keys.sh
  generate-dev-keys.bat
```

## Definition of Done

A change is done when ALL of these are true:

1. `mvn clean compile` succeeds on JDK 17.
2. The same code compiles cleanly when `<java.version>` is flipped to `1.8` (no Java 9+ syntax).
3. `mvn test` is green.
4. `mvn clean verify` is green.
5. No secrets (tokens, JWTs, passwords, private keys) appear in any log statement.
6. No keystore file is staged for commit (`git status` clean of `*.p12 *.key *.crt *.pem *.jks`).
7. New configuration is documented in this file's Configuration Surface table and in `README.md`.

## Common build/run commands

```
# Build the WAR
mvn clean package

# Run the unit tests
mvn test

# Run with embedded Tomcat (dev profile)
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Effective POM
mvn help:effective-pom
```

## When in doubt

- Need to add a dependency? Pin a version that supports Java 8 runtime, even though we build on 17.
- Need to log something near tokens/keys? Mask it. Re-read the Security checklist.
- Need to deploy somewhere new? The WAR runs on Tomcat 9 with externalized env vars. Tomcat 10 will not work.
