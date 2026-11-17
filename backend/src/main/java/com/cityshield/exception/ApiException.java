package com.cityshield.exception;

import java.util.List;

/** Base class of all API errors that map to a specific HTTP status and error code. */
public class ApiException extends RuntimeException {
    private final int status;
    private final String code;
    private final List<String> details;

    public ApiException(int status, String code, String message, List<String> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details == null ? List.of() : details;
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public List<String> getDetails() {
        return details;
    }

    public static ApiException badRequest(String message) {
        return new ApiException(400, "INVALID_ARGUMENT", message, null);
    }

    public static ApiException validation(List<String> details) {
        return new ApiException(400, "VALIDATION_FAILED", "Validation failed", details);
    }

    public static ApiException notFound(String message) {
        return new ApiException(404, "NOT_FOUND", message, null);
    }

    public static ApiException conflict(String message) {
        return new ApiException(409, "CONFLICT", message, null);
    }

    public static ApiException infeasible(String message, List<String> details) {
        return new ApiException(422, "INFEASIBLE", message, details);
    }
}
