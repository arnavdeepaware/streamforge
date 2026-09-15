# ADR 0008: Explicit Parquet Schema Contract

Date: 2026-09-15

Status: Accepted

## Context

Schema inference can vary with input order and can silently coerce exact timestamps, sequence
numbers, and fixed-point financial values. StreamForge requires deterministic files across mixed
canonical event variants.

## Decision

Parquet output requires a user-declared list of unique columns. Each column names a canonical or
transformed scalar path and one of `STRING`, `BOOLEAN`, `INT64`, `TIMESTAMP_NANOS`, or
`FIXED_DECIMAL`. Fixed decimals alone require a scale from 0 through 18 and use Parquet
`DECIMAL(19, scale)` with an exact non-floating-point physical representation. Timestamps use
nanosecond precision and signed integral values remain `INT64`.

The runtime rejects absent required values, non-scalars, incompatible types, lossy decimal
rescaling, overflow, duplicate names, invalid scale declarations, and unsupported compression.
Publication is staged and atomic. Supported compression is `UNCOMPRESSED`, `SNAPPY`, and `ZSTD`,
with `SNAPPY` as the default.

## Consequences

Every produced Parquet file has a predictable schema independent of observed data. Configuration
is more verbose, but correctness failures are visible instead of being hidden by inference or
floating-point conversion.
