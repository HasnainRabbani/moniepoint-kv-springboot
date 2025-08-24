# ---------- 1) Build stage ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy pom first to leverage layer caching
COPY pom.xml .
RUN mvn -q -e -DskipTests dependency:go-offline

# Copy sources and build
COPY src ./src
RUN mvn -q -DskipTests package

# ---------- 2) Runtime stage ----------
FROM eclipse-temurin:17-jre-alpine

# Create non-root user
RUN addgroup -S spring && adduser -S spring -G spring

# App dirs
WORKDIR /app
RUN mkdir -p /data && chown -R spring:spring /data

# Copy fat jar from build image
COPY --from=build /app/target/*.jar /app/app.jar

# Expose HTTP port
EXPOSE 8080

# Default runtime env (can be overridden at docker run / compose)
ENV KV_DATA_DIR=/data \
    KV_SYNC_MODE=ALWAYS \
    KV_BATCH_SYNC_EVERY=100 \
    KV_SYNC_INTERVAL_MS=50 \
    KV_COMPACT_THRESHOLD_BYTES=0

# Run as non-root
USER spring:spring

# Start the app
ENTRYPOINT ["java","-jar","/app/app.jar", \
  "--kv.data-dir=${KV_DATA_DIR}", \
  "--kv.sync-mode=${KV_SYNC_MODE}", \
  "--kv.batch-sync-every=${KV_BATCH_SYNC_EVERY}", \
  "--kv.sync-interval-ms=${KV_SYNC_INTERVAL_MS}", \
  "--kv.compact-threshold-bytes=${KV_COMPACT_THRESHOLD_BYTES}" ]
