package com.odea.oauth2client.controller;

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
 * <p>POST {@code /api/crypto/encrypt} — body: <code>{"data":"..."}</code>,
 * returns base64-encoded ciphertext. Call from Postman, curl, any HTTP client.
 *
 * <p>POST {@code /api/crypto/decrypt} — body: <code>{"encrypted":"&lt;base64&gt;"}</code>,
 * returns the original plaintext. Roundtrip-verifies a payload encrypted by
 * {@code /encrypt}.
 */
@RestController
@RequestMapping(value = "/api/crypto", produces = MediaType.APPLICATION_JSON_VALUE)
public class CryptoController {

    private final RsaCryptoService cryptoService;

    public CryptoController(RsaCryptoService cryptoService) {
        this.cryptoService = cryptoService;
    }

    @PostMapping(value = "/encrypt", consumes = MediaType.APPLICATION_JSON_VALUE)
    public EncryptResponse encrypt(@Valid @RequestBody EncryptRequest request) {
        String ciphertext = cryptoService.encrypt(request.getData());
        return new EncryptResponse(cryptoService.getAlgorithm(), ciphertext);
    }

    @PostMapping(value = "/decrypt", consumes = MediaType.APPLICATION_JSON_VALUE)
    public DecryptResponse decrypt(@Valid @RequestBody DecryptRequest request) {
        String plaintext = cryptoService.decrypt(request.getEncrypted());
        return new DecryptResponse(cryptoService.getAlgorithm(), plaintext);
    }
}
