# /// script
# requires-python = ">=3.11"
# dependencies = ["psycopg[binary]>=3.2"]
# ///
"""계산 단위 테스트(BillingAmountReferenceTest)가 쓰는 입력과 기대 금액을 만든다.

기대 금액은 C6 참조 계산 SQL(checks/reference/job1-billing-amount.sql)로 낸다. 운영 계산 코드를 쓰지 않는다.
입력은 두 가지다.
  1. 지금 적재된 billing_targets(2026-01)에서 요금 계산에 영향을 주는 값 조합마다 한 명
  2. 경계값을 일부러 만든 행. 기본 제공량 바로 앞뒤 사용량, 복지 할인 상한에 정확히 닿는 경우와 1원 넘는 경우

같은 이름의 임시 테이블에 입력을 넣고 참조 SQL 을 그대로 돌린다. 이름과 연락처는 테스트용 값으로 바꾼다.

uv run infra/reliability/scripts/make_reference_cases.py
"""

import json
import math
import os
from decimal import Decimal
from pathlib import Path

import psycopg
from psycopg.types.json import Jsonb
from psycopg.rows import dict_row

ROOT = Path(__file__).resolve().parents[3]
TARGET_PG = os.getenv("REL_TARGET_PG", "postgresql://postgres:postgres@localhost:25433/billing_target")
REFERENCE_SQL = ROOT / "apps/worker/src/test/resources/reliability/checks/reference/job1-billing-amount.sql"
OUTPUT = ROOT / "contexts/billing/src/test/resources/reference/billing-amount-cases.json"
BILLING_MONTH = "2026-01-01"
SYNTHETIC_USER_ID = 9_000_000

# 요금 계산에 영향을 주는 값 조합마다 한 명
SAMPLE_SQL = """
SELECT DISTINCT ON (plan_monthly_price, data_billing_type_code, sign(used_data_mb - coalesce(included_data_mb, 0)),
                    has_contract, soldier_eligible, welfare_code, least(jsonb_array_length(coalesce(options_jsonb, '[]')), 4))
       *
FROM public.billing_targets
WHERE billing_month = %s
ORDER BY plan_monthly_price, data_billing_type_code, sign(used_data_mb - coalesce(included_data_mb, 0)),
         has_contract, soldier_eligible, welfare_code, least(jsonb_array_length(coalesce(options_jsonb, '[]')), 4), user_id
"""


def floor_mul(a, rate):
    return math.floor(Decimal(a) * Decimal(rate))


def synthetic_rows(sample):
    """기본 제공량 경계와 복지 할인 상한 경계 행을 만든다"""
    rows, next_id = [], SYNTHETIC_USER_ID
    dbt02 = next(r for r in sample if r["data_billing_type_code"] == "DBT-02")
    for used in (0, dbt02["included_data_mb"] - 1, dbt02["included_data_mb"], dbt02["included_data_mb"] + 1,
                 dbt02["included_data_mb"] + 3, dbt02["included_data_mb"] + 100_000):
        rows.append(dict(dbt02, user_id=next_id, used_data_mb=used))
        next_id += 1

    # 복지 할인 = floor((plan - 약정 - 군인 + 초과 요금) * rate) 가 상한과 같아지는 사용량과 1원 넘는 사용량을 찾는다
    capped = next(r for r in sample if r["data_billing_type_code"] == "DBT-02" and r["welfare_eligible"]
                  and (r["welfare_cap_amount"] or 0) > 0)
    plan = capped["plan_monthly_price"]
    contract = floor_mul(plan, capped["contract_rate"]) if capped["has_contract"] else 0
    soldier = floor_mul(max(plan - contract, 0), "0.20") if capped["soldier_eligible"] else 0
    cap = capped["welfare_cap_amount"]
    found = {}
    for excess in range(0, 2_000_000):
        raw = floor_mul(max(plan - contract - soldier, 0) + floor_mul(excess, capped["excess_charge_per_mb"]),
                        capped["welfare_rate"])
        if raw in (cap - 1, cap, cap + 1) and raw not in found:
            found[raw] = excess
        if len(found) == 3:
            break
    for raw, excess in sorted(found.items()):
        rows.append(dict(capped, user_id=next_id, used_data_mb=capped["included_data_mb"] + excess))
        next_id += 1
    return rows


def as_case(row, expected):
    return {
        "row": {
            "billingMonth": row["billing_month"].strftime("%Y-%m"),  # 운영 조회 SQL 처럼 YYYY-MM
            "userName": f"테스트{row['user_id']}",
            "userId": row["user_id"],
            "userBirthDate": "1990-01-01",
            "recipientEmailEnc": f"user{row['user_id']}@example.com",
            "recipientPhoneEnc": "010-0000-0000",
            "planName": row["plan_name"],
            "planMonthlyPrice": row["plan_monthly_price"],
            "networkTypeName": row["network_type_name"],
            "dataBillingTypeCode": row["data_billing_type_code"],
            "dataBillingTypeName": row["data_billing_type_name"],
            "includedDataMb": row["included_data_mb"],
            "excessChargePerMb": str(row["excess_charge_per_mb"]) if row["excess_charge_per_mb"] is not None else None,
            "usedDataMb": row["used_data_mb"],
            "hasContract": row["has_contract"],
            "contractRate": float(row["contract_rate"]),
            "contractDurationMonths": row["contract_duration_months"],
            "soldierEligible": row["soldier_eligible"],
            "welfareEligible": row["welfare_eligible"],
            "welfareCode": row["welfare_code"],
            "welfareName": row["welfare_name"],
            "welfareRate": float(row["welfare_rate"]),
            "welfareCapAmount": row["welfare_cap_amount"],
            "optionsJsonb": json.dumps(row["options_jsonb"], ensure_ascii=False) if row["options_jsonb"] is not None else None,
        },
        "expected": {key: int(expected[key]) for key in (
            "subtotal", "discount_total", "total", "contract_discount", "soldier_discount", "welfare_discount")},
    }


def main():
    reference = REFERENCE_SQL.read_text().replace(":billingMonthDay", "%(month)s")
    with psycopg.connect(TARGET_PG, row_factory=dict_row) as conn:
        sample = conn.execute(SAMPLE_SQL, (BILLING_MONTH,)).fetchall()
        if not sample:
            raise SystemExit("billing_targets 2026-01 이 비어 있다. S-001 을 먼저 돌린다")
        rows = sample + synthetic_rows(sample)
        # 참조 SQL 이 보는 billing_targets 를 임시 테이블로 가린다
        conn.execute("CREATE TEMP TABLE billing_targets (LIKE public.billing_targets INCLUDING DEFAULTS)")
        columns = list(rows[0].keys())
        with conn.cursor() as cursor:
            cursor.executemany(
                f"INSERT INTO pg_temp.billing_targets ({', '.join(columns)}) VALUES ({', '.join(['%s'] * len(columns))})",
                [[Jsonb(r[c]) if isinstance(r[c], (dict, list)) else r[c] for c in columns]
                 for r in rows])
        expected = {r["user_id"]: r for r in conn.execute(reference, {"month": BILLING_MONTH}).fetchall()}
        conn.rollback()

    cases = [as_case(row, expected[row["user_id"]]) for row in rows]
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps({
        "source": "checks/reference/job1-billing-amount.sql",
        "sampled": len(sample),
        "synthetic": len(rows) - len(sample),
        "cases": cases,
    }, ensure_ascii=False, indent=2) + "\n")
    print(f"{OUTPUT} 표본 {len(sample)}건, 경계값 {len(rows) - len(sample)}건")


if __name__ == "__main__":
    main()
