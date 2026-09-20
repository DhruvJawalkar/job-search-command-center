ARG MAVEN_BUILD_IMAGE=maven:3.9.11-eclipse-temurin-21-alpine
ARG TEMURIN_RUNTIME_IMAGE=eclipse-temurin:21-jre-alpine

FROM ${MAVEN_BUILD_IMAGE} AS build
WORKDIR /workspace
COPY pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package && cp target/*.jar /tmp/job-search-command-center.jar

FROM ${TEMURIN_RUNTIME_IMAGE}
ARG APP_VERSION=dev
ARG VCS_REF=unknown
ARG SOURCE_URL=https://github.com/DhruvJawalkar/job-search-command-center
LABEL org.opencontainers.image.title="Job Search Command Center API" \
      org.opencontainers.image.version="${APP_VERSION}" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.source="${SOURCE_URL}" \
      org.opencontainers.image.licenses="LicenseRef-PolyForm-Noncommercial-1.0.0" \
      dev.dhruv.job-search-command-center.license-url="https://polyformproject.org/licenses/noncommercial/1.0.0" \
      dev.dhruv.job-search-command-center.required-notice="Required Notice: Copyright © 2026 Dhruv Jawalkar. Job Search Command Center was created and is maintained by Dhruv Jawalkar."
RUN apk del --no-network coreutils \
    && addgroup -S app \
    && adduser -S app -G app
WORKDIR /app
COPY --from=build /tmp/job-search-command-center.jar /app/job-search-command-center.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/job-search-command-center.jar"]
