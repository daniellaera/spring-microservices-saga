package com.daniellaera.authservice.adapter.in.web;

import com.daniellaera.authservice.domain.model.AuthenticationFailedException;
import com.daniellaera.authservice.domain.model.EmailAlreadyRegisteredException;
import com.daniellaera.authservice.domain.model.OtpLimitExceededException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
            fieldErrors.put(error.getField(), error.getDefaultMessage())
        );
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", 400);
        response.put("error", "Validation Failed");
        response.put("errors", fieldErrors);
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException ex) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", ex.getStatusCode().value());
        response.put("error", ex.getReason());
        // shop-ui shows `message` (e.g. "Too many invalid codes — please login again")
        response.put("message", ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(response);
    }

    // the domain's 401 and 429, with the body a ResponseStatusException had when they were ones
    @ExceptionHandler({AuthenticationFailedException.class, OtpLimitExceededException.class})
    public ResponseEntity<Map<String, Object>> handleAuthenticationFailure(RuntimeException ex) {
        HttpStatus status = ex instanceof OtpLimitExceededException ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.UNAUTHORIZED;
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", status.value());
        response.put("error", ex.getMessage());
        response.put("message", ex.getMessage());
        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler({DataIntegrityViolationException.class, EmailAlreadyRegisteredException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    public ResponseEntity<Map<String, Object>> handleDuplicateKey(RuntimeException ex) {
        return ResponseEntity.status(409).body(Map.of(
                "status", 409,
                "error", "Conflict",
                "message", "An account with this email already exists"
        ));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException ex) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", 400);
        response.put("error", "Bad Request");
        response.put("message", ex.getMessage());
        return ResponseEntity.badRequest().body(response);
    }
}
