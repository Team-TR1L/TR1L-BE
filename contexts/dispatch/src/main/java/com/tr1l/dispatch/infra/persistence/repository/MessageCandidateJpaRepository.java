package com.tr1l.dispatch.infra.persistence.repository;

import com.tr1l.dispatch.infra.persistence.entity.MessageCandidateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

@Deprecated
public interface MessageCandidateJpaRepository extends JpaRepository<MessageCandidateEntity, Long> {
    @Query("""
        select c
        from MessageCandidateEntity c
        where c.status = 'READY'
          and c.availableTime <= :now
    """)
    List<MessageCandidateEntity> findReadyCandidates(Instant now);
}
