package com.bank.transaction.controller;


import com.bank.transaction.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/api/v1/health")
    public ApiResponse<String> health() {
        return new ApiResponse<>(
                "SUCCESS",
                "Service is healthy",
                "UP"
        );
    }

}
