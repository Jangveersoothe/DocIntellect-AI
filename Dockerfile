FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY .mvn ./.mvn
COPY mvnw mvnw.cmd ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
RUN ./mvnw -q -DskipTests dependency:go-offline
COPY src ./src
RUN ./mvnw -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --create-home --shell /usr/sbin/nologin docintellect \
    && mkdir -p /app/uploads \
    && chown docintellect:docintellect /app/uploads
COPY --from=build --chown=docintellect:docintellect /workspace/target/docintellect-ai-*.jar app.jar
USER docintellect
EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
