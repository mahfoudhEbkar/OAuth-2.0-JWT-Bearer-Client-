package com.odea.oauth2client.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import com.odea.oauth2client.crypto.CryptoException;
import com.odea.oauth2client.crypto.DecryptRequest;
import com.odea.oauth2client.crypto.DecryptResponse;
import com.odea.oauth2client.crypto.EncryptRequest;
import com.odea.oauth2client.crypto.EncryptResponse;
import com.odea.oauth2client.crypto.RsaCryptoService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * REST endpoints for RSA encryption / decryption using the keystore's RSA keypair.
 *
 * <p>POST {@code /api/crypto/encrypt} — body: <code>{"data": &lt;any JSON&gt;}</code>.
 * The {@code data} field accepts a plain string, a JSON object, an array, or any
 * other JSON value. Structured values are serialized to canonical JSON before
 * encryption, so callers never need to backslash-escape quotes inside JSON they
 * want to encrypt.
 *
 * <p>POST {@code /api/crypto/decrypt} — body: <code>{"encrypted":"&lt;base64&gt;"}</code>.
 * Returns the original payload as native JSON: an object goes in, an object
 * comes out (no <code>\"</code> escaping); a string goes in, a string comes
 * out. The round-trip preserves shape exactly.
 */
@RestController
@RequestMapping(value = "/api/crypto", produces = MediaType.APPLICATION_JSON_VALUE)
public class CryptoController {

    private final RsaCryptoService cryptoService;
    private final ObjectMapper objectMapper;

    public CryptoController(RsaCryptoService cryptoService, ObjectMapper objectMapper) {
        this.cryptoService = cryptoService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(value = "/encrypt", consumes = MediaType.APPLICATION_JSON_VALUE)
    public EncryptResponse encrypt(@Valid @RequestBody EncryptRequest request) {
        JsonNode data = request.getData();
        if (data == null || data.isNull()) {
            throw new CryptoException("'data' must be present and non-null");
        }
        // Textual JSON node -> encrypt the raw string value (no surrounding quotes).
        // Any other shape (object, array, number, boolean) -> canonical JSON form.
        // JsonNode.toString() produces compact JSON with no whitespace, which is
        // what we want both for size and for deterministic roundtrips.
        String plaintext = data.isTextual() ? data.textValue() : data.toString();
        String ciphertext = cryptoService.encrypt(plaintext);
        return new EncryptResponse(cryptoService.getAlgorithm(), ciphertext);
    }

    @PostMapping(value = "/decrypt", consumes = MediaType.APPLICATION_JSON_VALUE)
    public DecryptResponse decrypt(@Valid @RequestBody DecryptRequest request) {
        String plaintext = cryptoService.decrypt(request.getEncrypted());
        JsonNode dataNode = parseStructuredOrString(plaintext);
        return new DecryptResponse(cryptoService.getAlgorithm(), dataNode);
    }

    /**
     * Try to parse {@code plaintext} as JSON. If it parses to an object or
     * array, return that node so callers get clean JSON in the response. For
     * anything else (a bare number, a bare string, malformed JSON), wrap the
     * original string in a {@link TextNode} so the response field is just a
     * JSON string. This avoids the surprise of {@code "data": 42} when the
     * caller encrypted the literal text "42".
     */
    private JsonNode parseStructuredOrString(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            JsonNode parsed = objectMapper.readTree(plaintext);
            if (parsed != null && (parsed.isObject() || parsed.isArray())) {
                return parsed;
            }
        } catch (JsonProcessingException ignored) {
            // not JSON — fall through to string
        }
        return TextNode.valueOf(plaintext);
    }
}
