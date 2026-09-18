package com.be.auth.service;

import com.be.auth.dto.*;
import com.be.global.security.*;
import com.be.member.enums.MemberStatus;
import com.be.member.repository.MemberRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

// 기존 PasswordEncoder로 비밀번호를 검증 -> ACTIVE·ON_LEAVE 회원에게 Access Token과 Refresh Token을 발급하는 인증 서비스
@Service
@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final MemberAuthenticationService memberAuthenticationService;

    // 현재 DB 회원 정보로 재발급하고, 사용된 Refresh Token은 원자적으로 교체
    public LoginResponse refresh(@NotNull @Valid RefreshTokenRequest request) {
        Long memberId;
        try {
            memberId = tokenProvider.parseRefreshToken(request.refreshToken());
        } catch (org.springframework.security.oauth2.jwt.JwtException exception) {
            throw new BadCredentialsException("인증할 수 없습니다.");
        }
        if (!refreshTokenService.matches(memberId, request.refreshToken())) {
            throw new BadCredentialsException("인증할 수 없습니다.");
        }
        // 기존 인증 서비스를 재사용하여 최신 이메일·역할 조회 및 퇴사 여부 검증
        MemberPrincipal principal = memberAuthenticationService.load(memberId);
        String accessToken = tokenProvider.createAccessToken(principal);
        String refreshToken = tokenProvider.createRefreshToken(memberId);
        // 조회 후 다른 요청이 회전·로그아웃한 경우 토큰을 덮어쓰지 않음
        if (!refreshTokenService.rotate(memberId, request.refreshToken(), refreshToken,
                tokenProvider.refreshTokenExpiresInSeconds())) {
            throw new BadCredentialsException("인증할 수 없습니다.");
        }
        return new LoginResponse(accessToken, refreshToken, "Bearer",
                tokenProvider.accessTokenExpiresInSeconds(), tokenProvider.refreshTokenExpiresInSeconds());
    }

    // Access Token은 만료까지 유지되며, Refresh Token만 제거하여 재발급 차단
    public void logout(Long memberId) {
        refreshTokenService.delete(memberId);
    }

    public LoginResponse login(@NotNull @Valid LoginRequest request) {
        var member = memberRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("로그인할 수 없습니다."));
        boolean matches = passwordEncoder.matches(request.password(), member.getPasswordHash());

        if (!matches || member.getStatus() == MemberStatus.RESIGNED) {
            // 계정 존재 여부와 퇴사 여부를 구분해서 노출하지 않음
            throw new BadCredentialsException("로그인할 수 없습니다.");
        }

        MemberPrincipal principal = MemberPrincipal.from(member);

        // Access Token 발급
        String accessToken = tokenProvider.createAccessToken(principal);

        // Refresh Token 발급
        String refreshToken = tokenProvider.createRefreshToken(member.getId());

        // Refresh Token을 Redis에 만료 시간과 함께 저장
        refreshTokenService.save(member.getId(), refreshToken, tokenProvider.refreshTokenExpiresInSeconds());

        return new LoginResponse(accessToken, refreshToken, "Bearer",
                tokenProvider.accessTokenExpiresInSeconds(), tokenProvider.refreshTokenExpiresInSeconds());
    }
}
