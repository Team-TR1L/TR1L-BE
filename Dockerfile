ARG APP_TASK
ARG APP_DIR

FROM gradle:8.8-jdk17 AS build
ARG APP_TASK
ARG APP_DIR
WORKDIR /home/gradle/src
COPY . .
RUN gradle ${APP_TASK} -x test --no-daemon

FROM eclipse-temurin:17-jre
ARG APP_DIR
WORKDIR /app
COPY --from=build /home/gradle/src/${APP_DIR}/build/libs/*.jar /app/app.jar
ENTRYPOINT ["java","-jar","/app/app.jar"]
