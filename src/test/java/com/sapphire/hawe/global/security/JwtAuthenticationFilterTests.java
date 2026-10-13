package com.sapphire.hawe.global.security;

import com.sapphire.hawe.auth.dao.UserMapper;
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
import static org.mockito.Mockito.never;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtAuthenticationFilterTests {

    @Test
    void publicCharacterListIgnoresExpiredBearerToken() throws Exception {
        JwtTokenProvider provider = mock(JwtTokenProvider.class);
        UserMapper users = mock(UserMapper.class);
        FilterChain chain = mock(FilterChain.class);
        var filter = new JwtAuthenticationFilter(provider, mock(ObjectMapper.class), users);
        var request = request("GET", "/api/characters");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        verify(chain).doFilter(request, response);
        verifyNoInteractions(provider);
        verifyNoInteractions(users);
    }

    @Test
    void protectedCharacterUpdateStillRejectsExpiredBearerToken() throws Exception {
        JwtTokenProvider provider = mock(JwtTokenProvider.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        FilterChain chain = mock(FilterChain.class);
        var filter = new JwtAuthenticationFilter(provider, objectMapper, mock(UserMapper.class));
        var request = request("PATCH", "/api/me/character");
        var response = new MockHttpServletResponse();
        when(provider.parseAccessToken("expired"))
                .thenThrow(new BusinessException(TokenErrorCode.TOKEN_EXPIRED));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        verifyNoInteractions(chain);
    }

    @Test
    void deletedUserTokenIsRejectedBeforeChain() throws Exception {
        SecurityContextHolder.clearContext();
        JwtTokenProvider provider = mock(JwtTokenProvider.class);
        UserMapper users = mock(UserMapper.class);
        FilterChain chain = mock(FilterChain.class);
        var filter = new JwtAuthenticationFilter(provider, new ObjectMapper(), users);
        var request = request("GET", "/api/me");
        var response = new MockHttpServletResponse();
        when(provider.parseAccessToken("expired")).thenReturn(new TokenClaims(9L, 0));
        when(users.findTokenVersion(9L)).thenReturn(null);

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertEquals("INVALID_TOKEN", new ObjectMapper().readTree(response.getContentAsString()).get("code").asText());
        assertFalse(SecurityContextHolder.getContext().getAuthentication() != null);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void matchingVersionAuthenticatesAndMismatchIsRejected() throws Exception {
        SecurityContextHolder.clearContext();
        JwtTokenProvider provider = mock(JwtTokenProvider.class);
        UserMapper users = mock(UserMapper.class);
        FilterChain chain = mock(FilterChain.class);
        var filter = new JwtAuthenticationFilter(provider, new ObjectMapper(), users);
        var request = request("GET", "/api/me");
        var response = new MockHttpServletResponse();
        when(provider.parseAccessToken("expired")).thenReturn(new TokenClaims(9L, 2));
        when(users.findTokenVersion(9L)).thenReturn(2, 3);

        filter.doFilter(request, response, chain);
        assertEquals(9L, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        verify(chain).doFilter(request, response);
        SecurityContextHolder.clearContext();
        var secondRequest = request("GET", "/api/me");
        var secondResponse = new MockHttpServletResponse();
        filter.doFilter(secondRequest, secondResponse, chain);
        assertEquals(401, secondResponse.getStatus());
        assertEquals("INVALID_TOKEN", new ObjectMapper().readTree(secondResponse.getContentAsString()).get("code").asText());
        verify(chain, never()).doFilter(secondRequest, secondResponse);
    }

    private MockHttpServletRequest request(String method, String path) {
        var request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        request.addHeader("Authorization", "Bearer expired");
        return request;
    }
}
