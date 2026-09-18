package com.be.global.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;

// Authorization: Bearer 토큰을 검증하고 현재 Member 정보로 인증
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenProvider tokens;
    private final MemberAuthenticationService members;
    private final JsonAuthenticationEntryPoint entryPoint;

    public JwtAuthenticationFilter(JwtTokenProvider tokens, MemberAuthenticationService members,
                                   JsonAuthenticationEntryPoint entryPoint) {
        this.tokens = tokens;
        this.members = members;
        this.entryPoint = entryPoint;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 만료된 Access Token이 있어도 로그인 및 본문의 Refresh Token 검증 가능
        return "POST".equals(request.getMethod())
                && ((request.getContextPath() + "/api/auth/login").equals(request.getRequestURI())
                || (request.getContextPath() + "/api/auth/refresh").equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null) {
            try {
                var values = request.getHeaders(HttpHeaders.AUTHORIZATION);
                values.nextElement();
                if (values.hasMoreElements()) {
                    throw new BadCredentialsException("중복 인증 헤더입니다.");
                }
                if (!header.regionMatches(true, 0, "Bearer ", 0, 7) || header.substring(7).isBlank()) {
                    throw new BadCredentialsException("잘못된 인증 헤더입니다.");
                }
                MemberPrincipal claims = tokens.parseAccessToken(header.substring(7));
                MemberPrincipal principal = members.load(claims.memberId());
                var authentication = UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, principal.authorities());
                var context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authentication);
                SecurityContextHolder.setContext(context);
            } catch (JwtException | AuthenticationException | IllegalArgumentException exception) {
                SecurityContextHolder.clearContext();
                entryPoint.commence(request, response, new BadCredentialsException("인증할 수 없습니다."));
                return;
            }
        }
        // 업무 예외를 인증 오류로 바꾸지 않도록 체인 실행은 위 catch 범위에서 제외
        chain.doFilter(request, response);
    }
}
