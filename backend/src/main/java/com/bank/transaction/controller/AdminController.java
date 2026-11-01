package com.bank.transaction.controller;

import com.bank.transaction.dto.ApiResponse;
import com.bank.transaction.domain.Transaction;
import com.bank.transaction.service.TransactionService;
import com.bank.transaction.service.AccountService;
import com.bank.transaction.dto.FundVaultRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final TransactionService transactionService;
    private final AccountService accountService;

    public AdminController(TransactionService transactionService, AccountService accountService) {
        this.transactionService = transactionService;
        this.accountService = accountService;
    }

    @PostMapping("/vault/fund")
    public ResponseEntity<ApiResponse<Transaction>> fundVault(@Valid @RequestBody FundVaultRequest request) {
        Transaction transaction = accountService.fundVault(request.getAmount(), request.getIdempotencyKey());
        return ResponseEntity.ok(new ApiResponse<>("SUCCESS", "System vault funded and opening balance recorded", transaction));
    }

    // 🕵️ THE MONEY TRACKER ENDPOINT
    @GetMapping("/transactions/all")
    public ResponseEntity<ApiResponse<List<Transaction>>> getGlobalFlow() {
        // Failures go to GlobalExceptionHandler, which logs the details and hides them from the client
        List<Transaction> logs = transactionService.getAllTransactionsForAdmin();
        return ResponseEntity.ok(new ApiResponse<>("SUCCESS", "Global logs retrieved", logs));
    }
}