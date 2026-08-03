import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
  controlPlaneClient,
  type PipelineRun,
} from '../../api/controlPlaneClient';
import { usePipeline } from '../../api/queries';
import {
  EmptyState,
  ErrorState,
  LoadingState,
} from '../../components/AsyncState';
import { PageHeader } from '../../components/PageHeader';
import { PipelineHealthPanel } from './PipelineHealthPanel';

export function PipelineDetailPage() {
  const { pipelineId = '' } = useParams();
  const pipeline = usePipeline(pipelineId);
  const [run, setRun] = useState<PipelineRun | null>(null);
  const [runError, setRunError] = useState<string | null>(null);
  const [actionPending, setActionPending] = useState(false);

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
        Back to pipelines
      </Link>
      <PageHeader eyebrow="Pipeline definition" title={definition.name}>
        {definition.description || 'No description provided.'}
      </PageHeader>
      <dl className="detail-list">
        <div>
          <dt>Status</dt>
          <dd>{definition.archived ? 'Archived' : 'Active'}</dd>
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
        <h3 id="run-controls-title">Local execution</h3>
        <p>
          Starts the latest immutable revision through the control-plane local
          runtime.
        </p>
        <div>
          <button
            disabled={actionPending || (run !== null && isActive(run.state))}
            onClick={() => void startPipeline()}
            type="button"
          >
            {actionPending ? 'Working…' : 'Start pipeline'}
          </button>
          {run !== null && isActive(run.state) ? (
            <button
              disabled={actionPending}
              onClick={() => void stopPipeline()}
              type="button"
            >
              Stop pipeline
            </button>
          ) : null}
        </div>
        {runError ? <p role="alert">{runError}</p> : null}
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
