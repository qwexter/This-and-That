FROM gradle:9.3-jdk21

RUN apt-get update && apt-get install -y libsqlite3-dev && rm -rf /var/lib/apt/lists/*

WORKDIR /warmup

COPY gradle/ gradle/
COPY gradlew gradlew
COPY gradle.properties gradle.properties
COPY settings.gradle.kts settings.gradle.kts
COPY libs/ libs/
COPY build.gradle.kts build.gradle.kts

RUN sed -i 's/\r//' gradlew && chmod +x gradlew

# Bake .konan and gradle deps INTO the image — becomes the persistent cache layer.
# Rebuild this image only when gradle files change (via build.ps1 checksum gate).
RUN ./gradlew downloadKotlinNativeDistribution dependencies --no-daemon || true
