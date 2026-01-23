############################################
# 1) Build stage
############################################
FROM gradle:8.7-jdk17 AS builder
WORKDIR /workspace

COPY build.gradle settings.gradle gradlew ./
COPY gradle ./gradle
RUN chmod +x ./gradlew && ./gradlew dependencies --no-daemon

COPY . .
RUN ./gradlew :apps:worker:bootJar -x test --no-daemon


############################################
# 2) certs stage: create truststore (default cacerts + RDS global bundle)
############################################
FROM eclipse-temurin:17-jdk-jammy AS certs
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl openssl ca-certificates \
  && rm -rf /var/lib/apt/lists/*

RUN set -eux; \
  CERT_DIR=/opt/certs; \
  STOREPASS=changeit; \
  TRUSTSTORE=${CERT_DIR}/rds-truststore.jks; \
  mkdir -p ${CERT_DIR}; \
  \
  # 기본 CA 유지(cacerts 복사)
  cp "${JAVA_HOME}/lib/security/cacerts" "${TRUSTSTORE}"; \
  \
  # RDS 글로벌 CA 번들 다운로드
  curl -sS "https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem" -o "${CERT_DIR}/global-bundle.pem"; \
  cd "${CERT_DIR}"; \
  \
  # PEM 인증서 분리
  awk 'split_after==1 {n++; split_after=0} \
       /-----END CERTIFICATE-----/ {split_after=1} \
       {print > ("rds-ca-" n ".pem")}' < global-bundle.pem; \
  \
  # truststore에 import
  for CERT in rds-ca-*.pem; do \
    CN="$(openssl x509 -noout -subject -in "$CERT" | sed -n 's/.*CN=//p' | tr -d " /")"; \
    ALIAS="${CN:-rds-ca}-${CERT}"; \
    keytool -importcert -noprompt \
      -keystore "${TRUSTSTORE}" \
      -storepass "${STOREPASS}" \
      -alias "${ALIAS}" \
      -file "${CERT}"; \
    rm -f "${CERT}"; \
  done; \
  rm -f global-bundle.pem; \
  keytool -list -keystore "${TRUSTSTORE}" -storepass "${STOREPASS}" >/dev/null


############################################
# 3) agents stage: (FIX) use writable dir
############################################
FROM curlimages/curl:8.5.0 AS agents
WORKDIR /tmp/agents

RUN set -eux; \
  mkdir -p otel pyroscope; \
  curl -fsSL -o otel/opentelemetry-javaagent.jar \
    https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar; \
  curl -fsSL -o pyroscope/pyroscope.jar \
    https://github.com/grafana/pyroscope-java/releases/latest/download/pyroscope.jar


############################################
# 4) runtime stage
############################################
FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=builder /workspace/apps/worker/build/libs/*.jar /app/app.jar

# truststore
COPY --from=certs /opt/certs/rds-truststore.jks /opt/certs/rds-truststore.jks

# agents (copy from /tmp/agents)
COPY --from=agents /tmp/agents/otel/opentelemetry-javaagent.jar /otel/opentelemetry-javaagent.jar
COPY --from=agents /tmp/agents/pyroscope/pyroscope.jar /pyroscope/pyroscope.jar

EXPOSE 8080

ENV DOCDB_TRUSTSTORE_PATH=/opt/certs/rds-truststore.jks
ENV DOCDB_TRUSTSTORE_PASSWORD=changeit

RUN set -eux; \
  printf '%s\n' \
'#!/usr/bin/env sh' \
'set -eu' \
'' \
': "${DOCDB_TRUSTSTORE_PATH:=/opt/certs/rds-truststore.jks}"' \
': "${DOCDB_TRUSTSTORE_PASSWORD:=changeit}"' \
'' \
'# 기존 JAVA_TOOL_OPTIONS 유지 + truststore 옵션 append' \
'export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Djavax.net.ssl.trustStore=${DOCDB_TRUSTSTORE_PATH} -Djavax.net.ssl.trustStorePassword=${DOCDB_TRUSTSTORE_PASSWORD}"' \
'' \
'exec java -javaagent:/otel/opentelemetry-javaagent.jar -javaagent:/pyroscope/pyroscope.jar -jar /app/app.jar "$@"' \
> /entrypoint.sh; \
  chmod +x /entrypoint.sh

ENTRYPOINT ["/entrypoint.sh"]
