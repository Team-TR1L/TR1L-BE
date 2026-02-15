---
title: ADR 목차(Index)
parent: 당위성/의사결정(Decisions)
nav_order: 1
---

# ADR Index
> TR1L에서의 기술/설계 선택을 **"왜(당위성)"**+"**트레이드 오프(trade-off)**"+"**증빙(evidence)**"로 기록한다."

# ADR 작성 규칙
> 아래 항목을 필수로 포함한다.
> 1. **Context** - 상황/배경 (언제/어디서/규모)
> 2. **Problem** - 문제 (무엇?)
> 3. **Options** - 대안(선택지 나열과 비교 / 간단하게만 설명하고 깊은 반론과 이유에 대해서는 **rejected-alternative** 문서에 정리 후 링크)
> 4. **Decision** - 최종 선택한 옵션
> 5. **Consequences** - 결과/영향
> 6. **Evidence** - 증빙/측정/지표

# ADRs
- **ADR-0001** [Flatten User Table JOIN 과부하 제거](adr-0001-flattening-join-explosion.md)
- **ADR-0002** [KeySet 페이지네이션 도입](adr-0002-keyset-pagination.md)
- **ADR-0003** [Snapshot용 MongoDB 도입](adr-0003-snapshot-store-mongo-vs-rds.md)
- **ADR-0004** [RDB 선정 - PostgreSQL VS MySQL](adr-0004-postgresql.md)
- **ADR-0005** [S3 선정 - S3 vs PostgresSQL vs IPFS](adr-0005-s3.md)
- **ADR-0006** [메시지 후보군 조회 쿼리 - Skip Locked 적용](adr-0006-cursor-skip-locked.md)