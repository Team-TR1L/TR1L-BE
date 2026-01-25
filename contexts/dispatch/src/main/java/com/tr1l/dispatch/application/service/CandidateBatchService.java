package com.tr1l.dispatch.application.service;

import com.tr1l.dispatch.domain.model.vo.BatchResult;
import com.tr1l.dispatch.application.command.DispatchCommand;
import com.tr1l.dispatch.domain.model.aggregate.DispatchPolicy;
import com.tr1l.dispatch.domain.model.enums.ChannelType;
import com.tr1l.dispatch.infra.persistence.entity.BillingTargetEntity;
import com.tr1l.dispatch.infra.persistence.repository.MessageCandidateJpaRepository;
import com.tr1l.dispatch.infra.s3.S3LocationMapper;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CandidateBatchService {

    private final MessageCandidateJpaRepository candidateRepository;
    private final EntityManager entityManager;
    private final S3LocationMapper s3LocationMapper;

    @Transactional
    public BatchResult loadAndPrepareBatch(
            DispatchPolicy policy,
            String dayTime,
            int currentHour,
            Long lastUserId,
            int pageSize
    ) {

        List<BillingTargetEntity> candidates =
                candidateRepository.findReadyCandidatesByUserCursorNative(
                        lastUserId,
                        dayTime,
                        policy.getRoutingPolicy().getPrimaryOrder().channels().size() - 1,
                        currentHour,
                        pageSize
                );

        if (candidates.isEmpty()) {
            return BatchResult.empty(lastUserId);
        }

        List<DispatchCommand> commands = new ArrayList<>();

        for (BillingTargetEntity candidate : candidates) {

            ChannelType nowChannel = policy.getRoutingPolicy()
                    .getPrimaryOrder()
                    .channels()
                    .get(Math.min(
                            policy.getRoutingPolicy().getPrimaryOrder().channels().size() - 1,
                            candidate.getAttemptCount()
                    ));

            String s3url =
                    s3LocationMapper.extractLocationValueByChannel(
                            candidate.getS3UrlJsonb(), nowChannel);

            String destination =
                    s3LocationMapper.extractValueByChannel(
                            candidate.getSendOptionJsonb(), nowChannel);

            commands.add(new DispatchCommand(
                    candidate.getId().getUserId(),
                    candidate.getId().getBillingMonth(),
                    nowChannel,
                    s3url,
                    destination
            ));

            // Cursor 이동
            lastUserId = candidate.getId().getUserId();
        }

        // 7. 영속성 컨텍스트 정리 (OOM 방지)
        entityManager.clear();

        return new BatchResult(commands, lastUserId);
    }
}