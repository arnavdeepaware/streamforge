export type PageResponse<T> = {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
};

export type PipelineSummary = {
  id: string;
  name: string;
  description: string;
  archived: boolean;
  latestRevisionNumber: number;
  createdAt: string;
  updatedAt: string;
};

export type PipelineDefinition = Omit<
  PipelineSummary,
  'latestRevisionNumber'
> & {
  version: number;
  latestRevision: {
    id: string;
    revisionNumber: number;
    createdAt: string;
  };
};

export type SchemaSummary = {
  id: string;
  name: string;
  description: string;
  archived: boolean;
  latestRevisionNumber: number;
  createdAt: string;
  updatedAt: string;
};

export class ControlPlaneApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message);
    this.name = 'ControlPlaneApiError';
  }
}

const configuredApiUrl = import.meta.env.VITE_CONTROL_PLANE_API_URL?.trim();
const apiBaseUrl = (configuredApiUrl || '/api/v1').replace(/\/$/, '');

export const controlPlaneClient = {
  listPipelines: () => get('/pipelines', parsePipelinePage),
  getPipeline: (pipelineId: string) =>
    get(
      `/pipelines/${encodeURIComponent(pipelineId)}`,
      parsePipelineDefinition,
    ),
  listSchemas: () => get('/schemas', parseSchemaPage),
};

async function get<T>(path: string, parser: (value: unknown) => T): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`${apiBaseUrl}${path}`, {
      headers: { Accept: 'application/json' },
    });
  } catch {
    throw new ControlPlaneApiError(
      'The control-plane API could not be reached.',
      0,
    );
  }

  const body = await response.json().catch(() => undefined);
  if (!response.ok) {
    throw new ControlPlaneApiError(
      problemDetail(body, response.statusText),
      response.status,
    );
  }

  try {
    return parser(body);
  } catch {
    throw new ControlPlaneApiError(
      'The control-plane API returned an unexpected response.',
      response.status,
    );
  }
}

function parsePipelinePage(value: unknown): PageResponse<PipelineSummary> {
  return parsePage(value, parsePipelineSummary);
}

function parseSchemaPage(value: unknown): PageResponse<SchemaSummary> {
  return parsePage(value, parseSchemaSummary);
}

function parsePage<T>(
  value: unknown,
  parseItem: (item: unknown) => T,
): PageResponse<T> {
  const object = record(value);
  const items = array(object.items).map(parseItem);
  return {
    items,
    page: number(object.page),
    size: number(object.size),
    totalItems: number(object.totalItems),
    totalPages: number(object.totalPages),
  };
}

function parsePipelineSummary(value: unknown): PipelineSummary {
  const object = record(value);
  return {
    id: string(object.id),
    name: string(object.name),
    description: string(object.description),
    archived: boolean(object.archived),
    latestRevisionNumber: number(object.latestRevisionNumber),
    createdAt: string(object.createdAt),
    updatedAt: string(object.updatedAt),
  };
}

function parsePipelineDefinition(value: unknown): PipelineDefinition {
  const object = record(value);
  const revision = record(object.latestRevision);
  return {
    id: string(object.id),
    name: string(object.name),
    description: string(object.description),
    archived: boolean(object.archived),
    version: number(object.version),
    createdAt: string(object.createdAt),
    updatedAt: string(object.updatedAt),
    latestRevision: {
      id: string(revision.id),
      revisionNumber: number(revision.revisionNumber),
      createdAt: string(revision.createdAt),
    },
  };
}

function parseSchemaSummary(value: unknown): SchemaSummary {
  const object = record(value);
  return {
    id: string(object.id),
    name: string(object.name),
    description: string(object.description),
    archived: boolean(object.archived),
    latestRevisionNumber: number(object.latestRevisionNumber),
    createdAt: string(object.createdAt),
    updatedAt: string(object.updatedAt),
  };
}

function problemDetail(value: unknown, fallback: string): string {
  if (isRecord(value) && typeof value.detail === 'string') return value.detail;
  return fallback || 'The control-plane API rejected this request.';
}

function record(value: unknown): Record<string, unknown> {
  if (!isRecord(value)) throw new TypeError('Expected an object.');
  return value;
}

function array(value: unknown): unknown[] {
  if (!Array.isArray(value)) throw new TypeError('Expected an array.');
  return value;
}

function string(value: unknown): string {
  if (typeof value !== 'string') throw new TypeError('Expected a string.');
  return value;
}

function number(value: unknown): number {
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    throw new TypeError('Expected a finite number.');
  }
  return value;
}

function boolean(value: unknown): boolean {
  if (typeof value !== 'boolean') throw new TypeError('Expected a boolean.');
  return value;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
