-- D5 규모 데이터셋
-- D3 와 같은 조합 생성기, 같은 seed 3 으로 사용자 수만 1,080,000명으로 늘린다
-- 그래서 차원별 비율은 D3 와 같고, 청구 대상 수만 36배가 된다
\set seed 3
\set users 1080000
\ir ../../_generator/combination_seed.sql
