FROM maven:3.9.12-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY backend ./backend
RUN mvn -B -DskipTests package
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/backend/user-service/target/user-service-0.1.0-SNAPSHOT.jar /app/user.jar
COPY --from=build /workspace/backend/content-service/target/content-service-0.1.0-SNAPSHOT.jar /app/content.jar
COPY --from=build /workspace/backend/api-gateway/target/api-gateway-0.1.0-SNAPSHOT.jar /app/api.jar
COPY infra/run.sh /app/run.sh
RUN apt-get update && apt-get install -y --no-install-recommends curl && chmod 755 /app/run.sh && useradd -u 10001 -M appuser
USER 10001
ENTRYPOINT ["sh","/app/run.sh"]
