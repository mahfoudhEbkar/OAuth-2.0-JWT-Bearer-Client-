package com.odea.oauth2client.api;

import com.odea.oauth2client.config.OAuth2ClientProperties;
import com.odea.oauth2client.token.TokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class ThirdPartyApiClient {

    private static final Logger log = LoggerFactory.getLogger(ThirdPartyApiClient.class);

    private final TokenService tokenService;
    private final OAuth2ClientProperties properties;
    private final RestTemplate restTemplate;

    public ThirdPartyApiClient(TokenService tokenService,
                               OAuth2ClientProperties properties,
                               RestTemplate restTemplate) {
        this.tokenService = tokenService;
        this.properties = properties;
        this.restTemplate = restTemplate;
    }

    public <T> T get(String path, Class<T> responseType) {
        return execute(HttpMethod.GET, path, null, responseType);
    }

    public <T> T post(String path, Object body, Class<T> responseType) {
        return execute(HttpMethod.POST, path, body, responseType);
    }

    private <T> T execute(HttpMethod method, String path, Object body, Class<T> responseType) {
        String url = buildUrl(path);
        try {
            return doExecute(method, url, body, responseType, false);
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                log.info("Got 401 from upstream, refreshing token and retrying once: url={}", url);
                tokenService.invalidateAndRefresh();
                try {
                    return doExecute(method, url, body, responseType, true);
                } catch (HttpStatusCodeException retryEx) {
                    throw new ThirdPartyApiException(
                            "Upstream API returned " + retryEx.getRawStatusCode() + " after token refresh",
                            retryEx.getRawStatusCode(), retryEx);
                } catch (RestClientException retryEx) {
                    throw new ThirdPartyApiException(
                            "Upstream API call failed after token refresh: " + retryEx.getMessage(),
                            0, retryEx);
                }
            }
            throw new ThirdPartyApiException(
                    "Upstream API returned " + e.getRawStatusCode(),
                    e.getRawStatusCode(), e);
        } catch (RestClientException e) {
            throw new ThirdPartyApiException(
                    "Upstream API call failed: " + e.getMessage(), 0, e);
        }
    }

    private <T> T doExecute(HttpMethod method, String url, Object body, Class<T> responseType, boolean retried) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenService.getAccessToken());
        headers.setAccept(java.util.Collections.singletonList(MediaType.APPLICATION_JSON));
        if (body != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }

        HttpEntity<Object> request = new HttpEntity<Object>(body, headers);
        ResponseEntity<T> response = restTemplate.exchange(url, method, request, responseType);
        log.info("Upstream API {} {} -> {} (retried={})",
                method, url, response.getStatusCodeValue(), retried);
        return response.getBody();
    }

    private String buildUrl(String path) {
        String base = properties.getApi().getBaseUrl();
        if (base == null || base.isEmpty()) {
            throw new ThirdPartyApiException("oauth2.api.base-url is not configured", 0);
        }
        if (path == null) {
            return base;
        }
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        boolean baseSlash = base.endsWith("/");
        boolean pathSlash = path.startsWith("/");
        if (baseSlash && pathSlash) {
            return base + path.substring(1);
        }
        if (!baseSlash && !pathSlash) {
            return base + "/" + path;
        }
        return base + path;
    }
}
