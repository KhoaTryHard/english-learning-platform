FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN groupadd --system progress && useradd --system --gid progress progress
COPY --from=build --chown=progress:progress /workspace/target/progress-service-0.1.0-SNAPSHOT.jar app.jar
USER progress
EXPOSE 8091
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
