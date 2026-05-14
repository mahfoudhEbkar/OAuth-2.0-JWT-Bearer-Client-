package com.odea.oauth2client.crypto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"algorithm", "data"})
public class DecryptResponse {

    private String algorithm;
    private String data;

    public DecryptResponse() {
    }

    public DecryptResponse(String algorithm, String data) {
        this.algorithm = algorithm;
        this.data = data;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }
}
