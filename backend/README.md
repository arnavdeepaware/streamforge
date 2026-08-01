# Backend Build

The backend is a Java 21 Maven multi-module build. It currently contains build configuration and one smoke test only; no StreamForge runtime features are implemented.

## Requirements

- A JDK from 21 through 26. The build compiles with Java 21 release compatibility.
- Internet access the first time Maven Wrapper downloads Maven and dependency artifacts.

## Commands

From the repository root:

```sh
./backend/mvnw -f backend/pom.xml verify
```

From the `backend/` directory:

```sh
./mvnw verify
```

The reactor includes `common-model`, `stp-protocol`, `tick-simulator`, `parser-engine`, `transform-engine`, `pipeline-runtime`, `control-plane`, and `stream-worker`.
