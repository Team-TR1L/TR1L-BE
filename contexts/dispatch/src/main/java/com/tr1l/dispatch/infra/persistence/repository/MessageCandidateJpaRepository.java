package com.tr1l.dispatch.infra.persistence.repository;

import com.tr1l.dispatch.infra.persistence.entity.MessageCandidateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface MessageCandidateJpaRepository extends JpaRepository<MessageCandidateEntity, Long> {
    @Query("""
        select c
        from MessageCandidateEntity c
        where c.status = 'READY'
          and c.availableTime <= :now
    """)
    List<MessageCandidateEntity> findReadyCandidates(@Param("now") Instant now);
}
