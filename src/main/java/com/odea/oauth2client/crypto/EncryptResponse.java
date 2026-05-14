package com.odea.oauth2client.crypto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"algorithm", "encrypted", "ciphertextLength"})
public class EncryptResponse {

    private String algorithm;
    private String encrypted;
    private Integer ciphertextLength;

    public EncryptResponse() {
    }

    public EncryptResponse(String algorithm, String encrypted) {
        this.algorithm = algorithm;
        this.encrypted = encrypted;
        this.ciphertextLength = encrypted == null ? null : Integer.valueOf(encrypted.length());
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public String getEncrypted() {
        return encrypted;
    }

    public void setEncrypted(String encrypted) {
        this.encrypted = encrypted;
    }

    public Integer getCiphertextLength() {
        return ciphertextLength;
    }

    public void setCiphertextLength(Integer ciphertextLength) {
        this.ciphertextLength = ciphertextLength;
    }
}
