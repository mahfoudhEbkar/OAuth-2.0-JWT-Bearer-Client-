package com.odea.oauth2client.api;

public class ThirdPartyApiException extends RuntimeException {

    private final int status;

    public ThirdPartyApiException(String message, int status) {
        super(message);
        this.status = status;
    }

    public ThirdPartyApiException(String message, int status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
