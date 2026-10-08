package com.sapphire.hawe.global.security;

import com.sapphire.hawe.global.exception.BusinessException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTests {

    @Test
    void publicCharacterListIgnoresExpiredBearerToken() throws Exception {
        JwtTokenProvider provider = mock(JwtTokenProvider.class);
        FilterChain chain = mock(FilterChain.class);
        var filter = new JwtAuthenticationFilter(provider, mock(ObjectMapper.class));
        var request = request("GET", "/api/characters");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        verify(chain).doFilter(request, response);
        verifyNoInteractions(provider);
    }

    @Test
    void protectedCharacterUpdateStillRejectsExpiredBearerToken() throws Exception {
        JwtTokenProvider provider = mock(JwtTokenProvider.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        FilterChain chain = mock(FilterChain.class);
        var filter = new JwtAuthenticationFilter(provider, objectMapper);
        var request = request("PATCH", "/api/me/character");
        var response = new MockHttpServletResponse();
        when(provider.validateAndGetUserId("expired"))
                .thenThrow(new BusinessException(TokenErrorCode.TOKEN_EXPIRED));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        verifyNoInteractions(chain);
    }

    private MockHttpServletRequest request(String method, String path) {
        var request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        request.addHeader("Authorization", "Bearer expired");
        return request;
    }
}
