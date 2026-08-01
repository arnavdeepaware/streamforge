# StreamForge

StreamForge is a planned configurable platform for ingesting real-time market data, normalizing it into a canonical event model, applying safe declarative transformations, and delivering it to multiple output formats and transports.

The Java 21 backend Maven reactor and a React/Vite dashboard shell have been initialized. The dashboard contains only accessible placeholder routes; no StreamForge runtime features, backend integration, or infrastructure services have been implemented yet.

Verify the backend from the repository root:

```sh
./backend/mvnw -f backend/pom.xml verify
```

Install dashboard dependencies from the repository root:

```sh
npm --prefix web-dashboard ci
```

Run the dashboard development server:

```sh
npm --prefix web-dashboard run dev
```

Run dashboard checks:

```sh
npm --prefix web-dashboard run lint
npm --prefix web-dashboard run test
npm --prefix web-dashboard run build
```

## Validation

Run every local quality check from the repository root:

```sh
make check
```

Run one area at a time:

```sh
make backend-check
make web-check
```

`backend-check` runs Maven Wrapper verification, including Java formatting enforcement. `web-check` runs `npm ci`, Prettier format checking, ESLint, Vitest in non-watch mode, and the Vite production build. GitHub Actions runs these same targets with Maven and npm caches.

Remove generated build outputs with:

```sh
make clean
```
