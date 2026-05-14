package com.odea.oauth2client.crypto;

import javax.validation.constraints.NotBlank;

public class EncryptRequest {

    @NotBlank
    private String data;

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }
}
