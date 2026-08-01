# Project Brief

## Problem

Market-data systems receive events in incompatible wire and file formats. Teams repeatedly build one-off parsers, normalization logic, and output integrations, making pipelines difficult to validate, evolve, and observe. StreamForge will provide a configurable path from heterogeneous market-data inputs to a precise canonical model and well-defined outputs.

## Intended Users

- Developers building or testing market-data ingestion and distribution systems.
- Data engineers creating repeatable normalization and transformation pipelines.
- Quantitative and operations teams that need deterministic replayable market-data flows without requiring a live exchange feed.

## MVP Boundary

The core MVP will run as a local, single-node pipeline. It will ingest simulated binary tick data, CSV, and JSONL; retain raw input for debugging and later replay; normalize every valid event through a canonical model; apply safe declarative transformations; and emit JSONL, CSV, or Parquet. It will preserve nanosecond timestamps, source sequence numbers, and exact fixed-point financial values across the pipeline and expose Prometheus-compatible metrics.

Later phases may add a PostgreSQL-backed control plane, a React dashboard, WebSocket delivery, Kafka-compatible streaming, optional Redis-backed ephemeral state, and distributed workers.

The current repository contains documentation and a Java 21 Maven build skeleton. None of the MVP behavior is implemented yet.

## Non-Goals

- Connecting directly to live exchanges or acting as a production trading gateway.
- Executing arbitrary user-provided JavaScript or expressions.
- Providing order management, strategy execution, portfolio accounting, or trade settlement.
- Delivering a remote control plane, dashboard, WebSocket or Kafka integration, replay controls, distributed dead-letter handling, or order-book reconstruction in the core MVP.
