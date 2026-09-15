FROM maven:3.9.11-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
COPY pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package && cp target/*.jar /tmp/job-search-command-center.jar

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl && addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /tmp/job-search-command-center.jar /app/job-search-command-center.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/job-search-command-center.jar"]
