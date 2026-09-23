import { useEffect } from 'react';
import {
  Activity,
  AlertTriangle,
  Clock3,
  Download,
  FileArchive,
  Gauge,
  Inbox,
  Radio,
} from 'lucide-react';
import {
  exactIntegerBigInt,
  exactIntegerText,
  pipelineOutputDownloadUrl,
  pipelineRawCaptureDownloadUrl,
  type ExactInteger,
  type PipelineRun,
  type PipelineRunState,
} from '../../api/controlPlaneClient';
import { usePipelineMonitoring } from './usePipelineMonitoring';
import { RunStatusBadge } from '../../components/StatusBadge';

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
          <p>
            Follow lifecycle, throughput, latency, data quality, and retained
            run artifacts.
          </p>
        </div>
        <p aria-live="polite" className="connection-state">
          <Radio aria-hidden="true" size={14} />
          {monitoring.connection === 'reconnecting'
            ? 'Reconnecting to metrics…'
            : `Metrics ${monitoring.connection}`}
        </p>
      </div>
      <details className="about-disclosure about-disclosure--compact">
        <summary>How to read these metrics</summary>
        <p>
          Counters show how records move through the pipeline. Sequence gaps and
          duplicates indicate source-order anomalies; dead letters explain
          records that could not complete processing.
        </p>
      </details>
      <dl className="health-summary">
        <div className="metric-card metric-card--state">
          <dt>Lifecycle</dt>
          <dd>
            <RunStatusBadge state={state} />
          </dd>
        </div>
        <Metric
          icon={Gauge}
          label="Event rate"
          value={`${snapshot?.eventRatePerSecond ?? 0} events/s`}
        />
        <Metric
          icon={Clock3}
          label="Latency"
          value={formatNanos(snapshot?.latency.averageNanos ?? 0)}
        />
        <Metric
          icon={Activity}
          label="Timed events"
          value={exactIntegerText(snapshot?.latency.processedEvents ?? 0)}
        />
        <Metric
          icon={Inbox}
          label="Queue depth"
          value={exactIntegerText(snapshot?.queueDepth ?? 0)}
        />
        <Metric
          icon={AlertTriangle}
          label="Sequence gaps"
          value={exactIntegerText(snapshot?.sequenceGapCount ?? 0)}
        />
        <Metric
          icon={AlertTriangle}
          label="Duplicates"
          value={exactIntegerText(snapshot?.duplicateCount ?? 0)}
        />
      </dl>
      {monitoring.error ? <p role="alert">{monitoring.error}</p> : null}
      <section className="health-section" aria-labelledby="throughput-title">
        <div className="section-heading section-heading--compact">
          <div>
            <p className="eyebrow">Record flow</p>
            <h4 id="throughput-title">Throughput</h4>
          </div>
        </div>
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
      </section>
      <DeadLetters deadLetters={snapshot?.deadLetters ?? []} />
      {terminal ? (
        <section
          className="health-section artifacts"
          aria-labelledby="artifacts-title"
        >
          <div className="section-heading section-heading--compact">
            <div>
              <p className="eyebrow">Retained files</p>
              <h4 id="artifacts-title">Run artifacts</h4>
            </div>
          </div>
          <p className="section-copy">
            Download the normalized output and byte-for-byte source capture
            retained for this run.
          </p>
          <div className="artifact-grid">
            {snapshot?.outputAvailable === true ? (
              <a
                aria-label="Download finite output"
                className="artifact-card"
                href={pipelineOutputDownloadUrl(pipelineId, run.runId)}
              >
                <Download aria-hidden="true" size={20} />
                <span>
                  <strong>Finite output</strong>
                  <small>Normalized pipeline result</small>
                </span>
              </a>
            ) : null}
            {snapshot?.rawCaptureAvailable === true ? (
              <a
                aria-label="Download immutable raw capture"
                className="artifact-card"
                download={snapshot.rawCaptureFilename ?? 'raw-input.capture'}
                href={pipelineRawCaptureDownloadUrl(pipelineId, run.runId)}
              >
                <FileArchive aria-hidden="true" size={20} />
                <span>
                  <strong>Immutable raw capture</strong>
                  <small>{snapshot.rawCaptureFilename ?? 'Raw input'}</small>
                </span>
              </a>
            ) : null}
            {snapshot?.outputAvailable !== true &&
            snapshot?.rawCaptureAvailable !== true ? (
              <p className="form-hint">
                No downloadable artifacts are available.
              </p>
            ) : null}
          </div>
        </section>
      ) : null}
    </section>
  );
}

function Metric({
  label,
  value,
  icon: Icon,
}: {
  label: string;
  value: string;
  icon?: typeof Activity;
}) {
  return (
    <div className="metric-card">
      <dt>
        {Icon ? <Icon aria-hidden="true" size={15} /> : null}
        {label}
      </dt>
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
    <dl className="counter-grid" aria-label="Pipeline event counters">
      <Metric label="Received" value={exactIntegerText(counters.received)} />
      <Metric label="Parsed" value={exactIntegerText(counters.parsed)} />
      <Metric label="Emitted" value={exactIntegerText(counters.emitted)} />
      <Metric label="Filtered" value={exactIntegerText(counters.filtered)} />
      <Metric label="Failed" value={exactIntegerText(counters.failed)} />
    </dl>
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
      <div aria-hidden="true" className="rate-history__axis">
        <span>Earlier</span>
        <span>Latest · {exactIntegerText(latest)} received</span>
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
    <section
      aria-labelledby="dead-letter-title"
      className="health-section dead-letter-list"
    >
      <div className="section-heading section-heading--compact">
        <div>
          <p className="eyebrow">Exceptions</p>
          <h4 id="dead-letter-title">Recent dead-letter events</h4>
        </div>
      </div>
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
