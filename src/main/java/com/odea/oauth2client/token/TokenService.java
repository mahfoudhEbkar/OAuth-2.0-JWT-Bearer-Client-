package com.odea.oauth2client.token;

import com.odea.oauth2client.config.OAuth2ClientProperties;
import com.odea.oauth2client.jwt.ClientAssertionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;

@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);

    private static final String GRANT_TYPE = "client_credentials";
    private static final String CLIENT_ASSERTION_TYPE =
            "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";
    private static final long REFRESH_WINDOW_SECONDS = 60L;

    private final ClientAssertionService clientAssertionService;
    private final OAuth2ClientProperties properties;
    private final RestTemplate restTemplate;

    private volatile String cachedToken;
    private volatile Instant cachedExpiry = Instant.EPOCH;

    public TokenService(ClientAssertionService clientAssertionService,
                        OAuth2ClientProperties properties,
                        RestTemplate restTemplate) {
        this.clientAssertionService = clientAssertionService;
        this.properties = properties;
        this.restTemplate = restTemplate;
    }

    public String getAccessToken() {
        if (isCacheUsable()) {
            return cachedToken;
        }
        synchronized (this) {
            if (isCacheUsable()) {
                return cachedToken;
            }
            fetchNewToken();
            return cachedToken;
        }
    }

    public synchronized void invalidateAndRefresh() {
        log.info("Forcing token refresh: clientId={}", maskedClientId());
        cachedToken = null;
        cachedExpiry = Instant.EPOCH;
        fetchNewToken();
    }

    private boolean isCacheUsable() {
        return cachedToken != null
                && Instant.now().plusSeconds(REFRESH_WINDOW_SECONDS).isBefore(cachedExpiry);
    }

    private void fetchNewToken() {
        String assertion = clientAssertionService.createAssertion();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(java.util.Collections.singletonList(MediaType.APPLICATION_JSON));

        MultiValueMap<String, String> form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", GRANT_TYPE);
        form.add("client_assertion_type", CLIENT_ASSERTION_TYPE);
        form.add("client_assertion", assertion);

        String scope = properties.getScope();
        if (scope != null && !scope.isEmpty()) {
            form.add("scope", scope);
        }
        if (properties.isSendClientIdInForm()) {
            form.add("client_id", properties.getClientId());
        }

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<MultiValueMap<String, String>>(form, headers);
        String endpoint = properties.getTokenEndpoint();
        log.debug("Calling token endpoint: {}", endpoint);

        try {
            ResponseEntity<TokenResponse> response =
                    restTemplate.postForEntity(endpoint, request, TokenResponse.class);

            TokenResponse body = response.getBody();
            if (body == null || body.getAccessToken() == null || body.getAccessToken().isEmpty()) {
                throw new TokenAcquisitionException(
                        "Authorization Server returned empty access_token",
                        response.getStatusCodeValue(),
                        null);
            }

            long expiresIn = body.getExpiresIn() != null ? body.getExpiresIn().longValue() : 3600L;
            this.cachedToken = body.getAccessToken();
            this.cachedExpiry = Instant.now().plusSeconds(expiresIn);

            log.info("Acquired access token: clientId={}, expires_in={}",
                    maskedClientId(), expiresIn);
        } catch (HttpStatusCodeException e) {
            String errorBody = e.getResponseBodyAsString();
            throw new TokenAcquisitionException(
                    "Authorization Server returned " + e.getRawStatusCode(),
                    e.getRawStatusCode(),
                    errorBody,
                    e);
        } catch (RestClientException e) {
            throw new TokenAcquisitionException(
                    "Failed to call token endpoint: " + e.getMessage(),
                    0,
                    null,
                    e);
        }
    }

    private String maskedClientId() {
        String id = properties.getClientId();
        if (id == null || id.length() <= 4) {
            return "****";
        }
        return id.substring(0, 4) + "****";
    }
}
