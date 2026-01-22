package com.tr1l.apiserver.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private final AdminAuthRepository adminAuthRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public AdminAuthEntity attemptLogin(String email, String rawPassword) {
        AdminAuthEntity auth = adminAuthRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("인증 정보가 일치하지 않습니다."));

        if (!passwordEncoder.matches(rawPassword, auth.getPassword())) {
            throw new BadCredentialsException("인증 정보가 일치하지 않습니다.");
        }

        return auth;
    }

    public AdminAuthEntity register(String email, String rawPassword) {
        AdminAuthEntity auth = new AdminAuthEntity();
        auth.setEmail(email);
        auth.setPassword(passwordEncoder.encode(rawPassword));
        return adminAuthRepository.save(auth);
    }
}