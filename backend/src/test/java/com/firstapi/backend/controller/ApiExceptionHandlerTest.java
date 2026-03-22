package com.firstapi.backend.controller;

import com.firstapi.backend.common.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

class ApiExceptionHandlerTest {

    private ApiExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ApiExceptionHandler();
    }

    @Test
    @DisplayName("maps ResponseStatusException to HTTP status")
    void shouldHandleResponseStatusException() {
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.NOT_FOUND, "resource not found");
        ResponseEntity<ApiResponse<Void>> resp = handler.handleResponseStatus(ex);
        assertEquals(404, resp.getStatusCode().value());
        assertFalse(resp.getBody().isSuccess());
        assertEquals("resource not found", resp.getBody().getMessage());
    }

    @Test
    @DisplayName("uses fallback message when ResponseStatusException has no reason")
    void shouldUseDefaultMessageWhenNoReason() {
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.BAD_REQUEST);
        ResponseEntity<ApiResponse<Void>> resp = handler.handleResponseStatus(ex);
        assertEquals(400, resp.getStatusCode().value());
        assertEquals("Request failed", resp.getBody().getMessage());
    }

    @Test
    @DisplayName("maps IllegalArgumentException to 400")
    void shouldHandleIllegalArgument() {
        IllegalArgumentException ex = new IllegalArgumentException("invalid parameter");
        ResponseEntity<ApiResponse<Void>> resp = handler.handleIllegalArgument(ex);
        assertEquals(400, resp.getStatusCode().value());
        assertEquals("invalid parameter", resp.getBody().getMessage());
    }

    @Test
    @DisplayName("maps HttpRequestMethodNotSupportedException to 405")
    void shouldHandleMethodNotSupported() {
        HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException("PATCH");
        ResponseEntity<ApiResponse<Void>> resp = handler.handleMethodNotSupported(ex);
        assertEquals(405, resp.getStatusCode().value());
        assertTrue(resp.getBody().getMessage().contains("PATCH"));
    }

    @Test
    @DisplayName("maps HttpMessageNotReadableException to 400")
    void shouldHandleMessageNotReadable() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("parse error", (org.springframework.http.HttpInputMessage) null);
        ResponseEntity<ApiResponse<Void>> resp = handler.handleMessageNotReadable(ex);
        assertEquals(400, resp.getStatusCode().value());
        assertEquals("Request body format is invalid", resp.getBody().getMessage());
    }

    @Test
    @DisplayName("maps generic DataIntegrityViolationException to conflict")
    void shouldHandleGenericDataIntegrity() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("some violation");
        ResponseEntity<ApiResponse<Void>> resp = handler.handleDataIntegrity(ex);
        assertEquals(409, resp.getStatusCode().value());
        assertEquals("Duplicate data or database constraint violation", resp.getBody().getMessage());
    }

    @Test
    @DisplayName("maps unexpected exception to 500")
    void shouldHandleUnexpectedException() {
        Exception ex = new RuntimeException("unexpected error");
        ResponseEntity<ApiResponse<Void>> resp = handler.handleUnexpected(ex);
        assertEquals(500, resp.getStatusCode().value());
        assertEquals("Internal server error", resp.getBody().getMessage());
    }
}
