FROM maven:3.9.12-eclipse-temurin-21 AS build
WORKDIR /workspace
ENV MAVEN_OPTS="-Xmx512m -Duser.timezone=UTC"
COPY pom.xml ./
COPY src ./src
RUN mvn -B -Dmaven.test.skip=true package
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl && useradd -u 10001 -M appuser
COPY --from=build /workspace/target/content-service-0.1.0-SNAPSHOT.jar /app/app.jar
COPY deploy/run.sh /app/run.sh
EXPOSE 8082
USER 10001
ENTRYPOINT ["sh","/app/run.sh"]
