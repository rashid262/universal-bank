package com.bank.transaction.config;

import com.bank.transaction.security.JwtAuthFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.List;

@Configuration
@EnableWebSecurity
@Order(1) // Tells Spring: "Use THIS config first, ignore defaults"
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final List<String> allowedOrigins;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter,
                          @Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(request -> {
                    var corsConfig = new org.springframework.web.cors.CorsConfiguration();
                    // Set CORS_ALLOWED_ORIGINS (comma-separated) to add a deployed frontend such as Vercel
                    corsConfig.setAllowedOrigins(allowedOrigins);
                    corsConfig.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
                    corsConfig.setAllowedHeaders(java.util.List.of("*"));
                    // MUST SET THIS TO TRUE for authentication headers to work
                    corsConfig.setAllowCredentials(true);
                    return corsConfig;
                }))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/register",
                        "/api/v1/auth/forgot-password", "/api/v1/auth/reset-password").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/auth/verify", "/api/v1/health").permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/accounts").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/accounts/*/credit").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/accounts/*/credit-quote").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/transactions/transfer").hasRole("CUSTOMER")
                .requestMatchers(HttpMethod.GET, "/api/v1/transactions/quote").hasRole("CUSTOMER")
                .requestMatchers(HttpMethod.GET, "/api/v1/accounts/*/balance").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/accounts/*/transactions").authenticated()
                .anyRequest().authenticated())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}