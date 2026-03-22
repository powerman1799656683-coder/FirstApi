package com.firstapi.backend.config;

import tools.jackson.databind.ObjectMapper;
import com.firstapi.backend.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthFilterPaymentPathTest {

    @Test
    @DisplayName("支付回调路径不应被视为公开接口")
    void paymentNotifyPathShouldRequireAuth() throws Exception {
        AuthService authService = mock(AuthService.class);
        when(authService.resolveAuthenticatedUser(any())).thenReturn(null);

        AuthFilter filter = new AuthFilter(authService, new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/payment/notify/alipay");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
    }
}
