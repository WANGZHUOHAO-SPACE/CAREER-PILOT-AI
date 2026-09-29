FROM maven:3.9-eclipse-temurin-21-noble AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
COPY sql/migrate-v15.sql ./sql/migrate-v15.sql
COPY sql/migrate-v16.sql ./sql/migrate-v16.sql
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp clean package

FROM eclipse-temurin:21-jre-noble
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/target/*.jar /app/app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
