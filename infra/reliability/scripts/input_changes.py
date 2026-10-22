"""부분 실패와 재실행 사이에 main DB 원천을 바꾸고, 끝나면 되돌린다 (TR1L-172, S-014R).

바뀐 사용자는 장애 직후 상태에 따라 세 무리로 나눠 고른다.
  CALCULATED  이미 계산이 끝난 사용자 (billing_work CALCULATED)
  LOADED      billing_targets 에는 있지만 아직 계산하지 않은 사용자
  NOT_LOADED  청구 대상인데 Step1 이 아직 billing_targets 에 넣지 않은 사용자
무리마다 바뀌어야 의미가 있는 사용자 중 user_id 가 작은 순으로 GROUP_SIZE 명을 고른다.

바꾸는 것
  I1  cutoff 뒤 사용량 재집계. 2026-01 사용량에 5,000MB 를 더하고 usage_aggregated_at 을 지금으로 바꾼다 (초과 요금이 있는 DBT-02 사용자)
  I2  탈퇴. user_status 를 WITHDRAWN 으로 바꾼다
  I3  옵션 해지. 구독의 end_date 를 지금으로 바꾼다 (옵션이 있는 사용자)
"""

import psycopg

GROUP_SIZE = 30
GROUPS = ("CALCULATED", "LOADED", "NOT_LOADED")

CHANGES = {
    "I1": dict(
        title="cutoff 뒤 사용량 재집계",
        candidates="""SELECT u.user_id FROM users u JOIN plan p ON p.plan_code = u.plan_code
                      JOIN monthly_data_usage m ON m.user_id = u.user_id AND m.usage_year_month = '2026-01'
                      WHERE p.data_billing_type_code = 'DBT-02'""",
        backup="""SELECT user_id, used_data_mb, usage_aggregated_at FROM monthly_data_usage
                  WHERE usage_year_month = '2026-01' AND user_id = ANY(%s)""",
        apply="""UPDATE monthly_data_usage SET used_data_mb = used_data_mb + 5000, usage_aggregated_at = now()
                 WHERE usage_year_month = '2026-01' AND user_id = ANY(%s)""",
        restore="""UPDATE monthly_data_usage SET used_data_mb = %s, usage_aggregated_at = %s
                   WHERE usage_year_month = '2026-01' AND user_id = %s""",
    ),
    "I2": dict(
        title="탈퇴",
        candidates="SELECT user_id FROM users",
        backup="SELECT user_id, user_status, modified_at FROM users WHERE user_id = ANY(%s)",
        apply="UPDATE users SET user_status = 'WITHDRAWN', modified_at = now() WHERE user_id = ANY(%s)",
        restore="UPDATE users SET user_status = %s, modified_at = %s WHERE user_id = %s",
    ),
    "I3": dict(
        title="옵션 해지",
        candidates="SELECT DISTINCT user_id FROM user_option_subscription",
        backup="SELECT user_option_id, end_date FROM user_option_subscription WHERE user_id = ANY(%s)",
        apply="UPDATE user_option_subscription SET end_date = now() WHERE user_id = ANY(%s)",
        restore="UPDATE user_option_subscription SET end_date = %s WHERE user_option_id = %s",
    ),
}


def pick_users(change, main, target, billing_month_day):
    """장애 직후 상태에서 무리마다 바꿀 사용자를 고른다"""
    candidates = {row[0] for row in main.execute(CHANGES[change]["candidates"]).fetchall()}
    billable = {row[0] for row in main.execute(
        """SELECT user_id FROM users
           WHERE user_role = 'USER' AND user_status = 'ACTIVE' AND plan_code IS NOT NULL""").fetchall()}
    loaded = {row[0] for row in target.execute(
        "SELECT user_id FROM billing_targets WHERE billing_month = %s", (billing_month_day,)).fetchall()}
    calculated = {row[0] for row in target.execute(
        "SELECT user_id FROM billing_work WHERE billing_month_day = %s AND status = 'CALCULATED'",
        (billing_month_day,)).fetchall()}
    pools = {
        "CALCULATED": calculated,
        "LOADED": loaded - calculated,
        "NOT_LOADED": billable - loaded,
    }
    return {group: sorted(pools[group] & candidates & billable)[:GROUP_SIZE] for group in GROUPS}


def apply(change, main, user_ids):
    """원천을 바꾸고 되돌릴 값을 돌려준다"""
    spec = CHANGES[change]
    backup = main.execute(spec["backup"], (user_ids,)).fetchall()
    main.execute(spec["apply"], (user_ids,))
    return backup


def restore(change, main_url, backup):
    with psycopg.connect(main_url, autocommit=True) as main:
        spec = CHANGES[change]
        with main.cursor() as cursor:
            if change == "I3":
                cursor.executemany(spec["restore"], [(end_date, option_id) for option_id, end_date in backup])
            else:
                cursor.executemany(spec["restore"], [(*row[1:], row[0]) for row in backup])
