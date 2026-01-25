.PHONY: infra-up infra-down infra-ps \
        api dispatch batch \
        api-build dispatch-build batch-build \
        logs-api logs-dispatch logs-batch \
        local-worker-monitor local-worker-monitor-down \
        down clean

# --- INFRA ---
up:
	docker compose up

down:
	docker compose down

ps:
	docker compose ps

# --- APPS ---
api:
	docker compose up -d api-server postgres_target

dispatch:
	docker compose up -d dispatch-server kafka postgres_target

batch:
	docker compose up -d worker postgres postgres_target mongo

delivery:
	docker compose up -d delivery-server kafka postgres_target

# --- BUILD + RESTART ---
api-build:
	docker compose build api-server
	docker compose up -d --no-deps api-server

dispatch-build:
	docker compose build dispatch-server kafka postgres_target
	docker compose up -d --no-deps dispatch-server kafka postgres_target

batch-build:
	docker compose build worker postgres postgres_target mongo
	docker compose up -d --no-deps worker postgres postgres_target mongo

delivery-build:
	docker compose build delivery-server kafka postgres_target
	       docker compose up -d --no-deps delivery-server kafka postgres_target

# --- LOGS ---
logs-api:
	docker compose logs -f api-server

logs-dispatch:
	docker compose logs -f dispatch-server

logs-batch:
	docker compose logs -f worker

logs-delivery:
	docker compose logs -f delivery

# --- LOCAL MONITORING (worker only) ---
local-worker-monitor:
	docker network create --driver bridge --attachable tr1l-monitoring || true
	docker compose -f docker-compose.local.monitoring.yml up -d
	docker compose --profile batch --compatibility \
		-f docker-compose.yml -f docker-compose.local.worker.yml \
		up --build postgres_target worker

local-worker-monitor-down:
	docker compose -f docker-compose.local.monitoring.yml down
	docker compose --profile batch --compatibility \
		-f docker-compose.yml -f docker-compose.local.worker.yml \
		down
	docker network rm tr1l-monitoring || true

# --- STOP / CLEAN ---
down:
	docker compose down

clean:
	docker compose down -v
