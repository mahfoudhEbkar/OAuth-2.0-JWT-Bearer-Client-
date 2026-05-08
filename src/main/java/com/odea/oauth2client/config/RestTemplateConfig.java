package com.odea.oauth2client.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {

    private final OAuth2ClientProperties properties;

    public RestTemplateConfig(OAuth2ClientProperties properties) {
        this.properties = properties;
    }

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        OAuth2ClientProperties.Api api = properties.getApi();
        return builder
                .setConnectTimeout(Duration.ofMillis(api.getConnectTimeoutMs()))
                .setReadTimeout(Duration.ofMillis(api.getReadTimeoutMs()))
                .build();
    }
}
