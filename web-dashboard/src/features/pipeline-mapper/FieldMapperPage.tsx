import { useEffect, useState } from 'react';
import type { JsonObject, PipelinePreview } from '../../api/controlPlaneClient';
import { controlPlaneClient } from '../../api/controlPlaneClient';
import { useCanonicalFields } from '../../api/queries';
import { ErrorState, LoadingState } from '../../components/AsyncState';
import { PageHeader } from '../../components/PageHeader';

type Mapping = {
  source: string;
  destination: string;
  format: 'NONE' | 'FIXED_DECIMAL_PLAIN' | 'TIMESTAMP_ISO_UTC';
};
const sample: JsonObject = {
  metadata: {
    eventId: 'c0676afdc91e20ff9e2d002343271ce62b63dbf2c9d59d27d23f59fd71a67072',
    schemaVersion: { major: 1, minor: 0 },
    source: 'jsonl/fixture-1',
    venue: 'XNAS',
    exchangeTimestamp: 1000000000,
    sequenceNumber: 1,
    rawEventReference: 'mapper:sample',
  },
  instrument: { symbol: 'AAPL' },
  payload: {
    type: 'ORDER_ADDED',
    orderId: 1001,
    side: 'BUY',
    quantity: 100,
    price: { mantissa: 12345, scale: 2 },
  },
};

export function FieldMapperPage() {
  const fields = useCanonicalFields();
  const [selected, setSelected] = useState('instrument.symbol');
  const [destination, setDestination] = useState('event.symbol');
  const [format, setFormat] = useState<Mapping['format']>('NONE');
  const [mappings, setMappings] = useState<Mapping[]>([
    {
      source: 'instrument.symbol',
      destination: 'event.symbol',
      format: 'NONE',
    },
  ]);
  const [constantPath, setConstantPath] = useState('event.source');
  const [constantValue, setConstantValue] = useState('streamforge');
  const [filterBuy, setFilterBuy] = useState(false);
  const [mapSide, setMapSide] = useState(false);
  const [preview, setPreview] = useState<PipelinePreview | null>(null);
  const [previewError, setPreviewError] = useState<string | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    const transform = buildTransform(filterBuy, mapSide);
    const blueprint = buildBlueprint(mappings, constantPath, constantValue);
    const timeout = window.setTimeout(() => {
      controlPlaneClient
        .previewPipeline(
          { sampleEvent: sample, transform, blueprint },
          controller.signal,
        )
        .then((result) => {
          setPreview(result);
          setPreviewError(null);
        })
        .catch((error: unknown) => {
          if (!(error instanceof DOMException && error.name === 'AbortError'))
            setPreviewError(
              error instanceof Error ? error.message : 'Preview failed.',
            );
        });
    }, 350);
    return () => {
      window.clearTimeout(timeout);
      controller.abort();
    };
  }, [constantPath, constantValue, filterBuy, mapSide, mappings]);

  if (fields.isPending) return <LoadingState title="Field mapper is loading" />;
  if (fields.isError)
    return (
      <ErrorState
        title="Canonical fields could not be loaded"
        onRetry={() => void fields.refetch()}
      >
        {fields.error.message}
      </ErrorState>
    );
  return (
    <section className="page-content mapper" aria-labelledby="page-title">
      <PageHeader eyebrow="Declarative mapping" title="Field Mapper">
        Build transformation and nested JSON output rules from canonical fields.
        The backend executes every preview.
      </PageHeader>
      <div className="mapper-grid">
        <section className="mapper-panel">
          <h3>Canonical fields</h3>
          <ul className="field-list">
            {fields.data.map((field) => (
              <li key={field.path}>
                <button
                  className={
                    selected === field.path
                      ? 'field-choice field-choice--selected'
                      : 'field-choice'
                  }
                  onClick={() => setSelected(field.path)}
                  type="button"
                >
                  {field.path}
                  <small>{field.type}</small>
                </button>
              </li>
            ))}
          </ul>
        </section>
        <section className="mapper-panel">
          <h3>Map output fields</h3>
          <label>
            Destination path
            <input
              aria-label="Destination path"
              onChange={(event) => setDestination(event.target.value)}
              value={destination}
            />
          </label>
          <label>
            Formatting
            <select
              aria-label="Formatting"
              onChange={(event) =>
                setFormat(event.target.value as Mapping['format'])
              }
              value={format}
            >
              <option value="NONE">No formatting</option>
              <option value="FIXED_DECIMAL_PLAIN">Fixed decimal</option>
              <option value="TIMESTAMP_ISO_UTC">ISO timestamp</option>
            </select>
          </label>
          <button
            onClick={() =>
              setMappings((current) => [
                ...current,
                { source: selected, destination, format },
              ])
            }
            type="button"
          >
            Add mapped field
          </button>
          <ul>
            {mappings.map((mapping, index) => (
              <li key={`${mapping.destination}-${index}`}>
                {mapping.destination} ← {mapping.source}{' '}
                <button
                  aria-label={`Remove ${mapping.destination}`}
                  onClick={() =>
                    setMappings((current) =>
                      current.filter(
                        (_, currentIndex) => currentIndex !== index,
                      ),
                    )
                  }
                  type="button"
                >
                  Remove
                </button>
              </li>
            ))}
          </ul>
          <h3>Constant and filter</h3>
          <label>
            Constant path
            <input
              aria-label="Constant path"
              onChange={(event) => setConstantPath(event.target.value)}
              value={constantPath}
            />
          </label>
          <label>
            Constant value
            <input
              aria-label="Constant value"
              onChange={(event) => setConstantValue(event.target.value)}
              value={constantValue}
            />
          </label>
          <label>
            <input
              checked={filterBuy}
              onChange={(event) => setFilterBuy(event.target.checked)}
              type="checkbox"
            />{' '}
            Keep BUY events only
          </label>
          <label>
            <input
              checked={mapSide}
              onChange={(event) => setMapSide(event.target.checked)}
              type="checkbox"
            />{' '}
            Normalize side enum values
          </label>
          <p className="form-hint">
            Nested destination paths create nested output objects. Enum maps and
            safe filters are validated by the backend preview.
          </p>
        </section>
        <PreviewPanel error={previewError} preview={preview} />
      </div>
    </section>
  );
}

