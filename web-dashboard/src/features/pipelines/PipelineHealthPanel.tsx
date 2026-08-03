import { useEffect } from 'react';
import {
  exactIntegerBigInt,
  exactIntegerText,
  pipelineOutputDownloadUrl,
  type ExactInteger,
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
          value={exactIntegerText(snapshot?.latency.processedEvents ?? 0)}
        />
        <Metric
          label="Queue depth"
          value={exactIntegerText(snapshot?.queueDepth ?? 0)}
        />
        <Metric
          label="Sequence gaps"
          value={exactIntegerText(snapshot?.sequenceGapCount ?? 0)}
        />
        <Metric
          label="Duplicates"
          value={exactIntegerText(snapshot?.duplicateCount ?? 0)}
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
      {terminal && snapshot?.outputAvailable === true ? (
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
    received: ExactInteger;
    parsed: ExactInteger;
    emitted: ExactInteger;
    filtered: ExactInteger;
    failed: ExactInteger;
  };
}) {
  return (
    <div className="counter-grid" aria-label="Pipeline event counters">
      <Metric label="Received" value={exactIntegerText(counters.received)} />
      <Metric label="Parsed" value={exactIntegerText(counters.parsed)} />
      <Metric label="Emitted" value={exactIntegerText(counters.emitted)} />
      <Metric label="Filtered" value={exactIntegerText(counters.filtered)} />
      <Metric label="Failed" value={exactIntegerText(counters.failed)} />
    </div>
  );
}

function RateHistory({
  history,
}: {
  history: { received: ExactInteger; timestamp: string }[];
}) {
  const maximum = history.reduce((current, sample) => {
    const received = exactIntegerBigInt(sample.received);
    return received > current ? received : current;
  }, 1n);
  const latest = history.at(-1)?.received ?? 0;
  return (
    <section aria-labelledby="event-history-title" className="rate-history">
      <h4 id="event-history-title">Received-event history</h4>
      <p className="visually-hidden">
        {history.length} retained samples. Latest received count:{' '}
        {exactIntegerText(latest)}.
      </p>
      <div aria-hidden="true" className="rate-bars">
        {history.map((sample) => (
          <span
            className="rate-bars__bar"
            key={sample.timestamp}
            style={{
              height: `${Math.max(
                3,
                Number(
                  (exactIntegerBigInt(sample.received) * 10_000n) / maximum,
                ) / 100,
              )}%`,
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

function formatNanos(value: ExactInteger): string {
  const nanos = exactIntegerBigInt(value);
  if (nanos >= 1_000_000n)
    return `${formatRatio(nanos, 1_000_000n)} ms average`;
  if (nanos >= 1_000n) return `${formatRatio(nanos, 1_000n)} µs average`;
  return `${nanos} ns average`;
}

function formatRatio(value: bigint, divisor: bigint): string {
  const hundredths = (value * 100n) / divisor;
  return `${hundredths / 100n}.${String(hundredths % 100n).padStart(2, '0')}`;
}
