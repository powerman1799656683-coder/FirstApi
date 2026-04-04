package com.firstapi.backend.controller;

import com.firstapi.backend.model.RelayErrorResponse;
import com.firstapi.backend.model.RelayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = RelayController.class)
public class RelayExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RelayExceptionHandler.class);

    @ExceptionHandler(RelayException.class)
    public ResponseEntity<RelayErrorResponse> handleRelayException(RelayException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(new RelayErrorResponse(ex.getMessage(), ex.getCode()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<RelayErrorResponse> handleInvalidJson(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new RelayErrorResponse("Invalid request body", "invalid_request"));
    }

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleClientDisconnect(AsyncRequestNotUsableException ex) {
        // 客户端已断开连接，无法写回任何响应，静默忽略
        LOGGER.debug("Client disconnected during relay: {}", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<RelayErrorResponse> handleUnexpected(Exception ex) {
        LOGGER.error("Unexpected relay error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new RelayErrorResponse("Internal server error", "internal_error"));
    }
}
