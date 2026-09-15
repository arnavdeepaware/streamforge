# ADR 0007: Immutable Run-Scoped Raw Capture

Date: 2026-09-15

Status: Accepted

## Context

Parsing a caller-owned file directly makes a run non-reproducible: the file can change while it is
being processed or disappear after a failure. Canonical events also need references that identify
the exact source bytes used by one run.

## Decision

Every local run generates a UUID and copies the source byte-for-byte into its managed artifact
directory before parsing begins. The copy is staged, hashed with SHA-256 while streaming, and
atomically published with a manifest containing the run ID, input type, original filename, byte
length, digest, capture time, and stored filename. Existing capture files are never replaced.

The parser consumes only the published capture. Canonical raw references are rewritten as
`capture:<run-id>:frame:<n>`, `:line:<n>`, or `:row:<n>`. Captures remain for every terminal
outcome, including failed, stopped, and partially processed runs. Local v1 retention is manual.

## Consequences

A run observes one stable byte sequence and can expose that evidence through a safe managed
download. Capturing requires enough storage for a full input before processing starts. Capture
failure prevents parsing and becomes a structured run failure. Automatic retention and remote
object storage remain post-v1 concerns.
