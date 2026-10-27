package com.bank.transaction.security;

import com.bank.transaction.domain.User;
import com.bank.transaction.repository.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Set<String> PUBLIC_AUTH_PATHS = Set.of(
            "/api/v1/auth/login", "/api/v1/auth/register", "/api/v1/auth/verify",
            "/api/v1/auth/forgot-password", "/api/v1/auth/reset-password");

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    public JwtAuthFilter(JwtUtil jwtUtil, UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getServletPath();

        // 1. Skip filter for public authentication endpoints (change-password still needs the JWT)
        if (PUBLIC_AUTH_PATHS.contains(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");

        // 2. Check for Bearer token
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = jwtUtil.parseClaims(token);

                String username = claims.getSubject();
                User user = userRepository.findByUsername(username)
                        .orElseThrow(() -> new IllegalStateException("Token user no longer exists"));

                // Tokens issued before the last password change are revoked (JWT iat has second precision)
                if (user.getPasswordChangedAt() != null && claims.getIssuedAt() != null
                        && claims.getIssuedAt().toInstant()
                            .isBefore(user.getPasswordChangedAt().truncatedTo(ChronoUnit.SECONDS))) {
                    throw new IllegalStateException("Token issued before the last password change");
                }
                // Trust the role stored in the database rather than the one copied into the token
                String role = user.getRole().name();

                if (SecurityContextHolder.getContext().getAuthentication() == null) {
                    // Spring Security expects roles to be prefixed with "ROLE_" for hasRole() checks
                    List<SimpleGrantedAuthority> authorities = Collections.singletonList(
                            new SimpleGrantedAuthority("ROLE_" + role)
                    );

                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            username, null, authorities);

                    // Set the security context
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            } catch (Exception e) {
                // If token is invalid, clear context and return 401
                SecurityContextHolder.clearContext();
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}