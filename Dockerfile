# syntax=docker/dockerfile:1

# ---------- Build stage ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Leverage Docker layer caching
COPY pom.xml ./
RUN mvn -q -e -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q -DskipTests package

# ---------- Runtime stage ----------
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Default server port (can be overridden at runtime)
ENV SERVER_PORT=8082
ENV JAVA_OPTS=""
ENV DB_PATH=/app/data

# Copy the built Spring Boot fat jar
COPY --from=build /app/target/search-analytics-service-*.jar /app/app.jar

EXPOSE 8082

# Allow overriding port and JVM options
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dserver.port=${SERVER_PORT} -jar /app/app.jar"]

# Persist application data (e.g., SQLite db)
VOLUME ["/app/data"]