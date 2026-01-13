INFRA = postgres mongo

.PHONY: infra-up infra-down infra-ps \
        api dispatch batch \
        api-build dispatch-build batch-build \
        logs-api logs-dispatch logs-batch \
        down clean

# --- INFRA ---
infra-up:
	docker compose up -d $(INFRA)

infra-down:
	docker compose down

infra-ps:
	docker compose ps

# --- APPS ---
api: infra-up
	docker compose up -d api-server

dispatch: infra-up
	docker compose up -d dispatch-server kafka

batch: infra-up
	docker compose up -d worker

# --- BUILD + RESTART ---
api-build: infra-up
	docker compose build api-server
	docker compose up -d --no-deps api-server

dispatch-build: infra-up
	docker compose build dispatch-server
	       docker compose up -d --no-deps dispatch-server

batch-build: infra-up
	docker compose build worker
	docker compose up -d --no-deps worker

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
