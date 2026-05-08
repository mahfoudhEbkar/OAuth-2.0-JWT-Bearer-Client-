package com.odea.oauth2client.jwt;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.odea.oauth2client.config.KeyStoreLoader;
import com.odea.oauth2client.config.OAuth2ClientProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class ClientAssertionService {

    private static final Logger log = LoggerFactory.getLogger(ClientAssertionService.class);

    private final KeyStoreLoader keyStoreLoader;
    private final OAuth2ClientProperties properties;

    public ClientAssertionService(KeyStoreLoader keyStoreLoader, OAuth2ClientProperties properties) {
        this.keyStoreLoader = keyStoreLoader;
        this.properties = properties;
    }

    public String createAssertion() {
        try {
            RSAKey rsaKey = keyStoreLoader.getRsaKey();

            Instant now = Instant.now();
            Instant exp = now.plusSeconds(properties.getAssertionTtlSeconds());
            String jti = UUID.randomUUID().toString();

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(properties.getClientId())
                    .subject(properties.getClientId())
                    .audience(properties.getTokenEndpoint())
                    .expirationTime(Date.from(exp))
                    .issueTime(Date.from(now))
                    .jwtID(jti)
                    .build();

            JWSHeader.Builder headerBuilder = new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .type(JOSEObjectType.JWT);
            String kid = properties.getKeyId();
            if (kid != null && !kid.isEmpty()) {
                headerBuilder.keyID(kid);
            }
            JWSHeader header = headerBuilder.build();

            SignedJWT signedJWT = new SignedJWT(header, claims);
            signedJWT.sign(new RSASSASigner(rsaKey));

            log.debug("Built JWT client assertion: header={}, jti={}, exp={}",
                    header.toJSONObject(), jti, exp);

            return signedJWT.serialize();
        } catch (JOSEException e) {
            throw new ClientAssertionException("Failed to sign JWT client assertion: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new ClientAssertionException("Failed to build JWT client assertion: " + e.getMessage(), e);
        }
    }
}
