package com.firstapi.backend.model;

public class RelayErrorResponse {
    private final ErrorBody error;

    public RelayErrorResponse(String message, String code) {
        this.error = new ErrorBody(message, code);
    }

    public ErrorBody getError() {
        return error;
    }

    public static class ErrorBody {
        private final String message;
        private final String type;
        private final String code;

        public ErrorBody(String message, String code) {
            this.message = message;
            this.type = "invalid_request_error";
            this.code = code;
        }

        public String getMessage() {
            return message;
        }

        public String getType() {
            return type;
        }

        public String getCode() {
            return code;
        }
    }
}
