package com.odea.oauth2client.jwt;

public class ClientAssertionException extends RuntimeException {

    public ClientAssertionException(String message) {
        super(message);
    }

    public ClientAssertionException(String message, Throwable cause) {
        super(message, cause);
    }
}
