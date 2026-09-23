import { useEffect, useState } from 'react';
import { ArrowLeft, Play, Square } from 'lucide-react';
import { Link, useParams } from 'react-router-dom';
import {
  controlPlaneClient,
  type PipelineRun,
} from '../../api/controlPlaneClient';
import { useLatestPipelineRun, usePipeline } from '../../api/queries';
import {
  EmptyState,
  ErrorState,
  LoadingState,
} from '../../components/AsyncState';
import { PageHeader } from '../../components/PageHeader';
import { PipelineHealthPanel } from './PipelineHealthPanel';
import { ResourceStatusBadge } from '../../components/StatusBadge';

export function PipelineDetailPage() {
  const { pipelineId = '' } = useParams();
  const pipeline = usePipeline(pipelineId);
  const latestRun = useLatestPipelineRun(pipelineId);
  const [run, setRun] = useState<PipelineRun | null>(null);
  const [runError, setRunError] = useState<string | null>(null);
  const [actionPending, setActionPending] = useState(false);

  useEffect(() => {
    if (latestRun.data !== undefined) setRun(latestRun.data);
  }, [latestRun.data]);

  if (pipelineId.length === 0) {
    return (
      <EmptyState title="Pipeline unavailable">
        The requested pipeline ID is missing.
      </EmptyState>
    );
  }
  if (pipeline.isPending) return <LoadingState title="Pipeline is loading" />;
  if (pipeline.isError) {
    return (
      <ErrorState
        title="Pipeline could not be loaded"
        onRetry={() => void pipeline.refetch()}
      >
        {pipeline.error.message}
      </ErrorState>
    );
  }

  const definition = pipeline.data;
  return (
    <section className="page-content" aria-labelledby="page-title">
      <Link className="back-link" to="/pipelines">
        <ArrowLeft aria-hidden="true" size={16} /> Back to pipelines
      </Link>
      <PageHeader
        about={{
          purpose:
            'This page combines the saved definition, local run controls, live monitoring, failures, and downloadable artifacts.',
          prerequisites:
            'The configured input path must be readable by the local control plane.',
          outcome:
            'A terminal run retains its health record, finite output when available, and immutable raw capture.',
        }}
        eyebrow="Pipeline definition"
        title={definition.name}
      >
        {definition.description || 'No description provided.'}
      </PageHeader>
      <dl className="detail-list surface-panel">
        <div>
          <dt>Status</dt>
          <dd>
            <ResourceStatusBadge archived={definition.archived} />
          </dd>
        </div>
        <div>
          <dt>Metadata version</dt>
          <dd>{definition.version}</dd>
        </div>
        <div>
          <dt>Latest revision</dt>
          <dd>{definition.latestRevision.revisionNumber}</dd>
        </div>
        <div>
          <dt>Revision created</dt>
          <dd>{formatDate(definition.latestRevision.createdAt)}</dd>
        </div>
        <div>
          <dt>Last updated</dt>
          <dd>{formatDate(definition.updatedAt)}</dd>
        </div>
      </dl>
      <section aria-labelledby="run-controls-title" className="run-controls">
        <div className="section-heading">
          <div>
            <p className="eyebrow">Run controls</p>
            <h3 id="run-controls-title">Local execution</h3>
            <p>
              Start the latest immutable revision through the local runtime.
            </p>
          </div>
        </div>
        <div>
          <button
            className="button button--primary"
            disabled={
              definition.archived ||
              latestRun.isPending ||
              actionPending ||
              (run !== null && isActive(run.state))
            }
            onClick={() => void startPipeline()}
            type="button"
          >
            <Play aria-hidden="true" size={17} />
            {actionPending ? 'Working…' : 'Start pipeline'}
          </button>
          {run !== null && isActive(run.state) ? (
            <button
              className="button button--danger-secondary"
              disabled={actionPending}
              onClick={() => void stopPipeline()}
              type="button"
            >
              <Square aria-hidden="true" size={16} />
              Stop pipeline
            </button>
          ) : null}
        </div>
        {definition.archived ? (
          <p className="form-hint">Archived pipelines cannot be started.</p>
        ) : null}
        {latestRun.isError ? (
          <p role="alert">Latest run could not be restored.</p>
        ) : null}
        {runError ? <p role="alert">{runError}</p> : null}
        {run?.failureSummary ? (
          <div className="notice notice--error" role="alert">
            <strong>Latest run failed</strong>
            <p>{run.failureSummary}</p>
          </div>
        ) : null}
      </section>
      {run !== null ? (
        <PipelineHealthPanel
          onStateChange={(state) =>
            setRun((current) =>
              current === null ? current : { ...current, state },
            )
          }
          pipelineId={pipelineId}
          run={run}
        />
      ) : null}
    </section>
  );

  async function startPipeline() {
    setActionPending(true);
    setRunError(null);
    try {
      setRun(await controlPlaneClient.startPipeline(pipelineId));
    } catch (reason: unknown) {
      setRunError(
        reason instanceof Error
          ? reason.message
          : 'Pipeline could not be started.',
      );
    } finally {
      setActionPending(false);
    }
  }

  async function stopPipeline() {
    if (run === null) return;
    setActionPending(true);
    setRunError(null);
    try {
      setRun(await controlPlaneClient.stopPipeline(pipelineId, run.runId));
    } catch (reason: unknown) {
      setRunError(
        reason instanceof Error
          ? reason.message
          : 'Pipeline could not be stopped.',
      );
    } finally {
      setActionPending(false);
    }
  }
}

function isActive(state: PipelineRun['state']): boolean {
  return ['CREATED', 'VALIDATED', 'STARTING', 'RUNNING', 'STOPPING'].includes(
    state,
  );
}

function formatDate(value: string): string {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}
