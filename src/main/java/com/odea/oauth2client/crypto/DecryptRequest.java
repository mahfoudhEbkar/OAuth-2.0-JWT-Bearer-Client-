package com.odea.oauth2client.crypto;

import javax.validation.constraints.NotBlank;

public class DecryptRequest {

    @NotBlank
    private String encrypted;

    public String getEncrypted() {
        return encrypted;
    }

    public void setEncrypted(String encrypted) {
        this.encrypted = encrypted;
    }
}
