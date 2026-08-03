import { useSearchParams } from 'react-router-dom';
import { exactIntegerText } from '../../api/controlPlaneClient';
import { useSchemas } from '../../api/queries';
import {
  EmptyState,
  ErrorState,
  LoadingState,
} from '../../components/AsyncState';
import { PageHeader } from '../../components/PageHeader';
import { Pagination } from '../../components/Pagination';

export function SchemaRegistryPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const page = pageFrom(searchParams.get('page'));
  const schemas = useSchemas(page);

  if (schemas.isPending)
    return <LoadingState title="Schema registry is loading" />;
  if (schemas.isError) {
    return (
      <ErrorState
        title="Schemas could not be loaded"
        onRetry={() => void schemas.refetch()}
      >
        {schemas.error.message}
      </ErrorState>
    );
  }
  if (schemas.data.items.length === 0) {
    return (
      <section className="page-content" aria-labelledby="page-title">
        <PageHeader eyebrow="Control plane" title="Schema Registry">
          Versioned JSON Schema definitions from the control plane.
        </PageHeader>
        <EmptyState title="No schemas yet">
          Schema editing is not implemented in the dashboard yet.
        </EmptyState>
      </section>
    );
  }

  return (
    <section className="page-content" aria-labelledby="page-title">
      <PageHeader eyebrow="Control plane" title="Schema Registry">
        Versioned JSON Schema definitions from the control plane.
      </PageHeader>
      <p aria-live="polite" className="result-summary">
        {exactIntegerText(schemas.data.totalItems)} schema
        {exactIntegerText(schemas.data.totalItems) === '1' ? '' : 's'} available
      </p>
      <div className="resource-grid">
        {schemas.data.items.map((schema) => (
          <article className="resource-card" key={schema.id}>
            <div className="resource-card__heading">
              <h3>{schema.name}</h3>
              <span
                className={
                  schema.archived
                    ? 'status-badge status-badge--muted'
                    : 'status-badge'
                }
              >
                {schema.archived ? 'Archived' : 'Active'}
              </span>
            </div>
            <p>{schema.description || 'No description provided.'}</p>
            <dl className="metadata-list">
              <div>
                <dt>Latest revision</dt>
                <dd>{schema.latestRevisionNumber}</dd>
              </div>
              <div>
                <dt>Updated</dt>
                <dd>{formatDate(schema.updatedAt)}</dd>
              </div>
            </dl>
          </article>
        ))}
      </div>
      <Pagination
        onPageChange={(nextPage) =>
          setSearchParams(nextPage === 0 ? {} : { page: String(nextPage + 1) })
        }
        page={page}
        totalPages={schemas.data.totalPages}
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
