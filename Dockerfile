# Stage 1: build frontend
FROM node:22-slim AS web-builder

WORKDIR /web

COPY web/package*.json ./
RUN npm ci --legacy-peer-deps

COPY web/ ./
RUN npm run build

# Stage 2: build backend (Linux binary) — uses pre-warmed builder image
FROM tat-builder:latest AS backend-builder

WORKDIR /build

COPY gradle/ gradle/
COPY gradlew gradlew
RUN sed -i 's/\r//' gradlew && chmod +x gradlew
COPY gradle.properties gradle.properties
COPY settings.gradle.kts settings.gradle.kts
COPY build.gradle.kts build.gradle.kts
COPY libs/ libs/
COPY src/ src/

RUN --mount=type=cache,target=/root/.gradle/caches \
    --mount=type=cache,target=/root/.gradle/wrapper \
    ./gradlew linkReleaseExecutableNative --no-daemon

# Stage 3: runtime image
FROM debian:bookworm-slim

RUN apt-get update && apt-get install -y libsqlite3-0 && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=backend-builder /build/build/bin/native/releaseExecutable/todo.kexe /app/server
COPY --from=web-builder /web/build /app/web

RUN chmod +x /app/server

VOLUME ["/data"]

ENV AUTH_MODE=header
ENV DB_PATH=/data/tat.db
ENV PORT=8080
ENV HOST=0.0.0.0
ENV STATIC_PATH=/app/web

EXPOSE 8080

CMD ["/app/server"]
