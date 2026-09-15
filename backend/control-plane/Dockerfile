FROM eclipse-temurin:21-jdk-jammy AS build

WORKDIR /workspace
COPY backend ./backend
COPY schemas ./schemas
RUN ./backend/mvnw -f backend/pom.xml -pl control-plane -am -DskipTests package

FROM eclipse-temurin:21-jre-jammy

# hadolint ignore=DL3008
RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
RUN groupadd --system --gid 10001 streamforge \
    && useradd --system --uid 10001 --gid streamforge streamforge
WORKDIR /opt/streamforge
COPY --from=build /workspace/backend/control-plane/target/control-plane-1.0.0.jar ./control-plane.jar
RUN mkdir -p /data/input /data/workspace /data/artifacts \
    && chown -R streamforge:streamforge /opt/streamforge /data/workspace /data/artifacts

USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/opt/streamforge/control-plane.jar"]
