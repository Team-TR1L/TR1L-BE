FROM gradle:8.7-jdk17 AS builder
WORKDIR /workspace

# 1. 의존성 관련 파일만 먼저 복사
COPY build.gradle settings.gradle gradlew ./
COPY gradle ./gradle

# 2. 의존성 다운로드 (이 레이어는 자주 캐시됨)
RUN chmod +x ./gradlew && ./gradlew dependencies --no-daemon

# 3. 전체 소스 코드 복사
COPY . .

# 4. 애플리케이션 빌드
RUN ./gradlew :apps:delivery-server:bootJar -x test --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /workspace/apps/delivery-server/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
