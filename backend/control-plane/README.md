# Control Plane

`control-plane` is a Spring Boot service that stores versioned pipeline configuration and executes
finite local pipeline revisions through the local runtime. It does not authenticate users.
PostgreSQL connectivity is supplied only through
`CONTROL_PLANE_DB_URL`, `CONTROL_PLANE_DB_USERNAME`, and `CONTROL_PLANE_DB_PASSWORD` environment
variables. Flyway migrates an empty database at startup, and health is available at
`/actuator/health`.

HTTP-started pipeline files are constrained by three local settings:

- `STREAMFORGE_LOCAL_PIPELINE_INPUT_ROOT` contains readable source files. Saved paths must be
  relative and cannot traverse or escape through symlinks.
- `STREAMFORGE_LOCAL_PIPELINE_ARTIFACT_ROOT` owns per-run output and dead-letter files. Persisted API
  records contain run-relative artifact identifiers, never host-absolute paths.
- `STREAMFORGE_LOCAL_PIPELINE_WORKSPACE` contains temporary materialized runtime configuration.

Copy [`backend/control-plane/.env.example`](.env.example) as a reference and choose roots suitable
for the local machine. The application defaults are intended for development only.

## Versioned APIs

The service exposes version-one schema and pipeline-definition APIs under `/api/v1/schemas` and
`/api/v1/pipelines`. Each resource supports creation, paginated listing, lookup, a validation-only
endpoint, immutable revision creation, mutable metadata updates, and archival that retains history.
Pipeline endpoints validate credential-free input/output JSON, safe declarative transformations,
and output blueprints before persistence. Local-run endpoints start and stop finite revisions,
return bounded monitoring snapshots, stream those snapshots over SSE, expose recent safe
dead-letter records, restore the latest run after restart, and download completed finite output
only while its managed artifact exists. `COMPLETED`, `CANCELLED`, and `FAILED` runtime outcomes map
to `COMPLETED`, `STOPPED`, and `FAILED` lifecycle states. Invalid requests use RFC
9457 problem details with an `errors` field containing field-level failures.

The generated OpenAPI document and interactive UI are available after startup at:

```sh
curl --fail http://localhost:8080/v3/api-docs
```

Open `http://localhost:8080/swagger-ui/index.html` in a browser for the interactive UI.

Start PostgreSQL and the service from the repository root:

```sh
cp infrastructure/compose/.env.example infrastructure/compose/.env
docker compose --env-file infrastructure/compose/.env \
  -f infrastructure/compose/docker-compose.yml up -d postgres
export CONTROL_PLANE_DB_URL=jdbc:postgresql://localhost:5432/streamforge
export CONTROL_PLANE_DB_USERNAME=streamforge
export CONTROL_PLANE_DB_PASSWORD=change-me-local-only
export STREAMFORGE_LOCAL_PIPELINE_INPUT_ROOT="$PWD/.streamforge/input"
export STREAMFORGE_LOCAL_PIPELINE_ARTIFACT_ROOT="$PWD/.streamforge/artifacts"
export STREAMFORGE_LOCAL_PIPELINE_WORKSPACE="$PWD/.streamforge/workspace"
mkdir -p "$STREAMFORGE_LOCAL_PIPELINE_INPUT_ROOT" \
  "$STREAMFORGE_LOCAL_PIPELINE_ARTIFACT_ROOT" \
  "$STREAMFORGE_LOCAL_PIPELINE_WORKSPACE"
./backend/mvnw -f backend/pom.xml -pl control-plane -am spring-boot:run
```

Confirm health in a second terminal:

```sh
curl --fail http://localhost:8080/actuator/health
```

Run repository integration tests with Docker available:

```sh
./backend/mvnw -f backend/pom.xml -pl control-plane -am test
```
