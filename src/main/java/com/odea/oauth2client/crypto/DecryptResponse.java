package com.odea.oauth2client.crypto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Response body for {@code POST /api/crypto/decrypt}.
 *
 * <p>{@code data} is returned as native JSON: if the decrypted plaintext parses
 * as a JSON object or array, the caller sees a real object/array (no backslash
 * escaping in the response). If it parses as a non-structured value or fails
 * to parse at all, it is returned as a JSON string.
 *
 * <pre>
 * // encrypted JSON object round-trips clean:
 * { "algorithm": "...", "data": { "firstname": "mahfoudh", "id": 42 } }
 *
 * // encrypted plain string round-trips as a string:
 * { "algorithm": "...", "data": "mahfoudh" }
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"algorithm", "data"})
public class DecryptResponse {

    private String algorithm;
    private JsonNode data;

    public DecryptResponse() {
    }

    public DecryptResponse(String algorithm, JsonNode data) {
        this.algorithm = algorithm;
        this.data = data;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    public JsonNode getData() {
        return data;
    }

    public void setData(JsonNode data) {
        this.data = data;
    }
}
