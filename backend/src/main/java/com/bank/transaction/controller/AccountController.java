package com.bank.transaction.controller;

import com.bank.transaction.domain.Account;
import com.bank.transaction.domain.Transaction;
import com.bank.transaction.dto.ApiResponse;
import com.bank.transaction.dto.CreateTransactionRequest;
import com.bank.transaction.dto.CreditAccountRequest;
import com.bank.transaction.dto.TransferQuoteResponse;
import com.bank.transaction.service.AccountService;
import com.bank.transaction.repository.UserRepository;
import com.bank.transaction.enums.Role;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import jakarta.validation.Valid;
import java.util.Map;
import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;

@RestController
@RequestMapping("/api/v1/accounts")
@Validated
public class AccountController {

    private final AccountService accountService;
    private final UserRepository userRepository;

    public AccountController(AccountService accountService, UserRepository userRepository) {
        this.accountService = accountService;
        this.userRepository = userRepository;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Account>> createAccount(@RequestBody Map<String, String> request) {
        Account newAccount = accountService.createNewUserAccount(
                request.get("accountId"),
                request.get("username"),
                request.get("email"), // 👈 Extracting email
                request.get("currency")
        );

        return ResponseEntity.ok(new ApiResponse<>(
                "SUCCESS",
                "Account " + newAccount.getAccountId() + " provisioned successfully",
                newAccount
        ));
    }

    @PostMapping("/{accountId}/credit")
    public ResponseEntity<ApiResponse<Transaction>> creditAccount(
            @PathVariable String accountId,
            @Valid @RequestBody CreditAccountRequest request
    ) {
        Transaction transaction = accountService.creditAccount(
                accountId,
                request.getAmount(),
                request.getCurrency(),
                request.getIdempotencyKey(),
                request.getQuotedExchangeRate(),
                request.getQuotedExchangeRateDate()
        );

        return ResponseEntity.ok(new ApiResponse<>("SUCCESS", "Funds dispatched", transaction));
    }

    @GetMapping("/{accountId}/credit-quote")
    public ResponseEntity<ApiResponse<TransferQuoteResponse>> quoteCredit(
            @PathVariable @NotBlank @Pattern(regexp = "^UB-\\d{4}$") String accountId,
            @RequestParam @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount
    ) {
        TransferQuoteResponse quote = accountService.quoteCredit(accountId, amount);
        return ResponseEntity.ok(new ApiResponse<>("SUCCESS", "Vault credit quote", quote));
    }

    @GetMapping("/{accountId}/balance")
    public ResponseEntity<ApiResponse<Account>> getBalance(
            @PathVariable String accountId,
            Authentication authentication
    ) {
        userRepository.findByUsername(authentication.getName())
                .filter(user -> user.getRole() == Role.ADMIN || accountId.equals(user.getAccountId()))
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("You cannot view another account's balance."));
        Account account = accountService.getAccount(accountId);
        return ResponseEntity.ok(new ApiResponse<>("SUCCESS", "Balance retrieved", account));
    }
}