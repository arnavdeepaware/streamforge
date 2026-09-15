# Changelog

## 1.0.0 - Unreleased

- Added mandatory immutable, run-scoped raw capture with SHA-256 manifests and stable canonical
  record references.
- Added explicit-schema Parquet output with exact integers, nanosecond timestamps, fixed decimals,
  atomic publication, and Snappy/Zstandard/uncompressed codecs.
- Added raw-capture monitoring and managed download APIs and dashboard controls.
- Added bounded Prometheus pipeline metrics plus storage-aware readiness and process liveness.
- Added production control-plane and dashboard images and a one-command Docker Compose stack.
- Defined the local v1 feature boundary and removed post-v1 inspector/global dead-letter routes.

The release remains unreleased until every item in `docs/v1-release-checklist.md` passes and an
authorized release commit and tag are created.
