package com.bank.transaction;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableTransactionManagement
public class TransactionServiceApplication {

    public static void main(String[] args) {
        // .env values are loaded by config.DotenvEnvironmentPostProcessor
        SpringApplication.run(TransactionServiceApplication.class, args);
    }
}
