# Cryptography

The RSA encrypt/decrypt endpoints share the same keystore as the JWT client-assertion signing. One keypair, two purposes:

| Purpose | Key half used |
|---|---|
| Sign JWT client assertions for the AS | **private** key |
| `POST /api/crypto/encrypt` | **public** key |
| `POST /api/crypto/decrypt` | **private** key |

## Algorithm

`RSA/ECB/OAEPWithSHA-256AndMGF1Padding` — modern OAEP padding with SHA-256. **IND-CCA2 secure** (resistant to chosen-ciphertext attacks).

> The "ECB" token in the JCE transformation name is a historical misnomer — it does **not** mean ECB block-cipher mode. RSA is not a block cipher; it's a single-block trapdoor permutation.

**Do not** use `RSA/ECB/PKCS1Padding` — that's PKCS #1 v1.5, vulnerable to Bleichenbacher attacks. OAEP is what's in the code and what should stay in the code.

## Size limit

RSA-2048 modulus is 256 bytes. OAEP-SHA-256 overhead is 2 × 32 + 2 = 66 bytes. That leaves **~190 bytes** of plaintext.

Sending more than that returns HTTP 400 with a clear message. For larger payloads, use hybrid encryption:

1. Generate a random AES-256 key.
2. Encrypt the payload with AES-GCM using that key.
3. Encrypt the AES key with RSA (fits easily in 190 bytes).
4. Send both `{ ciphertext, wrappedKey }`.

Not implemented in this codebase today. If you need it, extend `RsaCryptoService` and add a `HybridCryptoService` sibling.

## Encrypt path

```
plaintext (String)
   │
   ▼ UTF-8 encode
plainBytes[]
   │
   ▼ Cipher.init(ENCRYPT_MODE, RSAPublicKey from keystore)
   ▼ cipher.doFinal(plainBytes)
cipherBytes[]  (always 256 bytes for RSA-2048)
   │
   ▼ Base64 encode
"F/ik9nsEpCzWvQVocTFCDrlzso12..."   (344 chars for RSA-2048)
```

Deterministic input → **non-deterministic** output. OAEP includes a random seed, so encrypting the same plaintext twice gives two different ciphertexts. Both decrypt to the same plaintext.

## Decrypt path

```
ciphertextBase64 (String)
   │
   ▼ Base64 decode
cipherBytes[]  (must be exactly 256 bytes for RSA-2048)
   │
   ▼ Cipher.init(DECRYPT_MODE, RSAPrivateKey from keystore)
   ▼ cipher.doFinal(cipherBytes)
plainBytes[]
   │
   ▼ UTF-8 decode
plaintext (String)
```

## Response shape rules

The `CryptoController` layer adds JSON-shape handling on top of the raw byte pipeline.

### Encrypt

```java
JsonNode data = request.getData();
String plaintext = data.isTextual()
    ? data.textValue()   // {"data":"hello"} → encrypt "hello"
    : data.toString();   // {"data":{"a":1}} → encrypt '{"a":1}'
```

Canonical JSON (`JsonNode.toString()`) is used because it's deterministic — no whitespace, keys in insertion order — so identical payloads encrypt to same-length ciphertext.

### Decrypt

```java
String plaintext = cryptoService.decrypt(request.getEncrypted());
try {
    JsonNode parsed = objectMapper.readTree(plaintext);
    if (parsed.isObject() || parsed.isArray()) {
        return parsed;                        // native JSON, no \" in response
    }
} catch (JsonProcessingException ignored) {}
return TextNode.valueOf(plaintext);           // stays a string
```

Only **objects and arrays** are deserialized back to native JSON. A bare number like `42` in the plaintext is kept as the string `"42"` in the response — otherwise encrypting text `"42"` would silently come back as the JSON number `42`, changing the caller's semantics.

## Keystore lifecycle

| Event | What to do |
|---|---|
| Fresh dev PC | `scripts/generate-dev-keys.bat` (or `.sh`) → `keys/oauth2-client.p12` |
| Fresh production server | `scripts/windows-server-setup.ps1` generates one in `<TomcatHome>\conf\oauth2\` |
| Cert about to expire | Generate a new keystore, deploy to the server, send new `.crt` to AS admin, restart app. Old cert stays registered at the AS for the overlap window. |
| Suspected compromise | Rotate immediately: new keystore, revoke old cert at the AS, restart app. |

Never share the `.p12`. **Always** hand over only the `.crt` (public half).

## Where the algorithm string comes from

Both request and response include the algorithm identifier verbatim so callers know what they got:

```json
{ "algorithm": "RSA/ECB/OAEPWithSHA-256AndMGF1Padding", ... }
```

Comes from `RsaCryptoService.getAlgorithm()`. If the algorithm ever changes (e.g. moved to RSA-3072 or a hybrid scheme), this string changes with it — callers can detect a switch by comparing.

## Testing the roundtrip

The `RsaCryptoServiceTest` in `src/test/java/.../crypto/` runs encrypt→decrypt with a freshly generated key and asserts byte-for-byte roundtrip. The `CryptoControllerTest` (added in commit `0cd6cbf`) covers the JSON-shape rules with a mocked service. See [Architecture](Architecture) for the test file paths.
