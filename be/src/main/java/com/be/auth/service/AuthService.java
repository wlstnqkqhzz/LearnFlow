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
