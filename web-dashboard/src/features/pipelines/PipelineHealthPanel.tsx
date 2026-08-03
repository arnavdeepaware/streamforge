import { useEffect } from 'react';
import {
  pipelineOutputDownloadUrl,
  type PipelineRun,
  type PipelineRunState,
} from '../../api/controlPlaneClient';
import { usePipelineMonitoring } from './usePipelineMonitoring';

export function PipelineHealthPanel({
  pipelineId,
  run,
  onStateChange,
}: {
  pipelineId: string;
  run: PipelineRun;
  onStateChange: (state: PipelineRunState) => void;
}) {
  const monitoring = usePipelineMonitoring(pipelineId, run.runId);
  const snapshot = monitoring.snapshot;
  const state = snapshot?.state ?? run.state;
  const terminal = ['STOPPED', 'COMPLETED', 'FAILED'].includes(state);

  useEffect(() => {
    if (snapshot !== null && snapshot.state !== run.state)
      onStateChange(snapshot.state);
  }, [onStateChange, run.state, snapshot]);

  return (
    <section
      aria-labelledby="pipeline-health-title"
      className="pipeline-health"
    >
      <div className="pipeline-health__heading">
        <div>
          <p className="eyebrow">Live local run</p>
          <h3 id="pipeline-health-title">Pipeline health</h3>
        </div>
        <p aria-live="polite" className="connection-state">
          {monitoring.connection === 'reconnecting'
            ? 'Reconnecting to metrics…'
            : `Metrics ${monitoring.connection}`}
        </p>
      </div>
      <dl className="health-summary">
        <Metric label="Lifecycle" value={state} />
        <Metric
          label="Event rate"
          value={`${snapshot?.eventRatePerSecond ?? 0} events/s`}
        />
        <Metric
          label="Latency"
          value={formatNanos(snapshot?.latency.averageNanos ?? 0)}
        />
        <Metric
          label="Timed events"
          value={String(snapshot?.latency.processedEvents ?? 0)}
        />
        <Metric label="Queue depth" value={String(snapshot?.queueDepth ?? 0)} />
        <Metric
          label="Sequence gaps"
          value={String(snapshot?.sequenceGapCount ?? 0)}
        />
        <Metric
          label="Duplicates"
          value={String(snapshot?.duplicateCount ?? 0)}
        />
      </dl>
      {monitoring.error ? <p role="alert">{monitoring.error}</p> : null}
      <CounterCards
        counters={
          snapshot?.counters ?? {
            received: 0,
            parsed: 0,
            emitted: 0,
            filtered: 0,
            failed: 0,
          }
        }
      />
      <RateHistory history={snapshot?.history ?? []} />
      <DeadLetters deadLetters={snapshot?.deadLetters ?? []} />
      {terminal && state !== 'FAILED' ? (
        <a
          className="download-link"
          href={pipelineOutputDownloadUrl(pipelineId, run.runId)}
        >
          Download finite output
        </a>
      ) : null}
    </section>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt>{label}</dt>
      <dd>{value}</dd>
    </div>
  );
}

function CounterCards({
  counters,
}: {
  counters: {
    received: number;
    parsed: number;
    emitted: number;
    filtered: number;
    failed: number;
  };
}) {
  return (
    <div className="counter-grid" aria-label="Pipeline event counters">
      <Metric label="Received" value={String(counters.received)} />
      <Metric label="Parsed" value={String(counters.parsed)} />
      <Metric label="Emitted" value={String(counters.emitted)} />
      <Metric label="Filtered" value={String(counters.filtered)} />
      <Metric label="Failed" value={String(counters.failed)} />
    </div>
  );
}

function RateHistory({
  history,
}: {
  history: { received: number; timestamp: string }[];
}) {
  const maximum = Math.max(1, ...history.map((sample) => sample.received));
  const latest = history.at(-1)?.received ?? 0;
  return (
    <section aria-labelledby="event-history-title" className="rate-history">
      <h4 id="event-history-title">Received-event history</h4>
      <p className="visually-hidden">
        {history.length} retained samples. Latest received count: {latest}.
      </p>
      <div aria-hidden="true" className="rate-bars">
        {history.map((sample) => (
          <span
            className="rate-bars__bar"
            key={sample.timestamp}
            style={{
              height: `${Math.max(3, (sample.received / maximum) * 100)}%`,
            }}
          />
        ))}
      </div>
      <p className="form-hint">
        History is capped at 120 metric snapshots; raw events are not retained.
      </p>
    </section>
  );
}

function DeadLetters({
  deadLetters,
}: {
  deadLetters: {
    failureId: string;
    stage: string;
    category: string;
    sourceLocation: string;
    safeMessage: string;
    retryability: string;
    timestamp: string;
    payloadEncoding: string | null;
    payloadPreview: string | null;
    payloadTruncated: boolean;
  }[];
}) {
  return (
    <section aria-labelledby="dead-letter-title" className="dead-letter-list">
      <h4 id="dead-letter-title">Recent dead-letter events</h4>
      {deadLetters.length === 0 ? (
        <p>No quarantined records for this run.</p>
      ) : null}
      {deadLetters.map((deadLetter) => (
        <details key={deadLetter.failureId}>
          <summary>
            {deadLetter.stage}: {deadLetter.safeMessage}
          </summary>
          <dl className="dead-letter-detail">
            <Metric label="Category" value={deadLetter.category} />
            <Metric label="Location" value={deadLetter.sourceLocation} />
            <Metric label="Retryability" value={deadLetter.retryability} />
            <Metric
              label="Captured"
              value={new Date(deadLetter.timestamp).toLocaleString()}
            />
          </dl>
          {deadLetter.payloadPreview !== null ? (
            <>
              <p className="form-hint">
                Safe {deadLetter.payloadEncoding} payload preview
                {deadLetter.payloadTruncated ? ' (truncated)' : ''}
              </p>
              <pre>{deadLetter.payloadPreview}</pre>
            </>
          ) : (
            <p className="form-hint">
              Payload capture was not enabled for this record.
            </p>
          )}
        </details>
      ))}
    </section>
  );
}

function formatNanos(value: number): string {
  if (value >= 1_000_000) return `${(value / 1_000_000).toFixed(2)} ms average`;
  if (value >= 1_000) return `${(value / 1_000).toFixed(2)} µs average`;
  return `${value} ns average`;
}
