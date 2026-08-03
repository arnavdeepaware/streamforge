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

export type ApiFieldViolation = {
  field: string;
  message: string;
};

export type PipelineConfiguration = {
  input: JsonObject;
  transform: JsonObject;
  blueprint: JsonObject;
  output: JsonObject;
};

export type CreatePipelineRequest = {
  name: string;
  description: string;
  configuration: PipelineConfiguration;
};

export type PipelineValidationResult = {
  valid: boolean;
  errors: ApiFieldViolation[];
};

export type JsonValue =
  string | number | boolean | null | JsonObject | JsonValue[];

export type JsonObject = {
  [key: string]: JsonValue;
};

export class ControlPlaneApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
    readonly fieldErrors: ApiFieldViolation[] = [],
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
  validatePipeline: (configuration: PipelineConfiguration) =>
    post('/pipelines/validate', { configuration }, parseValidationResult),
  createPipeline: (request: CreatePipelineRequest) =>
    post('/pipelines', request, parsePipelineDefinition),
};

async function get<T>(path: string, parser: (value: unknown) => T): Promise<T> {
  return request(path, undefined, parser);
}

async function post<T>(
  path: string,
  body: unknown,
  parser: (value: unknown) => T,
): Promise<T> {
  return request(path, body, parser);
}

async function request<T>(
  path: string,
  body: unknown,
  parser: (value: unknown) => T,
): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`${apiBaseUrl}${path}`, {
      method: body === undefined ? 'GET' : 'POST',
      headers:
        body === undefined
          ? { Accept: 'application/json' }
          : { Accept: 'application/json', 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ControlPlaneApiError(
      'The control-plane API could not be reached.',
      0,
    );
  }

  const responseBody = await response.json().catch(() => undefined);
  if (!response.ok) {
    throw new ControlPlaneApiError(
      problemDetail(responseBody, response.statusText),
      response.status,
      problemFieldErrors(responseBody),
    );
  }

  try {
    return parser(responseBody);
  } catch {
    throw new ControlPlaneApiError(
      'The control-plane API returned an unexpected response.',
      response.status,
    );
  }
}

function parseValidationResult(value: unknown): PipelineValidationResult {
  const object = record(value);
  return {
    valid: boolean(object.valid),
    errors: array(object.errors).map(parseFieldViolation),
  };
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

function problemFieldErrors(value: unknown): ApiFieldViolation[] {
  if (!isRecord(value) || !Array.isArray(value.errors)) return [];
  return value.errors.flatMap((error) => {
    try {
      return [parseFieldViolation(error)];
    } catch {
      return [];
    }
  });
}

function parseFieldViolation(value: unknown): ApiFieldViolation {
  const object = record(value);
  return { field: string(object.field), message: string(object.message) };
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
