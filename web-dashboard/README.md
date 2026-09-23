# StreamForge Web Dashboard

The dashboard is the local operations workspace for creating, running, and
monitoring StreamForge pipelines. It is a React 19 and TypeScript application
served by Vite during development and Nginx in the production container.

## Development

Use Node.js 22 and npm:

```bash
npm install
npm run dev
```

The development server proxies `/api` to `http://localhost:8080` unless
`VITE_CONTROL_PLANE_PROXY_TARGET` is set. Runtime API paths, payloads, and
response parsing are owned by `src/api` and must remain independent from visual
components.

## Interface system

- Inter Variable is bundled for interface text; IBM Plex Mono is bundled for
  paths, identifiers, metrics, and code.
- The light operations palette uses a cool neutral canvas, white surfaces, deep
  teal actions, and distinct success, warning, danger, and information colors.
- CSS design tokens at the top of `src/styles.css` define color, spacing,
  typography, radii, elevation, sidebar sizing, and motion.
- The desktop sidebar collapses at the user's request. At 900px and below it
  becomes a keyboard-operable slide-over; grids progressively reduce to one
  column at 680px.
- Contextual “About this page” disclosures and the global help drawer explain
  features without blocking experienced users.

## Accessibility expectations

- Preserve semantic headings, labels, landmarks, live regions, and keyboard
  behavior when changing presentation.
- Interactive targets are at least 44px high for primary controls, with visible
  amber focus treatment.
- Status must use text or icons in addition to color.
- Layout must reflow without page-level horizontal scrolling, including at 200%
  text zoom.
- Motion is intentionally subtle and effectively disabled when the browser
  requests reduced motion.

## Verification

```bash
npm run format:check
npm run lint
npm test
npm run build
npm run test:e2e
```

The Playwright suite covers 390px, 768px, and 1440px viewports with mocked
control-plane responses and automated axe checks. Install its Chromium browser
once with `npx playwright install chromium` when it is not already available.
