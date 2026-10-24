# syntax=docker/dockerfile:1

# =====================================================================
# Production image for the TurnoVivo backend (Spring Boot 4 + Java 21)
# Multi-stage build: compile with the JDK and run with a lightweight JRE.
# =====================================================================

# ---- Stage 1: build the JAR ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copy the wrapper and POM first to take advantage of layer caching.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

# Copy the source code and package it without tests; tests run in CI.
COPY src/ src/
RUN ./mvnw -B -q -DskipTests clean package

# ---- Stage 2: runtime image ----
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Use an unprivileged user for security.
RUN groupadd --system app && useradd --system --gid app app

COPY --from=build /app/target/*.jar app.jar
USER app

# Default production profile; it can be overridden through the environment.
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS=""

# The platform defines the actual port through the PORT variable (default: 3000).
EXPOSE 3000

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
