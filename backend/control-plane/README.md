# Control Plane 1.0

The Spring Boot control plane persists versioned, credential-free pipeline definitions and local
run lifecycle in PostgreSQL. It executes finite local runs, always captures input, publishes
managed output/dead-letter artifacts, restores terminal status after restart, and exposes REST,
SSE, OpenAPI, readiness/liveness, and Prometheus endpoints. It is intentionally unauthenticated
and local-only.

The supported product startup from the repository root is:

```sh
docker compose up --build
```

Inputs are relative to `/data/input`; outputs, dead letters, and captures are relative to the
server-owned artifact root. Raw capture is downloadable for every run where capture completed,
including failed and stopped runs. Artifact retention is manual in v1.

For host development, set `CONTROL_PLANE_DB_URL`, `CONTROL_PLANE_DB_USERNAME`,
`CONTROL_PLANE_DB_PASSWORD`, `STREAMFORGE_LOCAL_PIPELINE_INPUT_ROOT`,
`STREAMFORGE_LOCAL_PIPELINE_WORKSPACE`, and `STREAMFORGE_LOCAL_PIPELINE_ARTIFACT_ROOT`, then run:

```sh
./backend/mvnw -f backend/pom.xml -pl control-plane -am spring-boot:run
```

Useful endpoints:

- `/v3/api-docs` and `/swagger-ui/index.html`
- `/actuator/health/liveness`
- `/actuator/health/readiness`
- `/actuator/prometheus`
- `/api/v1/pipelines/{pipelineId}/runs/{runId}/output`
- `/api/v1/pipelines/{pipelineId}/runs/{runId}/raw-capture`
