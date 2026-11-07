# Build (from this directory, with .env filled in):
#   docker build -t osc-markets-api .
# Run, loading this directory's .env.
# If Postgres is on this same machine, the entrypoint uses the Docker gateway
# when the public IP in SPRING_DATASOURCE_URL is not reachable from the container.
#   docker run -d --name osc-markets-api --restart unless-stopped --env-file .env -p 8286:8286 osc-markets-api

# ============================
# Build stage
# ============================
FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests


# ============================
# Runtime stage
# ============================
FROM eclipse-temurin:21-jre

WORKDIR /app

RUN useradd -ms /bin/bash springuser

COPY --from=builder /app/target/*.jar app.jar
COPY .env .env
COPY docker-entrypoint.sh /app/docker-entrypoint.sh

RUN chmod +x /app/docker-entrypoint.sh \
    && chown -R springuser:springuser /app
USER springuser

EXPOSE 8286

ENV SERVER_PORT=8286
ENV SERVER_ADDRESS=0.0.0.0
ENV JAVA_OPTS="-Xms256m -Xmx1024m"

ENTRYPOINT ["/app/docker-entrypoint.sh"]
