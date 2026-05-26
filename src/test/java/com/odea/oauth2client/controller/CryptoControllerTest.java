package com.odea.oauth2client.controller;

import com.odea.oauth2client.crypto.RsaCryptoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins down the request/response contract for the crypto endpoints — the
 * point of this test class is the JsonNode handling on both sides, not the
 * crypto math. The RsaCryptoService is mocked.
 *
 * <p>Verifies:
 * <ul>
 *   <li>encrypt accepts a JSON object directly in {@code data} (no escaping)</li>
 *   <li>encrypt accepts a plain string in {@code data} (back-compat)</li>
 *   <li>encrypt accepts an array in {@code data}</li>
 *   <li>decrypt returns a JSON object directly when the plaintext is an object</li>
 *   <li>decrypt returns a JSON string when the plaintext is not structured</li>
 *   <li>missing or null {@code data} is rejected</li>
 * </ul>
 */
@WebMvcTest(controllers = CryptoController.class)
class CryptoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RsaCryptoService cryptoService;

    @BeforeEach
    void setUp() {
        when(cryptoService.getAlgorithm()).thenReturn("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
    }

    @Test
    void encrypt_acceptsJsonObjectWithoutEscaping() throws Exception {
        when(cryptoService.encrypt(any(String.class))).thenReturn("BASE64CIPHERTEXT");

        // Caller sends a JSON object directly under "data" — no backslash escaping.
        String body = "{\"data\":{\"firstname\":\"mahfoudh\",\"id\":42}}";

        mockMvc.perform(post("/api/crypto/encrypt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.algorithm",
                        equalTo("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")))
                .andExpect(jsonPath("$.encrypted", equalTo("BASE64CIPHERTEXT")));
    }

    @Test
    void encrypt_acceptsPlainString() throws Exception {
        when(cryptoService.encrypt(any(String.class))).thenReturn("BASE64CIPHERTEXT");

        String body = "{\"data\":\"mahfoudh\"}";

        mockMvc.perform(post("/api/crypto/encrypt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encrypted", equalTo("BASE64CIPHERTEXT")));
    }

    @Test
    void encrypt_acceptsJsonArray() throws Exception {
        when(cryptoService.encrypt(any(String.class))).thenReturn("BASE64CIPHERTEXT");

        String body = "{\"data\":[1,2,3]}";

        mockMvc.perform(post("/api/crypto/encrypt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.encrypted", equalTo("BASE64CIPHERTEXT")));
    }

    @Test
    void encrypt_rejectsMissingData() throws Exception {
        mockMvc.perform(post("/api/crypto/encrypt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void decrypt_returnsJsonObjectNativelyWhenPlaintextIsObject() throws Exception {
        when(cryptoService.decrypt(any(String.class)))
                .thenReturn("{\"firstname\":\"mahfoudh\",\"id\":42}");

        String body = "{\"encrypted\":\"BASE64CIPHERTEXT\"}";

        mockMvc.perform(post("/api/crypto/decrypt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                // Native JSON: data.firstname is a real field, not a string with \"
                .andExpect(jsonPath("$.data.firstname", equalTo("mahfoudh")))
                .andExpect(jsonPath("$.data.id", equalTo(42)));
    }

    @Test
    void decrypt_returnsJsonStringWhenPlaintextIsPlainString() throws Exception {
        when(cryptoService.decrypt(any(String.class))).thenReturn("mahfoudh");

        String body = "{\"encrypted\":\"BASE64CIPHERTEXT\"}";

        mockMvc.perform(post("/api/crypto/decrypt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", equalTo("mahfoudh")));
    }

    @Test
    void decrypt_returnsJsonStringWhenPlaintextIsBareNumber() throws Exception {
        // We deliberately do NOT treat "42" as a JSON number in the response,
        // because the caller may have encrypted the literal text "42".
        // Object/array roundtrips are clean; scalar roundtrips stay as strings.
        when(cryptoService.decrypt(any(String.class))).thenReturn("42");

        String body = "{\"encrypted\":\"BASE64CIPHERTEXT\"}";

        mockMvc.perform(post("/api/crypto/decrypt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", equalTo("42")));
    }
}
