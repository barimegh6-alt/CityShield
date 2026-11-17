package com.cityshield.exception;

import com.cityshield.util.Json;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Converts every failure into the documented JSON error body. */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private ResponseEntity<Map<String, Object>> body(int status, String code, String message, List<String> details, HttpServletRequest req) {
        Map<String, Object> m = Json.obj("timestamp", LocalDateTime.now().withNano(0).toString(), "status", status, "code", code,
                "message", message, "details", details == null ? List.of() : details, "path", req.getRequestURI());
        return ResponseEntity.status(status).body(m);
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> api(ApiException e, HttpServletRequest req) {
        return body(e.getStatus(), e.getCode(), e.getMessage(), e.getDetails(), req);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> invalid(MethodArgumentNotValidException e, HttpServletRequest req) {
        List<String> details = new ArrayList<>();
        e.getBindingResult().getFieldErrors().forEach(f -> details.add(f.getField() + ": " + f.getDefaultMessage()));
        e.getBindingResult().getGlobalErrors().forEach(g -> details.add(g.getDefaultMessage()));
        return body(400, "VALIDATION_FAILED", "Validation failed", details, req);
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<Map<String, Object>> badRequest(Exception e, HttpServletRequest req) {
        String msg = e instanceof HttpMessageNotReadableException ? "Malformed or unreadable request body" : e.getMessage();
        return body(400, "INVALID_ARGUMENT", msg, null, req);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> integrity(DataIntegrityViolationException e, HttpServletRequest req) {
        log.warn("Data integrity violation: {}", e.getMostSpecificCause().getMessage());
        return body(409, "CONFLICT", "The change conflicts with existing data (duplicate or referenced record)", null, req);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> noResource(NoResourceFoundException e, HttpServletRequest req) {
        return body(404, "NOT_FOUND", "No such endpoint", null, req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> other(Exception e, HttpServletRequest req) {
        log.error("Unhandled error on {}", req.getRequestURI(), e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR.value(), "INTERNAL_ERROR", "Unexpected server error", null, req);
    }
}
