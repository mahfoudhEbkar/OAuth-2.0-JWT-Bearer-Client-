# API Reference

Base URL:
- **Local / standalone:** `http://localhost:8080`
- **External Tomcat (Windows Server):** `http://<server-ip>:8080/oauth2-jwt-client`

All request/response bodies are JSON. `Content-Type: application/json` is required on POSTs.

---

## `GET /api/health`

Uptime check. Confirms the app started and read its env vars.

**Response 200:**
```json
{
  "status": "UP",
  "clientId": "abcd****"
}
```

`clientId` is **masked** — first 4 chars + `****`. Full value is never logged or exposed.

**Response 500:** the app failed to load the keystore or bind properties. See [Troubleshooting](Troubleshooting).

---

## `GET /api/proxy/{*path}`

Thin pass-through to the third-party API. Exercises the full **acquire-token → attach-bearer → 401-retry** path end-to-end.

**Example:**
```
GET /api/proxy/customers/42
```
becomes upstream:
```
GET https://<OAUTH2_API_BASE_URL>/customers/42
Authorization: Bearer <cached or freshly-acquired token>
```

**Response:** whatever the upstream API returns, byte-for-byte.

**On upstream 401:** the token is invalidated, refreshed, and the request is retried **once**. If it still returns 401, that 401 is passed back to the caller.

---

## `POST /api/crypto/encrypt`

RSA-encrypt a payload with the keystore's **public** key.

**Body — accepts any JSON value under `data`:**

```json
{ "data": { "firstname": "mahfoudh", "id": 42 } }
```
```json
{ "data": "hello world" }
```
```json
{ "data": [1, 2, 3] }
```

No `\"` escaping needed. Structured values are serialized to canonical JSON (`{"firstname":"mahfoudh","id":42}`) before being handed to the RSA cipher.

**Response 200 (same shape for all three inputs):**
```json
{
  "algorithm": "RSA/ECB/OAEPWithSHA-256AndMGF1Padding",
  "encrypted": "F/ik9nsEpCzWvQVocTFCDrlzso12hfGVId13DaWdDvKSCAMygXFFJkEmKinJZmp0ETipsgsdH7lXlUuwANg3Gw...",
  "ciphertextLength": 344
}
```

`encrypted` is a base64 string of the RSA ciphertext bytes. For RSA-2048 it is always exactly **344 chars**.

**Response 400 — payload too large:**
```json
{
  "error": "crypto_failure",
  "message": "Plaintext is too large for direct RSA encryption (300 bytes). Max ~190 bytes for RSA-2048 with OAEP-SHA-256. For larger payloads use a hybrid scheme (AES + RSA-wrapped key)."
}
```

**Response 400 — missing `data`:**
```json
{
  "error": "validation_failure",
  "fields": [
    { "field": "data", "message": "must not be null" }
  ]
}
```

**Size ceiling:** RSA-2048 + OAEP-SHA-256 allows **~190 bytes** of plaintext. For larger payloads, use hybrid encryption (AES-256 with an RSA-wrapped AES key). Not implemented today.

---

## `POST /api/crypto/decrypt`

RSA-decrypt a base64 ciphertext with the keystore's **private** key.

**Body:**
```json
{ "encrypted": "F/ik9nsEpCzWvQVocTFCDrlzso12hfG..." }
```

**Response 200 — when the plaintext was a JSON object/array, returned natively:**
```json
{
  "algorithm": "RSA/ECB/OAEPWithSHA-256AndMGF1Padding",
  "data": { "firstname": "mahfoudh", "id": 42 }
}
```

**Response 200 — when the plaintext was a plain string:**
```json
{
  "algorithm": "RSA/ECB/OAEPWithSHA-256AndMGF1Padding",
  "data": "hello world"
}
```

**Response 400 — bad base64:**
```json
{
  "error": "crypto_failure",
  "message": "encrypted value is not valid base64"
}
```

**Response 400 — wrong keypair / corrupted ciphertext:**
```json
{
  "error": "crypto_failure",
  "message": "RSA decryption failed: <underlying JCE message>"
}
```

`data` in the response is **always** clean JSON — no `\"` anywhere. See [Cryptography → Response shape rules](Cryptography#response-shape-rules) for the exact logic.

---

## Bruno collection (recommended)

The clean-JSON design is easiest to use with a Bruno collection that stores the ciphertext in an env var between requests.

**`01-encrypt.bru`:**
```
post {
  url: {{baseUrl}}/api/crypto/encrypt
}
headers {
  Content-Type: application/json
}
body:json {
  {
    "data": {
      "firstname": "mahfoudh",
      "id": 42
    }
  }
}
script:post-response {
  bru.setEnvVar("ciphertext", res.body.encrypted);
}
```

**`02-decrypt.bru`:**
```
post {
  url: {{baseUrl}}/api/crypto/decrypt
}
headers {
  Content-Type: application/json
}
body:json {
  {
    "encrypted": "{{ciphertext}}"
  }
}
assert {
  res.status: eq 200
  res.body.data.firstname: eq "mahfoudh"
}
```

## Postman equivalent

The clean-JSON bodies above work as-is. In Postman's **Tests** tab on the encrypt request:

```javascript
pm.environment.set("ciphertext", pm.response.json().encrypted);
```

Then on the decrypt request's body, use `{{ciphertext}}`.

## curl

```bash
# Encrypt a JSON object (no escaping)
curl -X POST http://localhost:8080/api/crypto/encrypt \
  -H "Content-Type: application/json" \
  -d '{"data":{"firstname":"mahfoudh","id":42}}'

# Decrypt
curl -X POST http://localhost:8080/api/crypto/decrypt \
  -H "Content-Type: application/json" \
  -d '{"encrypted":"<base64 from previous response>"}'
```

## Error envelopes across all endpoints

Consistent shape from `GlobalExceptionHandler`. See [Troubleshooting → Error envelopes](Troubleshooting#error-envelopes) for the full list.
