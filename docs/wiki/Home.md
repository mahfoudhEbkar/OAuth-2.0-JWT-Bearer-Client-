# oauth2-jwt-client — Wiki

Welcome. This is the entry point to the project's documentation.

`oauth2-jwt-client` is a **Spring Boot 2.7 WAR** that:

1. Authenticates to an OAuth 2.0 Authorization Server using **private_key_jwt** (RFC 7523) — a signed JWT instead of a password.
2. Caches the returned access token in memory, refreshes 60 s before expiry.
3. Calls a protected third-party API with `Authorization: Bearer <token>`, retrying once on 401.
4. Exposes a small **RSA encrypt / decrypt** REST utility that uses the same keystore.

Everything is Java 8 end-to-end, ships as a dual-mode WAR (embedded Tomcat via `java -jar` **or** deployed to an external Tomcat 9), and holds zero secrets in source.

## Where to go next

| If you want to… | Read this page |
|---|---|
| Understand the project shape and package layout | [Architecture](Architecture) |
| Clone, build, and run on your dev PC in ~5 minutes | [Getting Started](Getting-Started) |
| Call the REST endpoints from Bruno / Postman / curl | [API Reference](API-Reference) |
| Deploy to a real Windows Server with Tomcat 9 | [Deployment](Deployment) |
| Understand the crypto endpoints (RSA-OAEP-SHA-256) | [Cryptography](Cryptography) |
| See the full env-var / property list | [Configuration](Configuration) |
| Debug a failure symptom | [Troubleshooting](Troubleshooting) |

## Project identity at a glance

| Field | Value |
|---|---|
| groupId | `com.odea` |
| artifactId | `oauth2-jwt-client` |
| version | `1.0.0` |
| Packaging | `war` (dual-mode: `java -jar` + Tomcat 9) |
| Java | **JDK 1.8 end-to-end** (build, bytecode, runtime) |
| Framework | Spring Boot 2.7.18 |
| JWT library | Nimbus JOSE 9.37.3 |
| Container target | Tomcat 9.x (**not** Tomcat 10) |
| Owner | Ebkar, Mahfoudh |
| Repo | <https://github.com/mahfoudhEbkar/OAuth-2.0-JWT-Bearer-Client-> |
| Latest release (WAR) | <https://github.com/mahfoudhEbkar/OAuth-2.0-JWT-Bearer-Client-/releases/tag/v1.0.0> |

## One-sentence rules (details on each linked page)

- **Never** commit keystores, `.env` files, or anything under `keys/` — `.gitignore` enforces this.
- **Never** set defaults for `OAUTH2_KEYSTORE_PASSWORD` or `OAUTH2_KEY_PASSWORD` in `application.yml`.
- **Never** log JWTs, tokens, keystore passwords, or private key material.
- **Never** upgrade Nimbus JOSE to 10.x (drops Java 8) or Spring Boot to 3.x (drops Java 8, moves to jakarta.*).
- **Never** change `<packaging>` to `jar` — it breaks the Tomcat-deploy path (see [Architecture](Architecture#packaging-choices)).

The strict, versioned form of these rules lives in [CLAUDE.md](https://github.com/mahfoudhEbkar/OAuth-2.0-JWT-Bearer-Client-/blob/main/CLAUDE.md).
