package com.bank.transaction.exception;

import com.bank.transaction.dto.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void businessMessagesWrittenByTheAppAreShown() {
        ResponseEntity<ApiResponse<String>> response =
                handler.handleRuntime(new RuntimeException("Insufficient funds in your account."));

        assertEquals(400, response.getStatusCode().value());
        assertEquals("Insufficient funds in your account.", response.getBody().getMessage());
    }

    @Test
    void validationMessagesFromIllegalArgumentAreShown() {
        ResponseEntity<ApiResponse<String>> response =
                handler.handleRuntime(new IllegalArgumentException("Account numbers use the format UB-0000."));

        assertEquals(400, response.getStatusCode().value());
        assertEquals("Account numbers use the format UB-0000.", response.getBody().getMessage());
    }

    @Test
    void databaseErrorsNeverShowSqlOrTableNames() {
        ResponseEntity<ApiResponse<String>> response = handler.handleDatabaseError(new DataIntegrityViolationException(
                "could not execute statement [ERROR: duplicate key value violates unique constraint \"users_email_key\"]"));

        assertEquals(500, response.getStatusCode().value());
        String message = response.getBody().getMessage();
        assertFalse(message.contains("users_email_key"));
        assertFalse(message.toLowerCase().contains("constraint"));
        assertTrue(message.contains("Reference:"));
    }

    @Test
    void unexpectedJvmErrorsAreHidden() {
        ResponseEntity<ApiResponse<String>> response = handler.handleRuntime(new NullPointerException(
                "Cannot invoke \"com.bank.transaction.domain.Account.getBalance()\" because \"source\" is null"));

        assertEquals(500, response.getStatusCode().value());
        assertFalse(response.getBody().getMessage().contains("com.bank"));
        assertEquals("ERROR", response.getBody().getStatus());
    }

    @Test
    void libraryExceptionsThatExtendIllegalArgumentAreHidden() {
        ResponseEntity<ApiResponse<String>> response =
                handler.handleRuntime(new NumberFormatException("For input string: \"abc\" under radix 10"));

        assertEquals(500, response.getStatusCode().value());
        assertFalse(response.getBody().getMessage().contains("radix"));
    }

    @Test
    void malformedJsonGetsAPlainMessage() {
        ResponseEntity<ApiResponse<String>> response = handler.handleUnreadableBody(new HttpMessageNotReadableException(
                "JSON parse error: Cannot deserialize value of type `java.math.BigDecimal` from String \"ten\"",
                new MockHttpInputMessage(new byte[0])));

        assertEquals(400, response.getStatusCode().value());
        assertFalse(response.getBody().getMessage().contains("java.math"));
    }

    @Test
    void accessDeniedIsForbiddenNotBadRequest() {
        ResponseEntity<ApiResponse<String>> response =
                handler.handleAccessDenied(new AccessDeniedException("You cannot view another account's balance."));

        assertEquals(403, response.getStatusCode().value());
        assertEquals("You cannot view another account's balance.", response.getBody().getMessage());
    }
}
