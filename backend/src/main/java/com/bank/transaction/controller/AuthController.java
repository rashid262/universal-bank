package com.bank.transaction.controller;

import com.bank.transaction.dto.ApiResponse;
import com.bank.transaction.dto.AuthRequest;
import com.bank.transaction.dto.AuthResponse;
import com.bank.transaction.domain.User;
import com.bank.transaction.dto.ChangePasswordRequest;
import com.bank.transaction.dto.ForgotPasswordRequest;
import com.bank.transaction.dto.ProfileResponse;
import com.bank.transaction.dto.RegisterRequest;
import com.bank.transaction.dto.ResendVerificationRequest;
import com.bank.transaction.dto.ResetPasswordRequest;
import jakarta.validation.Valid;
import com.bank.transaction.service.AuthService;
import com.bank.transaction.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final String frontendUrl;

    public AuthController(AuthService authService, UserRepository userRepository,
                          @Value("${app.frontend-url}") String frontendUrl) {
        this.authService = authService;
        this.userRepository = userRepository;
        this.frontendUrl = frontendUrl;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@RequestBody AuthRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(new ApiResponse<>("SUCCESS", "Login successful", response));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Boolean>> register(@Valid @RequestBody RegisterRequest request) {
        // data = whether the verification email went out
        boolean emailSent = authService.register(request);
        String message = emailSent
                ? "Registration successful! Check your email to verify."
                : "Account created, but the verification email could not be sent. Request a new link below.";
        return ResponseEntity.ok(new ApiResponse<>("SUCCESS", message, emailSent));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<ApiResponse<String>> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerification(request.getEmail());
        // Identical response whether or not the email exists or is already verified
        return ResponseEntity.ok(new ApiResponse<>("SUCCESS",
                "If that email belongs to an account awaiting verification, a new link is on its way.", null));
    }

    // New Endpoint for Email Verification
    @GetMapping("/verify")
    public ResponseEntity<Void> verifyAccount(@RequestParam String token) {
        User user = userRepository.findByVerificationToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid token"));

        user.setVerified(true);
        user.setVerificationToken(null);
        userRepository.save(user);

        // Redirect to the React frontend success page
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(frontendUrl + "/verify-success"))
                .build();
    }

    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<String>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication
    ) {
        // The account comes from the JWT, never from the request body.
        // Other sessions are signed out; the returned token keeps this browser signed in.
        String freshToken = authService.changePassword(
                authentication.getName(), request.getOldPassword(), request.getNewPassword());
        return ResponseEntity.ok(new ApiResponse<>("SUCCESS",
                "Password updated. Other signed-in devices have been signed out.", freshToken));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<ProfileResponse>> me(Authentication authentication) {
        // Always the caller's own record, identified by the JWT
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        ProfileResponse profile = new ProfileResponse(
                user.getUsername(), user.getEmail(), user.getAccountId(), user.getRole().name(), user.isVerified());
        return ResponseEntity.ok(new ApiResponse<>("SUCCESS", "Profile retrieved", profile));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.requestPasswordReset(request.getEmail());
        // Identical response whether or not the email exists
        return ResponseEntity.ok(new ApiResponse<>("SUCCESS",
                "If that email belongs to a verified customer account, a reset link is on its way.", null));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(new ApiResponse<>("SUCCESS", "Password reset. You can now sign in.", null));
    }
}
