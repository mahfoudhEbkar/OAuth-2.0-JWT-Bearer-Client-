package com.odea.oauth2client.controller;

import com.odea.oauth2client.config.OAuth2ClientProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final OAuth2ClientProperties properties;

    public HealthController(OAuth2ClientProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("status", "UP");
        body.put("clientId", maskClientId(properties.getClientId()));
        return body;
    }

    private String maskClientId(String id) {
        if (id == null || id.length() <= 4) {
            return "****";
        }
        return id.substring(0, 4) + "****";
    }
}
