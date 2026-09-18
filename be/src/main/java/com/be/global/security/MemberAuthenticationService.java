package com.be.global.security;

import com.be.member.enums.MemberStatus;
import com.be.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 발급 이후의 퇴사·역할 변경을 반영하기 위해 현재 회원 상태로 인증 구성
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberAuthenticationService {
    private final MemberRepository memberRepository;

    public MemberPrincipal load(Long memberId) {
        var member = memberRepository.findWithRolesById(memberId)
                .orElseThrow(() -> new BadCredentialsException("인증할 수 없습니다."));
        if (member.getStatus() == MemberStatus.RESIGNED) {
            throw new BadCredentialsException("인증할 수 없습니다.");
        }
        return MemberPrincipal.from(member);
    }
}
