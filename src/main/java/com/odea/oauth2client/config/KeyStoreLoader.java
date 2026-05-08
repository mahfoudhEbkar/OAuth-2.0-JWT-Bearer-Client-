package com.odea.oauth2client.config;

import com.nimbusds.jose.jwk.RSAKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.ResourceUtils;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.Key;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

@Component
public class KeyStoreLoader {

    private static final Logger log = LoggerFactory.getLogger(KeyStoreLoader.class);
    private static final String KEYSTORE_TYPE = "PKCS12";

    private final OAuth2ClientProperties properties;
    private RSAKey rsaKey;

    public KeyStoreLoader(OAuth2ClientProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        OAuth2ClientProperties.Keystore ks = properties.getKeystore();
        try {
            KeyStore keyStore = KeyStore.getInstance(KEYSTORE_TYPE);
            try (InputStream in = openKeystore(ks.getPath())) {
                keyStore.load(in, ks.getPassword().toCharArray());
            }

            Key key = keyStore.getKey(ks.getAlias(), ks.getKeyPassword().toCharArray());
            if (!(key instanceof RSAPrivateKey)) {
                throw new IllegalStateException(
                        "Alias '" + ks.getAlias() + "' does not contain an RSA private key");
            }
            Certificate cert = keyStore.getCertificate(ks.getAlias());
            if (cert == null) {
                throw new IllegalStateException(
                        "No certificate found for alias '" + ks.getAlias() + "'");
            }
            if (!(cert.getPublicKey() instanceof RSAPublicKey)) {
                throw new IllegalStateException(
                        "Public key for alias '" + ks.getAlias() + "' is not RSA");
            }

            RSAKey.Builder builder = new RSAKey.Builder((RSAPublicKey) cert.getPublicKey())
                    .privateKey((RSAPrivateKey) key)
                    .keyID(properties.getKeyId() != null && !properties.getKeyId().isEmpty()
                            ? properties.getKeyId()
                            : ks.getAlias());

            this.rsaKey = builder.build();

            log.info("Loaded RSA key from keystore: alias={}, kid={}",
                    ks.getAlias(), this.rsaKey.getKeyID());
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to load PKCS12 keystore at " + ks.getPath() + ": " + e.getMessage(), e);
        }
    }

    public RSAKey getRsaKey() {
        if (rsaKey == null) {
            throw new IllegalStateException("RSA key has not been loaded yet");
        }
        return rsaKey;
    }

    private InputStream openKeystore(String path) throws Exception {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("oauth2.keystore.path must be set");
        }
        if (path.startsWith("classpath:")) {
            return ResourceUtils.getURL(path).openStream();
        }
        File f = new File(path);
        if (!f.exists() || !f.isFile()) {
            throw new IllegalArgumentException("Keystore file not found: " + path);
        }
        return new FileInputStream(f);
    }
}
