package com.bank.transaction.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitFilterTest {

    private final RateLimitFilter filter = new RateLimitFilter(new RateLimiter(new MutableClock()));

    private MockHttpServletResponse call(String method, String path, String remoteAddr, String forwardedFor) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        request.setRemoteAddr(remoteAddr);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void forgotPasswordIsBlockedWith429AfterFiveRequests() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertEquals(200, call("POST", "/api/v1/auth/forgot-password", "203.0.113.7", null).getStatus());
        }
        MockHttpServletResponse blocked = call("POST", "/api/v1/auth/forgot-password", "203.0.113.7", null);

        assertEquals(429, blocked.getStatus());
        assertNotNull(blocked.getHeader("Retry-After"));
        assertTrue(blocked.getContentAsString().contains("\"status\":\"FAILED\""));
    }

    @Test
    void otherEndpointsAreNotLimited() throws Exception {
        for (int i = 0; i < 50; i++) {
            assertEquals(200, call("GET", "/api/v1/accounts/UB-1234/balance", "203.0.113.7", null).getStatus());
        }
    }

    @Test
    void behindNgrokEachForwardedClientHasItsOwnLimit() throws Exception {
        for (int i = 0; i < 5; i++) {
            call("POST", "/api/v1/auth/forgot-password", "127.0.0.1", "198.51.100.1");
        }
        assertEquals(429, call("POST", "/api/v1/auth/forgot-password", "127.0.0.1", "198.51.100.1").getStatus());
        assertEquals(200, call("POST", "/api/v1/auth/forgot-password", "127.0.0.1", "198.51.100.2").getStatus());
    }

    @Test
    void directClientsCannotDodgeTheLimitWithAFakeForwardedHeader() throws Exception {
        for (int i = 0; i < 5; i++) {
            call("POST", "/api/v1/auth/forgot-password", "203.0.113.9", "10.0.0." + i);
        }
        assertEquals(429, call("POST", "/api/v1/auth/forgot-password", "203.0.113.9", "10.0.0.99").getStatus());
    }

    @Test
    void clientIpUsesTheFirstForwardedAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "198.51.100.5, 10.0.0.1");
        assertEquals("198.51.100.5", RateLimitFilter.clientIp(request));
    }
}
