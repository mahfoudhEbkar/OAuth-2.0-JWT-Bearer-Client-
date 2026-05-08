package com.odea.oauth2client.jwt;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.odea.oauth2client.config.KeyStoreLoader;
import com.odea.oauth2client.config.OAuth2ClientProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClientAssertionServiceTest {

    private static RSAKey rsaKey;

    private OAuth2ClientProperties properties;
    private KeyStoreLoader keyStoreLoader;
    private ClientAssertionService service;

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
        properties = new OAuth2ClientProperties();
        properties.setClientId("test-client-id");
        properties.setTokenEndpoint("https://auth.example.com/oauth2/token");
        properties.setKeyId("test-kid");
        properties.setAssertionTtlSeconds(300);

        keyStoreLoader = mock(KeyStoreLoader.class);
        when(keyStoreLoader.getRsaKey()).thenReturn(rsaKey);

        service = new ClientAssertionService(keyStoreLoader, properties);
    }

    @Test
    void createsValidSignedJwtWithExpectedClaims() throws Exception {
        long before = System.currentTimeMillis() / 1000L;
        String assertion = service.createAssertion();
        long after = System.currentTimeMillis() / 1000L;

        SignedJWT parsed = SignedJWT.parse(assertion);
        boolean signatureValid = parsed.verify(new RSASSAVerifier(rsaKey.toRSAPublicKey()));
        assertThat(signatureValid).isTrue();

        assertThat(parsed.getHeader().getAlgorithm().getName()).isEqualTo("RS256");
        assertThat(parsed.getHeader().getType().getType()).isEqualTo("JWT");
        assertThat(parsed.getHeader().getKeyID()).isEqualTo("test-kid");

        JWTClaimsSet claims = parsed.getJWTClaimsSet();
        assertThat(claims.getIssuer()).isEqualTo("test-client-id");
        assertThat(claims.getSubject()).isEqualTo("test-client-id");
        assertThat(claims.getAudience()).containsExactly("https://auth.example.com/oauth2/token");
        assertThat(claims.getJWTID()).isNotBlank();

        Date iat = claims.getIssueTime();
        Date exp = claims.getExpirationTime();
        assertThat(iat).isNotNull();
        assertThat(exp).isNotNull();
        long iatSec = iat.getTime() / 1000L;
        long expSec = exp.getTime() / 1000L;
        assertThat(iatSec).isBetween(before, after);
        assertThat(expSec - iatSec).isEqualTo(300L);
    }

    @Test
    void omitsKidHeaderWhenKeyIdNotConfigured() throws Exception {
        properties.setKeyId(null);
        String assertion = service.createAssertion();
        SignedJWT parsed = SignedJWT.parse(assertion);
        assertThat(parsed.getHeader().getKeyID()).isNull();
    }

    @Test
    void eachAssertionHasUniqueJti() throws Exception {
        SignedJWT a = SignedJWT.parse(service.createAssertion());
        SignedJWT b = SignedJWT.parse(service.createAssertion());
        assertThat(a.getJWTClaimsSet().getJWTID())
                .isNotEqualTo(b.getJWTClaimsSet().getJWTID());
    }
}
