import { ArrowRight, Plus } from 'lucide-react';
import { Link, useSearchParams } from 'react-router-dom';
import { exactIntegerText } from '../../api/controlPlaneClient';
import { usePipelines } from '../../api/queries';
import {
  EmptyState,
  ErrorState,
  LoadingState,
} from '../../components/AsyncState';
import { PageHeader } from '../../components/PageHeader';
import { Pagination } from '../../components/Pagination';
import { ResourceStatusBadge } from '../../components/StatusBadge';

export function PipelineListPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const page = pageFrom(searchParams.get('page'));
  const pipelines = usePipelines(page);

  if (pipelines.isPending) return <LoadingState title="Pipelines is loading" />;
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
        <PageHeader
          about={{
            purpose:
              'Pipeline definitions describe the complete local normalization workflow and point to immutable revisions.',
            outcome:
              'Open a pipeline to run its latest revision and inspect its health and artifacts.',
          }}
          actions={
            <Link className="button button--primary" to="/pipelines/new">
              <Plus aria-hidden="true" size={18} />
              New pipeline
            </Link>
          }
          eyebrow="Control plane"
          title="Pipelines"
        >
          Pipeline definitions saved in the control plane appear here.
        </PageHeader>
        <EmptyState title="No pipelines yet">
          Create a pipeline with the guided editor to start processing local
          market data.
        </EmptyState>
      </section>
    );
  }

  return (
    <section className="page-content" aria-labelledby="page-title">
      <PageHeader
        about={{
          purpose:
            'Pipeline definitions describe input, transformations, output structure, and storage settings.',
          outcome:
            'Open any pipeline to run its latest immutable revision or inspect the most recent run.',
        }}
        actions={
          <Link className="button button--primary" to="/pipelines/new">
            <Plus aria-hidden="true" size={18} />
            New pipeline
          </Link>
        }
        eyebrow="Control plane"
        title="Pipelines"
      >
        Pipeline definitions, revision state, and archival status from the
        control plane.
      </PageHeader>
      <p aria-live="polite" className="result-summary">
        {exactIntegerText(pipelines.data.totalItems)} pipeline
        {exactIntegerText(pipelines.data.totalItems) === '1' ? '' : 's'}{' '}
        available
      </p>
      <div className="resource-grid">
        {pipelines.data.items.map((pipeline) => (
          <article className="resource-card" key={pipeline.id}>
            <div className="resource-card__heading">
              <h3>
                <Link to={`/pipelines/${pipeline.id}`}>{pipeline.name}</Link>
              </h3>
              <ResourceStatusBadge archived={pipeline.archived} />
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
            <Link
              aria-label={`Open ${pipeline.name}`}
              className="card-link"
              to={`/pipelines/${pipeline.id}`}
            >
              Open pipeline <ArrowRight aria-hidden="true" size={15} />
            </Link>
          </article>
        ))}
      </div>
      <Pagination
        onPageChange={(nextPage) =>
          setSearchParams(nextPage === 0 ? {} : { page: String(nextPage + 1) })
        }
        page={page}
        totalPages={pipelines.data.totalPages}
      />
    </section>
  );
}

function pageFrom(value: string | null): number {
  const page = Number(value);
  return Number.isSafeInteger(page) && page > 0 ? page - 1 : 0;
}

function formatDate(value: string): string {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}
