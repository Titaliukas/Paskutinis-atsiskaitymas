FROM eclipse-temurin:25-jdk-jammy AS build
RUN apt-get update && apt-get install -y --no-install-recommends unzip && rm -rf /var/lib/apt/lists/*
WORKDIR /workspace
COPY . .
RUN chmod +x mvnw && ./mvnw -B -ntp clean verify

FROM eclipse-temurin:25-jre-jammy
WORKDIR /app
RUN groupadd --system game && useradd --system --gid game game
COPY --from=build --chown=game:game /workspace/game-server/target/game-server.jar /app/game-server.jar
USER game
ENV SERVER_PORT=8080
EXPOSE 8080
ENTRYPOINT ["java", "-Djava.awt.headless=true", "-jar", "/app/game-server.jar"]
