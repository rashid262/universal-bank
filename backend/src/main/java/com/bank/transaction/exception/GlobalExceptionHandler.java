package com.bank.transaction.exception;

import com.bank.transaction.dto.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Set;
import java.util.UUID;

/**
 * Turns exceptions into API responses.
 *
 * Messages this application writes for users (business rules, validation) are passed through.
 * Anything from a library, the database or the JVM can contain SQL, class names or stack details,
 * so the client gets a generic message with a short reference, and the details go to the server log.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // Exact types the application throws with user-facing messages. Subclasses from libraries
    // (for example NumberFormatException) are deliberately not included.
    private static final Set<Class<?>> USER_MESSAGE_TYPES = Set.of(
            RuntimeException.class, IllegalArgumentException.class, IllegalStateException.class,
            CurrencyMismatchException.class, UnauthorizedAccessException.class);

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ApiResponse<String>> handleAccountNotFound(AccountNotFoundException ex) {
        return failed(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    public ResponseEntity<ApiResponse<String>> handleInsufficientBalance(InsufficientBalanceException ex) {
        return failed(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ApiResponse<String>> handleTooManyRequests(TooManyRequestsException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(new ApiResponse<>("FAILED", ex.getMessage(), null));
    }

    // Ownership checks in controllers (another customer's balance or history): forbidden, not a bad request
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<String>> handleAccessDenied(AccessDeniedException ex) {
        return failed(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<String>> handleInvalidRequest(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage())
                .findFirst()
                .orElse("Request validation failed.");
        return failed(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<String>> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(violation -> violation.getMessage())
                .findFirst()
                .orElse("Request validation failed.");
        return failed(HttpStatus.BAD_REQUEST, message);
    }

    // Spring's own messages for these name internal types (JSON parser, Java classes), so they are rewritten
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<String>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return failed(HttpStatus.BAD_REQUEST, "The request body is missing or is not valid JSON.");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<String>> handleMissingParameter(MissingServletRequestParameterException ex) {
        return failed(HttpStatus.BAD_REQUEST, "Missing required parameter '" + ex.getParameterName() + "'.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<String>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return failed(HttpStatus.BAD_REQUEST, "Invalid value for '" + ex.getName() + "'.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<String>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return failed(HttpStatus.METHOD_NOT_ALLOWED, "This method is not allowed for this address.");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<String>> handleNotFound(NoResourceFoundException ex) {
        return failed(HttpStatus.NOT_FOUND, "Not found.");
    }

    // Database errors can include table, column and constraint names: never shown to the client
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<String>> handleDatabaseError(DataAccessException ex) {
        return unexpected(ex);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<String>> handleRuntime(RuntimeException ex) {
        if (USER_MESSAGE_TYPES.contains(ex.getClass()) && ex.getMessage() != null) {
            return failed(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
        return unexpected(ex);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<String>> handleGeneralErrors(Exception ex) {
        return unexpected(ex);
    }

    private static ResponseEntity<ApiResponse<String>> failed(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ApiResponse<>("FAILED", message, null));
    }

    /** Logs the full error under a short reference that the user can quote; reveals nothing else. */
    private static ResponseEntity<ApiResponse<String>> unexpected(Exception ex) {
        String reference = UUID.randomUUID().toString().substring(0, 8);
        log.error("Unexpected error [ref {}]", reference, ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>("ERROR",
                        "Something went wrong on our side. Please try again. (Reference: " + reference + ")", null));
    }
}
