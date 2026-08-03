import { Link } from 'react-router-dom';
import { usePipelines } from '../../api/queries';
import {
  EmptyState,
  ErrorState,
  LoadingState,
} from '../../components/AsyncState';
import { PageHeader } from '../../components/PageHeader';

type PipelineListPageProps = {
  compact?: boolean;
};

export function PipelineListPage({ compact = false }: PipelineListPageProps) {
  const pipelines = usePipelines();
  const title = compact ? 'Dashboard' : 'Pipelines';

  if (pipelines.isPending)
    return <LoadingState title={`${title} is loading`} />;
  if (pipelines.isError) {
    return (
      <ErrorState
        title="Pipelines could not be loaded"
        onRetry={() => void pipelines.refetch()}
      >
        {pipelines.error.message}
      </ErrorState>
    );
  }

  if (pipelines.data.items.length === 0) {
    return (
      <section className="page-content" aria-labelledby="page-title">
        <PageHeader eyebrow="Control plane" title={title}>
          Pipeline definitions saved in the control plane appear here.
        </PageHeader>
        <EmptyState title="No pipelines yet">
          Create pipeline definitions through the control-plane API. The
          graphical editor is not implemented yet.
        </EmptyState>
      </section>
    );
  }

  return (
    <section className="page-content" aria-labelledby="page-title">
      <PageHeader eyebrow="Control plane" title={title}>
        {compact
          ? 'A live view of pipeline definitions available through the control plane.'
          : 'Pipeline definitions, revision state, and archival status from the control plane.'}
      </PageHeader>
      <p aria-live="polite" className="result-summary">
        {pipelines.data.totalItems} pipeline
        {pipelines.data.totalItems === 1 ? '' : 's'} available
      </p>
      <div className="resource-grid">
        {pipelines.data.items.map((pipeline) => (
          <article className="resource-card" key={pipeline.id}>
            <div className="resource-card__heading">
              <h3>
                <Link to={`/pipelines/${pipeline.id}`}>{pipeline.name}</Link>
              </h3>
              <StatusBadge archived={pipeline.archived} />
            </div>
            <p>{pipeline.description || 'No description provided.'}</p>
            <dl className="metadata-list">
              <div>
                <dt>Latest revision</dt>
                <dd>{pipeline.latestRevisionNumber}</dd>
              </div>
              <div>
                <dt>Updated</dt>
                <dd>{formatDate(pipeline.updatedAt)}</dd>
              </div>
            </dl>
          </article>
        ))}
      </div>
    </section>
  );
}

function StatusBadge({ archived }: { archived: boolean }) {
  return (
    <span
      className={archived ? 'status-badge status-badge--muted' : 'status-badge'}
    >
      {archived ? 'Archived' : 'Active'}
    </span>
  );
}

function formatDate(value: string): string {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}
