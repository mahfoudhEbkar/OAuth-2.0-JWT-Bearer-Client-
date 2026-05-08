package com.odea.oauth2client.token;

public class TokenAcquisitionException extends RuntimeException {

    private final int status;
    private final String upstreamError;

    public TokenAcquisitionException(String message, int status, String upstreamError) {
        super(message);
        this.status = status;
        this.upstreamError = upstreamError;
    }

    public TokenAcquisitionException(String message, int status, String upstreamError, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.upstreamError = upstreamError;
    }

    public int getStatus() {
        return status;
    }

    public String getUpstreamError() {
        return upstreamError;
    }
}
