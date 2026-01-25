package com.tr1l.apiserver.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminUserDetailsService implements UserDetailsService {

    private final AdminAuthRepository adminAuthRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        AdminAuthEntity auth = adminAuthRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("관리자 정보를 찾을 수 없습니다."));

        return User.builder()
                .username(auth.getEmail())
                .password(auth.getPassword()) // DB BCrypt 해시
                .roles("ADMIN")              // 최소 하나의 권한 필요
                .build();
    }
}
