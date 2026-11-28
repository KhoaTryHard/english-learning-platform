FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN groupadd --system assessment && useradd --system --gid assessment assessment
COPY --from=build --chown=assessment:assessment /workspace/target/assessment-service-0.1.0-SNAPSHOT.jar app.jar
USER assessment
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
