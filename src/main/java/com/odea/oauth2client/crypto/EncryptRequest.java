package com.odea.oauth2client.crypto;

import com.fasterxml.jackson.databind.JsonNode;

import javax.validation.constraints.NotNull;

/**
 * Request body for {@code POST /api/crypto/encrypt}.
 *
 * <p>{@code data} can be any JSON value — a string, an object, an array, a
 * number, a boolean. The controller serializes structured values to canonical
 * JSON before encrypting, so callers do not need to backslash-escape inner
 * quotes when encrypting a JSON payload. Both of these are valid:
 *
 * <pre>
 * { "data": "mahfoudh" }                              // plain string
 * { "data": { "firstname": "mahfoudh", "id": 42 } }   // JSON object, no escaping
 * { "data": [1, 2, 3] }                               // JSON array
 * </pre>
 */
public class EncryptRequest {

    @NotNull
    private JsonNode data;

    public JsonNode getData() {
        return data;
    }

    public void setData(JsonNode data) {
        this.data = data;
    }
}
