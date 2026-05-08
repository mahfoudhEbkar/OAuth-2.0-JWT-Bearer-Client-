package com.odea.oauth2client.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Validated
@ConfigurationProperties(prefix = "oauth2")
public class OAuth2ClientProperties {

    @NotBlank
    private String clientId;

    @NotBlank
    private String tokenEndpoint;

    private String scope;

    private String keyId;

    @Min(60)
    private int assertionTtlSeconds = 300;

    private boolean sendClientIdInForm = false;

    @Valid
    @NotNull
    private Keystore keystore = new Keystore();

    @Valid
    @NotNull
    private Api api = new Api();

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getTokenEndpoint() {
        return tokenEndpoint;
    }

    public void setTokenEndpoint(String tokenEndpoint) {
        this.tokenEndpoint = tokenEndpoint;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }

    public int getAssertionTtlSeconds() {
        return assertionTtlSeconds;
    }

    public void setAssertionTtlSeconds(int assertionTtlSeconds) {
        this.assertionTtlSeconds = assertionTtlSeconds;
    }

    public boolean isSendClientIdInForm() {
        return sendClientIdInForm;
    }

    public void setSendClientIdInForm(boolean sendClientIdInForm) {
        this.sendClientIdInForm = sendClientIdInForm;
    }

    public Keystore getKeystore() {
        return keystore;
    }

    public void setKeystore(Keystore keystore) {
        this.keystore = keystore;
    }

    public Api getApi() {
        return api;
    }

    public void setApi(Api api) {
        this.api = api;
    }

    public static class Keystore {

        @NotBlank
        private String path;

        @NotBlank
        private String password;

        @NotBlank
        private String alias;

        @NotBlank
        private String keyPassword;

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getAlias() {
            return alias;
        }

        public void setAlias(String alias) {
            this.alias = alias;
        }

        public String getKeyPassword() {
            return keyPassword;
        }

        public void setKeyPassword(String keyPassword) {
            this.keyPassword = keyPassword;
        }
    }

    public static class Api {

        @NotBlank
        private String baseUrl;

        @Min(100)
        private int connectTimeoutMs = 5000;

        @Min(100)
        private int readTimeoutMs = 15000;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public int getReadTimeoutMs() {
            return readTimeoutMs;
        }

        public void setReadTimeoutMs(int readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
        }
    }
}
