package com.locationreview.location_review_platform.service;

import java.util.Map;
import org.springframework.http.HttpStatus;

public class AccountException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final Map<String, String> fieldErrors;
    public AccountException(HttpStatus status, String code, String message, Map<String, String> fieldErrors) {
        super(message); this.status = status; this.code = code; this.fieldErrors = Map.copyOf(fieldErrors);
    }
    public HttpStatus status() { return status; }
    public String code() { return code; }
    public Map<String, String> fieldErrors() { return fieldErrors; }
}
