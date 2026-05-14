package com.odea.oauth2client.crypto;

import com.nimbusds.jose.jwk.RSAKey;
import com.odea.oauth2client.config.KeyStoreLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RsaCryptoServiceTest {

    private static RSAKey rsaKey;

    private RsaCryptoService service;

    @BeforeAll
    static void generateKey() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair = gen.generateKeyPair();
        rsaKey = new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                .privateKey((RSAPrivateKey) pair.getPrivate())
                .keyID("test-key")
                .build();
    }

    @BeforeEach
    void setUp() {
        KeyStoreLoader loader = mock(KeyStoreLoader.class);
        when(loader.getRsaKey()).thenReturn(rsaKey);
        service = new RsaCryptoService(loader);
    }

    @Test
    void encryptDecryptRoundtripPreservesPlaintext() {
        String plaintext = "hello, world — JSON payloads OK too: {\"x\":42}";
        String ciphertext = service.encrypt(plaintext);
        assertThat(ciphertext).isNotBlank();
        assertThat(ciphertext).doesNotContain(plaintext);

        String roundtripped = service.decrypt(ciphertext);
        assertThat(roundtripped).isEqualTo(plaintext);
    }

    @Test
    void encryptIsNonDeterministicAcrossCalls() {
        // OAEP padding adds randomness, so two encryptions of the same plaintext
        // must produce different ciphertexts. This is a security property.
        String plaintext = "same-input";
        String a = service.encrypt(plaintext);
        String b = service.encrypt(plaintext);
        assertThat(a).isNotEqualTo(b);
        assertThat(service.decrypt(a)).isEqualTo(plaintext);
        assertThat(service.decrypt(b)).isEqualTo(plaintext);
    }

    @Test
    void encryptRejectsPayloadLargerThanRsaCapacity() {
        // For RSA-2048 with OAEP-SHA-256, max plaintext is 190 bytes.
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            sb.append("A");
        }
        assertThatThrownBy(() -> service.encrypt(sb.toString()))
                .isInstanceOf(CryptoException.class)
                .hasMessageContaining("too large");
    }

    @Test
    void encryptRejectsNullPlaintext() {
        assertThatThrownBy(() -> service.encrypt(null))
                .isInstanceOf(CryptoException.class)
                .hasMessageContaining("required");
    }

    @Test
    void decryptRejectsInvalidBase64() {
        assertThatThrownBy(() -> service.decrypt("not!!!valid!!!base64"))
                .isInstanceOf(CryptoException.class);
    }

    @Test
    void decryptRejectsTamperedCiphertext() {
        String ciphertext = service.encrypt("legitimate");
        // Flip a byte in the middle of the base64 stream — should fail to decrypt.
        char[] chars = ciphertext.toCharArray();
        int mid = chars.length / 2;
        chars[mid] = chars[mid] == 'A' ? 'B' : 'A';
        String tampered = new String(chars);
        assertThatThrownBy(() -> service.decrypt(tampered))
                .isInstanceOf(CryptoException.class)
                .hasMessageContaining("decryption failed");
    }

    @Test
    void algorithmIsOAEPWithSha256() {
        assertThat(service.getAlgorithm()).isEqualTo("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
    }
}
