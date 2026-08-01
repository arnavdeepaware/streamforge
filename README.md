# StreamForge

StreamForge is a planned configurable platform for ingesting real-time market data, normalizing it into a canonical event model, applying safe declarative transformations, and delivering it to multiple output formats and transports.

The Java 21 backend Maven reactor has been initialized with build configuration and a smoke test. No StreamForge runtime features, dashboard, or infrastructure services have been implemented yet.

Verify the backend from the repository root:

```sh
./backend/mvnw -f backend/pom.xml verify
```
