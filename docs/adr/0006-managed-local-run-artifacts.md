# ADR 0006: Managed Local Run Artifacts and Explicit Outcomes

Date: 2026-08-03

Status: Accepted

## Context

The local control plane starts saved pipeline revisions through an unauthenticated HTTP API. Saved
input and output paths cannot safely act as unrestricted host paths, and a persisted lifecycle
state alone does not distinguish successful processing, cooperative cancellation, and terminal
failure. Monitoring must also remain bounded and recover useful terminal state after restart.

## Decision

HTTP-started runs resolve relative input paths beneath a configured input root using real-path
containment. Outputs and local JSONL dead letters are placed beneath a configured artifact root in
server-owned `{runId}` directories. Only run-relative artifact identifiers are persisted. Output is
downloadable only for a `COMPLETED` run when the recorded file still resolves beneath the artifact
root. CLI execution remains a separate trusted boundary and may accept explicit local paths.

`PipelineReport` records one of `COMPLETED`, `CANCELLED`, or `FAILED`. Record-level failures handled
by skip or quarantine policy do not prevent `COMPLETED`; input, output, configuration, fail-fast,
and dead-letter-store failures produce `FAILED`. These outcomes map to persisted lifecycle states
`COMPLETED`, `STOPPED`, and `FAILED` respectively.

Run creation commits `STARTING` before execution is launched. Transactional lifecycle persistence
is separated from asynchronous coordination, and one atomic active-run holder prevents concurrent
starts per pipeline. Local active states discovered on process startup become `FAILED` because the
in-process execution cannot survive restart.

Live monitoring coalesces updates away from the processing thread. It retains all active runs and
at most 100 terminal observations for 24 hours. Persisted final counters and up to 50 recent managed
dead letters may be rehydrated, but historical rate samples are not persisted.

## Alternatives Considered

- Trust saved absolute paths. Rejected because an unauthenticated local API would expose arbitrary
  host files and output locations.
- Store absolute resolved artifact paths. Rejected because database records would leak host layout
  and would not be portable when the configured root changes.
- Treat every record failure as a failed run. Rejected because quarantine and skip are explicit
  policies for continuing independent event processing.
- Persist every monitoring sample. Deferred because the MVP needs bounded operational visibility,
  not a time-series database.

## Consequences

Existing revisions containing absolute or escaping paths fail safely and require a new revision.
Moving or deleting the artifact root makes old downloads unavailable even though final reports
remain durable. Terminal monitoring can be restored after restart, but event-rate history begins
empty. Deployments must configure and protect both local roots; these boundaries do not replace
authentication for a non-local deployment.
