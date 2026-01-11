# ===== 1) Build stage =====
FROM gradle:8.7-jdk17 AS builder
WORKDIR /app

# 캐시를 위해 먼저 복사 (의존성 변화가 적은 파일)
COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle

# 의존성 미리 받아두기(캐시 목적). 실패해도 다음 단계에서 빌드됨.
RUN ./gradlew dependencies --no-daemon || true

# 소스 복사 후 빌드
COPY . .
RUN ./gradlew clean bootJar -x test --no-daemon


# ===== 2) Runtime stage =====
FROM eclipse-temurin:17-jre
WORKDIR /app

# 빌드 결과 JAR 복사 (프로젝트에 따라 jar 이름이 달라도 되게 와일드카드)
COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
