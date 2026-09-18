package com.be.global.security;

import com.be.member.entity.Member;
import com.be.member.enums.Role;
import java.util.List;
import java.util.Set;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

// SecurityContext에 저장할 회원 정보 - 비밀번호와 JPA Entity는 포함하지 않음
public record MemberPrincipal(Long memberId, String email, Set<Role> roles) {
    public MemberPrincipal {
        roles = Set.copyOf(roles);
    }

    public static MemberPrincipal from(Member member) {
        return new MemberPrincipal(member.getId(), member.getEmail(), member.getRoles());
    }

    public List<SimpleGrantedAuthority> authorities() {
        return roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role.name())).toList();
    }
}
