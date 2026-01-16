.PHONY: infra-up infra-down infra-ps \
        api dispatch batch \
        api-build dispatch-build batch-build \
        logs-api logs-dispatch logs-batch \
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
	docker compose up -d api-server postgres

dispatch:
	docker compose up -d dispatch-server kafka postgres_target

batch:
	docker compose up -d worker postgres postgres_target mongo

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

# --- LOGS ---
logs-api:
	docker compose logs -f api-server

logs-dispatch:
	docker compose logs -f dispatch-server

logs-batch:
	docker compose logs -f worker

# --- STOP / CLEAN ---
down:
	docker compose down

clean:
	docker compose down -v
