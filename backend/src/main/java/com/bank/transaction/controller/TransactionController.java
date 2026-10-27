package com.bank.transaction.controller;

import com.bank.transaction.domain.Transaction;
import com.bank.transaction.dto.ApiResponse;
import com.bank.transaction.dto.CreateTransactionRequest;
import com.bank.transaction.service.TransactionService;
import com.bank.transaction.dto.TransferQuoteResponse;
import com.bank.transaction.repository.UserRepository;
import com.bank.transaction.enums.Role;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Validated
public class TransactionController {

    private final TransactionService transactionService;
    private final UserRepository userRepository;

    public TransactionController(TransactionService transactionService, UserRepository userRepository) {
        this.transactionService = transactionService;
        this.userRepository = userRepository;
    }

    @PostMapping("/transactions/transfer")
    public ResponseEntity<ApiResponse<Transaction>> transferMoney(
            @Valid @RequestBody CreateTransactionRequest request,
            Authentication authentication
    ) {
        Transaction tx = transactionService.transferMoney(
                authentication.getName(),
                request.getDestinationAccountId(),
                request.getAmount(),
                request.getNote(),
                request.getIdempotencyKey(),
                request.getQuotedExchangeRate(),
                request.getQuotedExchangeRateDate()
        );
        return ResponseEntity.ok(new ApiResponse<>("SUCCESS", "Transfer completed!", tx));
    }

    @GetMapping("/transactions/quote")
    public ResponseEntity<ApiResponse<TransferQuoteResponse>> quoteTransfer(
            @RequestParam @NotBlank @Pattern(regexp = "^UB-\\d{4}$") String destinationAccountId,
            @RequestParam @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
            Authentication authentication
    ) {
        TransferQuoteResponse quote = transactionService.quoteTransfer(
                authentication.getName(), destinationAccountId, amount);
        return ResponseEntity.ok(new ApiResponse<>("SUCCESS", "Reference exchange-rate quote", quote));
    }

    @GetMapping("/accounts/{accountId}/transactions")
    public ResponseEntity<ApiResponse<List<Transaction>>> getHistory(
            @PathVariable String accountId,
            Authentication authentication
    ) {
        userRepository.findByUsername(authentication.getName())
                .filter(user -> user.getRole() == Role.ADMIN || accountId.equals(user.getAccountId()))
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("You cannot view another account's transaction history."));
        List<Transaction> history = transactionService.getTransactionHistory(accountId);
        return ResponseEntity.ok(new ApiResponse<>("SUCCESS", "History found", history));
    }
}