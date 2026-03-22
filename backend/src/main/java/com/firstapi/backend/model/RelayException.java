package com.firstapi.backend.model;

import org.springframework.http.HttpStatus;

public class RelayException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public RelayException(HttpStatus status, String message, String code) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
