import { useSchemas } from '../../api/queries';
import {
  EmptyState,
  ErrorState,
  LoadingState,
} from '../../components/AsyncState';
import { PageHeader } from '../../components/PageHeader';

export function SchemaRegistryPage() {
  const schemas = useSchemas();

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
        {schemas.data.totalItems} schema
        {schemas.data.totalItems === 1 ? '' : 's'} available
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
    </section>
  );
}

function formatDate(value: string): string {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}