function PreviewPanel({
  preview,
  error,
}: {
  preview: PipelinePreview | null;
  error: string | null;
}) {
  return (
    <section className="mapper-panel">
      <h3>Server preview</h3>
      {error ? <p role="alert">{error}</p> : null}
      {preview === null ? (
        <p aria-live="polite">Waiting to preview changes…</p>
      ) : (
        <>
          <p className="status-message" role="status">
            {preview.status}
          </p>
          <Json title="Input" value={preview.input} />
          <Json title="Transformed" value={preview.transformed} />
          <Json title="Output" value={preview.output} />
          {preview.errors.map((issue) => (
            <p
              className="field-error"
              key={`${issue.field}-${issue.message}`}
              role="alert"
            >
              {issue.field}: {issue.message}
            </p>
          ))}
        </>
      )}
    </section>
  );
}
function Json({ title, value }: { title: string; value: JsonObject | null }) {
  return (
    <>
      <h4>{title}</h4>
      <pre>{value === null ? '—' : JSON.stringify(value, null, 2)}</pre>
    </>
  );
}
function buildTransform(filterBuy: boolean, mapSide: boolean): JsonObject {
  const operations: JsonObject[] = [
    {
      op: 'add_constant',
      path: 'pipelineLabel',
      value: { type: 'STRING', value: 'mapper' },
    },
  ];
  if (filterBuy)
    operations.push({
      op: 'filter',
      condition: {
        type: 'comparison',
        field: 'payload.side',
        operator: 'EQ',
        value: { type: 'ENUM', value: 'BUY' },
      },
    });
  if (mapSide)
    operations.push({
      op: 'enum_map',
      path: 'payload.side',
      mapping: { BUY: 'BUY', SELL: 'SELL' },
    });
  return { schemaVersion: '1.0', operations };
}
function buildBlueprint(
  mappings: Mapping[],
  constantPath: string,
  constantValue: string,
): JsonObject {
  const fields: Record<string, JsonObject> = {};
  for (const mapping of mappings)
    add(
      fields,
      mapping.destination.split('.'),
      mapping.format === 'NONE'
        ? { kind: 'reference', source: 'canonical', path: mapping.source }
        : {
            kind: 'format',
            source: 'canonical',
            path: mapping.source,
            format: mapping.format,
          },
    );
  if (constantPath)
    add(fields, constantPath.split('.'), {
      kind: 'literal',
      value: constantValue,
    });
  return { schemaVersion: '1.0', output: { kind: 'object', fields } };
}
function add(
  fields: Record<string, JsonObject>,
  parts: string[],
  value: JsonObject,
): void {
  const [head, ...tail] = parts;
  if (!head || !/^[A-Za-z][A-Za-z0-9_]*$/.test(head)) return;
  if (tail.length === 0) {
    fields[head] = value;
    return;
  }
  const current = fields[head];
  if (
    !current ||
    current.kind !== 'object' ||
    typeof current.fields !== 'object' ||
    current.fields === null ||
    Array.isArray(current.fields)
  )
    fields[head] = { kind: 'object', fields: {} };
  add(fields[head].fields as Record<string, JsonObject>, tail, value);
}
