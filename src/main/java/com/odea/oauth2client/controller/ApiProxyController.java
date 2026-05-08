package com.odea.oauth2client.controller;

import com.odea.oauth2client.api.ThirdPartyApiClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/proxy")
public class ApiProxyController {

    private final ThirdPartyApiClient apiClient;

    public ApiProxyController(ThirdPartyApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @GetMapping("/{*path}")
    public Object proxy(@PathVariable("path") String path) {
        String upstreamPath = path == null ? "" : path;
        return apiClient.get(upstreamPath, Object.class);
    }
}
