package com.odea.oauth2client.config;

import com.odea.oauth2client.api.ThirdPartyApiException;
import com.odea.oauth2client.crypto.CryptoException;
import com.odea.oauth2client.jwt.ClientAssertionException;
import com.odea.oauth2client.token.TokenAcquisitionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ClientAssertionException.class)
    public ResponseEntity<Map<String, Object>> handleClientAssertion(ClientAssertionException ex) {
        log.error("Client assertion creation failed: {}", ex.getMessage());
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("error", "client_assertion_failure");
        body.put("message", safeMessage(ex));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    @ExceptionHandler(TokenAcquisitionException.class)
    public ResponseEntity<Map<String, Object>> handleTokenAcquisition(TokenAcquisitionException ex) {
        log.error("Token acquisition failed: status={}, upstream={}",
                ex.getStatus(), ex.getUpstreamError());
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("error", "token_acquisition_failure");
        body.put("message", safeMessage(ex));
        body.put("upstream_error", ex.getUpstreamError());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(body);
    }

    @ExceptionHandler(ThirdPartyApiException.class)
    public ResponseEntity<Map<String, Object>> handleApi(ThirdPartyApiException ex) {
        log.error("Third-party API call failed: status={}, message={}",
                ex.getStatus(), ex.getMessage());
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("error", "upstream_api_failure");
        body.put("message", safeMessage(ex));
        body.put("status", ex.getStatus());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(body);
    }

    @ExceptionHandler(CryptoException.class)
    public ResponseEntity<Map<String, Object>> handleCrypto(CryptoException ex) {
        log.warn("Crypto operation failed: {}", ex.getMessage());
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("error", "crypto_failure");
        body.put("message", safeMessage(ex));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("error", "internal_error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private String safeMessage(Throwable t) {
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }
}
