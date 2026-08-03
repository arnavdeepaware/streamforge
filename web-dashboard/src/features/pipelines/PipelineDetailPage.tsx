import { Link, useParams } from 'react-router-dom';
import { usePipeline } from '../../api/queries';
import {
  EmptyState,
  ErrorState,
  LoadingState,
} from '../../components/AsyncState';
import { PageHeader } from '../../components/PageHeader';

export function PipelineDetailPage() {
  const { pipelineId = '' } = useParams();
  const pipeline = usePipeline(pipelineId);

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
      <p className="notice">
        Viewing configuration contents and editing pipelines are not implemented
        in the dashboard yet.
      </p>
    </section>
  );
}

function formatDate(value: string): string {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}
