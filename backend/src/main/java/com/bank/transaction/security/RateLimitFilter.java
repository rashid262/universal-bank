package com.bank.transaction.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

/**
 * Limits how often one client IP can call the public authentication endpoints.
 * Over the limit, the request is answered with HTTP 429 and a Retry-After header.
 *
 * Not a Spring bean on purpose: SecurityConfig adds it to the security filter chain (after CORS,
 * so browsers can read the 429), and it must not also be registered as a global servlet filter.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    record Rule(int limit, Duration window) { }

    // Generous enough for the nightly test suite, which signs in many times from one CI runner IP
    static final Map<String, Rule> RULES = Map.of(
            "/api/v1/auth/login", new Rule(30, Duration.ofMinutes(1)),
            // These three send an email, so they are limited per hour
            "/api/v1/auth/register", new Rule(10, Duration.ofHours(1)),
            "/api/v1/auth/forgot-password", new Rule(5, Duration.ofHours(1)),
            "/api/v1/auth/resend-verification", new Rule(5, Duration.ofHours(1)),
            "/api/v1/auth/reset-password", new Rule(10, Duration.ofHours(1)));

    private final RateLimiter rateLimiter;

    public RateLimitFilter(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Rule rule = "POST".equalsIgnoreCase(request.getMethod()) ? RULES.get(request.getServletPath()) : null;
        if (rule != null) {
            String key = request.getServletPath() + "|" + clientIp(request);
            long retryAfter = rateLimiter.tryAcquire(key, rule.limit(), rule.window());
            if (retryAfter > 0) {
                response.setStatus(429);
                response.setHeader("Retry-After", String.valueOf(retryAfter));
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write("{\"status\":\"FAILED\",\"message\":\"Too many requests. Try again in "
                        + describe(retryAfter) + ".\",\"data\":null}");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * The caller's IP. Behind ngrok every request reaches the app from the local ngrok agent
     * (127.0.0.1), so the real address is taken from X-Forwarded-For, but only for requests that
     * arrived through a local proxy. A client connecting directly cannot spoof its IP with that header.
     */
    static String clientIp(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        boolean viaLocalProxy = "127.0.0.1".equals(remote) || "0:0:0:0:0:0:0:1".equals(remote) || "::1".equals(remote);
        String forwarded = request.getHeader("X-Forwarded-For");
        if (viaLocalProxy && forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return remote;
    }

    static String describe(long seconds) {
        if (seconds < 60) {
            return seconds + (seconds == 1 ? " second" : " seconds");
        }
        long minutes = (seconds + 59) / 60;
        return minutes + (minutes == 1 ? " minute" : " minutes");
    }
}
