FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY backend/pom.xml backend/pom.xml
RUN --mount=type=cache,target=/root/.m2 mvn -f backend/pom.xml -q -DskipTests dependency:go-offline

COPY backend backend
RUN --mount=type=cache,target=/root/.m2 mvn -f backend/pom.xml -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app/backend

COPY --from=build /workspace/backend/target/stock-ai-backend-0.1.0-SNAPSHOT.jar /app/backend/app.jar
COPY frontend /app/frontend

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/backend/app.jar"]
