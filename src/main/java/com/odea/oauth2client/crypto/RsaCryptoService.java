package com.odea.oauth2client.crypto;

import com.nimbusds.jose.jwk.RSAKey;
import com.odea.oauth2client.config.KeyStoreLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

/**
 * RSA encryption / decryption using the keystore's RSA keypair.
 *
 * <p>Algorithm: <code>RSA/ECB/OAEPWithSHA-256AndMGF1Padding</code> — modern
 * OAEP padding with SHA-256, IND-CCA2 secure. (The "ECB" token in the JCE
 * transformation name is misleading — it does not mean ECB mode in the block
 * cipher sense; RSA is not a block cipher. It is what the JCE spec calls a
 * direct, non-chained RSA operation.)
 *
 * <p>Size limit: with a 2048-bit RSA key and OAEP-SHA-256 padding the
 * maximum plaintext is 190 bytes. Inputs longer than that get rejected with
 * a clear error. For arbitrary-length payloads you would wrap an AES key
 * with RSA and encrypt the data with AES — out of scope for this method.
 */
@Service
public class RsaCryptoService {

    private static final Logger log = LoggerFactory.getLogger(RsaCryptoService.class);
    private static final String TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";

    private final KeyStoreLoader keyStoreLoader;

    public RsaCryptoService(KeyStoreLoader keyStoreLoader) {
        this.keyStoreLoader = keyStoreLoader;
    }

    /**
     * Encrypts the given UTF-8 string with the keystore's RSA public key.
     *
     * @param plaintext the data to encrypt, max ~190 bytes for RSA-2048 + OAEP-SHA-256
     * @return base64-encoded ciphertext
     */
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            throw new CryptoException("plaintext is required");
        }
        byte[] plainBytes = plaintext.getBytes(StandardCharsets.UTF_8);
        try {
            RSAKey rsaKey = keyStoreLoader.getRsaKey();
            RSAPublicKey publicKey = rsaKey.toRSAPublicKey();

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] cipherBytes = cipher.doFinal(plainBytes);

            log.info("RSA encrypted {} bytes -> {} bytes ciphertext", plainBytes.length, cipherBytes.length);
            return Base64.getEncoder().encodeToString(cipherBytes);
        } catch (javax.crypto.IllegalBlockSizeException e) {
            throw new CryptoException(
                    "Plaintext is too large for direct RSA encryption (" + plainBytes.length
                            + " bytes). Max ~190 bytes for RSA-2048 with OAEP-SHA-256. "
                            + "For larger payloads use a hybrid scheme (AES + RSA-wrapped key).", e);
        } catch (Exception e) {
            throw new CryptoException("RSA encryption failed: " + e.getMessage(), e);
        }
    }

    /**
     * Decrypts a base64-encoded ciphertext with the keystore's RSA private key.
     *
     * @param ciphertextBase64 base64-encoded ciphertext produced by {@link #encrypt(String)}
     * @return the original UTF-8 plaintext
     */
    public String decrypt(String ciphertextBase64) {
        if (ciphertextBase64 == null || ciphertextBase64.isEmpty()) {
            throw new CryptoException("encrypted value is required");
        }
        byte[] cipherBytes;
        try {
            cipherBytes = Base64.getDecoder().decode(ciphertextBase64);
        } catch (IllegalArgumentException e) {
            throw new CryptoException("encrypted value is not valid base64", e);
        }

        try {
            RSAKey rsaKey = keyStoreLoader.getRsaKey();
            RSAPrivateKey privateKey = rsaKey.toRSAPrivateKey();
            if (privateKey == null) {
                throw new CryptoException("No RSA private key available in the keystore");
            }

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] plainBytes = cipher.doFinal(cipherBytes);

            log.info("RSA decrypted {} bytes ciphertext -> {} bytes plaintext", cipherBytes.length, plainBytes.length);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new CryptoException("RSA decryption failed: " + e.getMessage(), e);
        }
    }

    public String getAlgorithm() {
        return TRANSFORMATION;
    }
}
