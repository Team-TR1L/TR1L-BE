#!/usr/bin/env bash
set -euo pipefail

CERT_DIR="${1:-/opt/certs}"
STOREPASS="${2:-changeit}"
TRUSTSTORE="${CERT_DIR}/rds-truststore.jks"

mkdir -p "${CERT_DIR}"
cd "${CERT_DIR}"

# 1) AWS CA bundle 다운로드
curl -sS "https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem" -o global-bundle.pem

# 2) PEM 안의 인증서들을 개별 파일로 분리
awk 'split_after==1 {n++; split_after=0}
     /-----END CERTIFICATE-----/ {split_after=1}
     {print > ("rds-ca-" n ".pem")}' < global-bundle.pem

# 3) 각 인증서를 keytool로 JKS에 import
for CERT in rds-ca-*.pem; do
  # alias는 CN을 뽑아서 쓰는데, 중복될 수 있으니 파일명도 섞어줌
  CN=$(openssl x509 -noout -subject -in "${CERT}" | sed -n 's/.*CN=//p' | tr -d ' /')
  ALIAS="${CN:-docdb-ca}-${CERT}"

  echo "Importing ${ALIAS}"
  keytool -import -noprompt \
    -file "${CERT}" \
    -alias "${ALIAS}" \
    -keystore "${TRUSTSTORE}" \
    -storepass "${STOREPASS}"

  rm -f "${CERT}"
done

rm -f global-bundle.pem

# 4) sanity check
keytool -list -keystore "${TRUSTSTORE}" -storepass "${STOREPASS}" >/dev/null
echo "✅ truststore created: ${TRUSTSTORE}"
