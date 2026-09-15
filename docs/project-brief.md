# Project Brief

StreamForge 1.0 solves the repeated work of converting precise market data between incompatible
file and wire formats. It is intended for developers, data engineers, quantitative teams, and
operations users who need deterministic local normalization without a live exchange connection.

The v1 product is a Docker Compose application containing a Java 21 pipeline/control-plane
service, PostgreSQL, and a static React dashboard. STP binary, CSV, and JSONL inputs pass through a
single canonical event model before safe transformation and JSONL, CSV, or Parquet output. Run
inputs are captured byte-for-byte before parsing. Nanosecond timestamps, sequence numbers, and
fixed-point monetary values remain exact.

V1 is local and unauthenticated. It does not include live exchange connectivity, arbitrary code,
authentication, distributed workers, Kafka, Redis, WebSockets, replay controls, order-book
reconstruction, Stream Inspector, global dead-letter browsing, schema editing, trading, portfolio
accounting, or settlement.
