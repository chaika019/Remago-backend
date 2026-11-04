FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src src
# Keep downloaded artifacts across builds, including failed build attempts.
RUN --mount=type=cache,id=ad-crm-maven,target=/root/.m2,sharing=locked \
    mvn -B -U --no-transfer-progress package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/target/ad-crm-backend-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
