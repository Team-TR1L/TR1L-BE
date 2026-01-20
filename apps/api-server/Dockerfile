FROM gradle:8.7-jdk17 AS builder
WORKDIR /workspace

COPY . .
RUN chmod +x ./gradlew

RUN ./gradlew :api-server:bootJar -x test --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /workspace/apps/api-server/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
