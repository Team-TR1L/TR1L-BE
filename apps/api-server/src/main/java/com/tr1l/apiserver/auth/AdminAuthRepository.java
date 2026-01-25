package com.tr1l.apiserver.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AdminAuthRepository extends JpaRepository<AdminAuthEntity, Long> {
    Optional<AdminAuthEntity> findByEmail(String email);
}
