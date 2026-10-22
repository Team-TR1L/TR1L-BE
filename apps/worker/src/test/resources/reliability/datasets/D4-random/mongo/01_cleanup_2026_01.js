// 2026 01 snapshot 정리
// baseline 재생성 전 비우기 목적
db.getSiblingDB("tr1l").billing_snapshot.deleteMany({
  billingMonth: "2026-01-01"
})
