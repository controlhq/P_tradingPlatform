# ---- build stage: Maven + JDK 21 ----
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build

# dependencies first, so this layer is reused until pom.xml changes
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# tests need Docker (Testcontainers), so they run outside the image build: ./mvnw test
RUN mvn -B -q package -DskipTests \
    && cp target/tradingPlatform-*.jar app.jar

# ---- runtime stage: JRE only ----
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app --no-create-home app
COPY --from=build --chown=app:app /build/app.jar app.jar
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
