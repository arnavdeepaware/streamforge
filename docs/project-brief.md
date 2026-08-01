# Project Brief

## Problem

Market-data systems receive events in incompatible wire and file formats. Teams repeatedly build one-off parsers, normalization logic, and output integrations, making pipelines difficult to validate, evolve, and observe. StreamForge will provide a configurable path from heterogeneous market-data inputs to a precise canonical model and well-defined outputs.

## Intended Users

- Developers building or testing market-data ingestion and distribution systems.
- Data engineers creating repeatable normalization and transformation pipelines.
- Quantitative and operations teams that need deterministic replayable market-data flows without requiring a live exchange feed.

## MVP Boundary

The MVP will ingest simulated binary tick data, CSV, and JSONL; normalize every event through a canonical model; apply safe declarative transformations; and emit JSONL, CSV, Parquet, WebSocket, or Kafka output. It will preserve nanosecond timestamps, sequence numbers, and exact fixed-point financial values across the pipeline.

The current repository contains only documentation and directory scaffolding. None of the MVP behavior is implemented yet.

## Non-Goals

- Connecting directly to live exchanges or acting as a production trading gateway.
- Executing arbitrary user-provided JavaScript or expressions.
- Providing order management, strategy execution, portfolio accounting, or trade settlement.
- Delivering live metrics, replay controls, dead-letter handling, or order-book reconstruction in the initial MVP.
