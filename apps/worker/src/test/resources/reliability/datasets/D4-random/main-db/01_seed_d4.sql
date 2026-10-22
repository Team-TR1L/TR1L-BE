-- D4 무작위 데이터셋
-- seed 는 psql -v seed=<값> 으로 바꿀 수 있다. 없으면 4
\if :{?seed}
\else
\set seed 4
\endif
\if :{?users}
\else
\set users 30000
\endif
\ir ../../_generator/combination_seed.sql
