package com.odea.oauth2client.token;

import com.odea.oauth2client.config.OAuth2ClientProperties;
import com.odea.oauth2client.jwt.ClientAssertionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TokenServiceTest {

    private OAuth2ClientProperties properties;
    private ClientAssertionService clientAssertionService;
    private RestTemplate restTemplate;
    private TokenService service;

    @BeforeEach
    void setUp() {
        properties = new OAuth2ClientProperties();
        properties.setClientId("test-client-id");
        properties.setTokenEndpoint("https://auth.example.com/oauth2/token");
        properties.setScope("api.read");

        clientAssertionService = mock(ClientAssertionService.class);
        when(clientAssertionService.createAssertion()).thenReturn("signed.jwt.value");

        restTemplate = mock(RestTemplate.class);
        service = new TokenService(clientAssertionService, properties, restTemplate);
    }

    @Test
    void firstCallFetchesNewToken() {
        TokenResponse body = tokenResponse("token-1", 3600L);
        when(restTemplate.postForEntity(
                eq("https://auth.example.com/oauth2/token"),
                any(),
                eq(TokenResponse.class)))
                .thenReturn(ResponseEntity.ok(body));

        String t = service.getAccessToken();
        assertThat(t).isEqualTo("token-1");
        verify(restTemplate, times(1))
                .postForEntity(any(String.class), any(), eq(TokenResponse.class));
    }

    @Test
    void secondCallWithinWindowReturnsCachedToken() {
        TokenResponse body = tokenResponse("token-cached", 3600L);
        when(restTemplate.postForEntity(any(String.class), any(), eq(TokenResponse.class)))
                .thenReturn(ResponseEntity.ok(body));

        String first = service.getAccessToken();
        String second = service.getAccessToken();

        assertThat(first).isEqualTo("token-cached");
        assertThat(second).isEqualTo("token-cached");
        verify(restTemplate, times(1))
                .postForEntity(any(String.class), any(), eq(TokenResponse.class));
    }

    @Test
    void callAfterExpiryFetchesNewToken() throws Exception {
        TokenResponse first = tokenResponse("token-old", 3600L);
        TokenResponse second = tokenResponse("token-new", 3600L);
        when(restTemplate.postForEntity(any(String.class), any(), eq(TokenResponse.class)))
                .thenReturn(ResponseEntity.ok(first))
                .thenReturn(ResponseEntity.ok(second));

        String t1 = service.getAccessToken();
        assertThat(t1).isEqualTo("token-old");

        forceCacheExpiry(service);

        String t2 = service.getAccessToken();
        assertThat(t2).isEqualTo("token-new");
        verify(restTemplate, times(2))
                .postForEntity(any(String.class), any(), eq(TokenResponse.class));
    }

    @Test
    void nonSuccessResponseThrowsTokenAcquisitionException() {
        when(restTemplate.postForEntity(any(String.class), any(), eq(TokenResponse.class)))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.UNAUTHORIZED,
                        "Unauthorized",
                        org.springframework.http.HttpHeaders.EMPTY,
                        "{\"error\":\"invalid_client\"}".getBytes(),
                        null));

        assertThatThrownBy(() -> service.getAccessToken())
                .isInstanceOf(TokenAcquisitionException.class)
                .hasMessageContaining("401");
    }

    @Test
    void invalidateAndRefreshClearsCacheAndFetches() {
        TokenResponse first = tokenResponse("a", 3600L);
        TokenResponse second = tokenResponse("b", 3600L);
        when(restTemplate.postForEntity(any(String.class), any(), eq(TokenResponse.class)))
                .thenReturn(ResponseEntity.ok(first))
                .thenReturn(ResponseEntity.ok(second));

        assertThat(service.getAccessToken()).isEqualTo("a");
        service.invalidateAndRefresh();
        assertThat(service.getAccessToken()).isEqualTo("b");
        verify(restTemplate, times(2))
                .postForEntity(any(String.class), any(), eq(TokenResponse.class));
    }

    @Test
    void emptyAccessTokenThrows() {
        TokenResponse body = tokenResponse("", 3600L);
        when(restTemplate.postForEntity(any(String.class), any(), eq(TokenResponse.class)))
                .thenReturn(ResponseEntity.ok(body));

        assertThatThrownBy(() -> service.getAccessToken())
                .isInstanceOf(TokenAcquisitionException.class);
        verify(clientAssertionService, times(1)).createAssertion();
        verify(restTemplate, never()).getForEntity(any(String.class), any());
    }

    private TokenResponse tokenResponse(String token, long expiresIn) {
        TokenResponse r = new TokenResponse();
        r.setAccessToken(token);
        r.setTokenType("Bearer");
        r.setExpiresIn(expiresIn);
        r.setScope("api.read");
        return r;
    }

    private void forceCacheExpiry(TokenService service) throws Exception {
        Field expiryField = TokenService.class.getDeclaredField("cachedExpiry");
        expiryField.setAccessible(true);
        expiryField.set(service, Instant.now().minusSeconds(10));
    }
}
