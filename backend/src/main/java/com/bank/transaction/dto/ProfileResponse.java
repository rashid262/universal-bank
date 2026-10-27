package com.bank.transaction.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

// What a signed-in user may see about their own account; never includes hashes or tokens.
@Data
@AllArgsConstructor
public class ProfileResponse {
    private String username;
    private String email;
    private String accountId;
    private String role;
    private boolean verified;
}
