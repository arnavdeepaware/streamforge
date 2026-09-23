import { useSearchParams } from 'react-router-dom';
import { BookOpen, LockKeyhole } from 'lucide-react';
import { exactIntegerText } from '../../api/controlPlaneClient';
import { useSchemas } from '../../api/queries';
import {
  EmptyState,
  ErrorState,
  LoadingState,
} from '../../components/AsyncState';
import { PageHeader } from '../../components/PageHeader';
import { Pagination } from '../../components/Pagination';
import { ResourceStatusBadge } from '../../components/StatusBadge';

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
        <PageHeader
          about={schemaRegistryHelp}
          eyebrow="Read-only in v1"
          title="Schema Registry"
        >
          Versioned JSON Schema definitions from the control plane. Editing is
          deferred until after v1.
        </PageHeader>
        <EmptyState title="No schemas yet">
          Schema editing is not implemented in the dashboard yet.
        </EmptyState>
      </section>
    );
  }

  return (
    <section className="page-content" aria-labelledby="page-title">
      <PageHeader
        about={schemaRegistryHelp}
        actions={
          <span className="read-only-badge">
            <LockKeyhole aria-hidden="true" size={15} /> Read-only
          </span>
        }
        eyebrow="Canonical contracts"
        title="Schema Registry"
      >
        Versioned JSON Schema definitions from the control plane. Editing is
        deferred until after v1.
      </PageHeader>
      <div className="notice notice--info">
        <BookOpen aria-hidden="true" size={19} />
        <div>
          <strong>How schemas support pipelines</strong>
          <p>
            Versioned schemas document the canonical event contracts used by
            validation and mapping. Editing is intentionally deferred beyond
            local v1.
          </p>
        </div>
      </div>
      <p aria-live="polite" className="result-summary">
        {exactIntegerText(schemas.data.totalItems)} schema
        {exactIntegerText(schemas.data.totalItems) === '1' ? '' : 's'} available
      </p>
      <div className="resource-grid">
        {schemas.data.items.map((schema) => (
          <article className="resource-card" key={schema.id}>
            <div className="resource-card__heading">
              <h3>{schema.name}</h3>
              <ResourceStatusBadge archived={schema.archived} />
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

const schemaRegistryHelp = {
  purpose:
    'The registry lists versioned canonical JSON Schema definitions exposed by the control plane.',
  outcome:
    'Use schema names and revision metadata to understand the contracts behind validation and field mapping.',
};

function pageFrom(value: string | null): number {
  const page = Number(value);
  return Number.isSafeInteger(page) && page > 0 ? page - 1 : 0;
}

function formatDate(value: string): string {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}
