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

export type CanonicalField = {
  path: string;
  type: string;
  protectedField: boolean;
};
export type PipelinePreview = {
  status: string;
  input: JsonObject | null;
  transformed: JsonObject | null;
  output: JsonObject | null;
  errors: ApiFieldViolation[];
};

export type PipelineRun = {
  runId: string;
  pipelineId: string;
  revisionId: string;
  state: PipelineRunState;
  failureSummary: string | null;
  startedAt: string | null;
  finishedAt: string | null;
};

export type PipelineRunState =
  | 'CREATED'
  | 'VALIDATED'
  | 'STARTING'
  | 'RUNNING'
  | 'STOPPING'
  | 'STOPPED'
  | 'COMPLETED'
  | 'FAILED';

export type MetricSample = {
  timestamp: string;
  received: number;
  emitted: number;
  failed: number;
};

export type DeadLetter = {
  failureId: string;
  stage: string;
  category: string;
  sourceLocation: string;
  safeMessage: string;
  retryability: string;
  timestamp: string;
  payloadEncoding: string | null;
  payloadPreview: string | null;
  payloadTruncated: boolean;
};

export type PipelineMonitoring = {
  runId: string;
  state: PipelineRunState;
  counters: {
    received: number;
    parsed: number;
    emitted: number;
    filtered: number;
    failed: number;
  };
  eventRatePerSecond: number;
  latency: {
    totalNanos: number;
    processedEvents: number;
    averageNanos: number;
  };
  queueDepth: number;
  sequenceGapCount: number;
  duplicateCount: number;
  history: MetricSample[];
  deadLetters: DeadLetter[];
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
  startPipeline: (pipelineId: string) =>
    post(
      `/pipelines/${encodeURIComponent(pipelineId)}/runs`,
      {},
      parsePipelineRun,
    ),
  stopPipeline: (pipelineId: string, runId: string) =>
    post(
      `/pipelines/${encodeURIComponent(pipelineId)}/runs/${encodeURIComponent(runId)}/stop`,
      {},
      parsePipelineRun,
    ),
  getPipelineMonitoring: (pipelineId: string, runId: string) =>
    get(
      `/pipelines/${encodeURIComponent(pipelineId)}/runs/${encodeURIComponent(runId)}/monitoring`,
      parsePipelineMonitoring,
    ),
  getDeadLetter: (pipelineId: string, runId: string, failureId: string) =>
    get(
      `/pipelines/${encodeURIComponent(pipelineId)}/runs/${encodeURIComponent(runId)}/dead-letters/${encodeURIComponent(failureId)}`,
      parseDeadLetter,
    ),
  canonicalFields: () =>
    get('/pipelines/preview/canonical-fields', parseCanonicalFields),
  previewPipeline: (body: JsonObject, signal: AbortSignal) =>
    post('/pipelines/preview', body, parsePreview, signal),
};

export function pipelineMonitoringEventsUrl(
  pipelineId: string,
  runId: string,
): string {
  return `${apiBaseUrl}/pipelines/${encodeURIComponent(pipelineId)}/runs/${encodeURIComponent(runId)}/events`;
}

export function pipelineOutputDownloadUrl(
  pipelineId: string,
  runId: string,
): string {
  return `${apiBaseUrl}/pipelines/${encodeURIComponent(pipelineId)}/runs/${encodeURIComponent(runId)}/output`;
}

async function get<T>(path: string, parser: (value: unknown) => T): Promise<T> {
  return request(path, undefined, parser);
}

async function post<T>(
  path: string,
  body: unknown,
  parser: (value: unknown) => T,
  signal?: AbortSignal,
): Promise<T> {
  return request(path, body, parser, signal);
}

async function request<T>(
  path: string,
  body: unknown,
  parser: (value: unknown) => T,
  signal?: AbortSignal,
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
      signal,
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

function parseCanonicalFields(value: unknown): CanonicalField[] {
  return array(value).map((item) => {
    const object = record(item);
    return {
      path: string(object.path),
      type: string(object.type),
      protectedField: boolean(object.protectedField),
    };
  });
}

function parsePreview(value: unknown): PipelinePreview {
  const object = record(value);
  return {
    status: string(object.status),
    input: nullableObject(object.input),
    transformed: nullableObject(object.transformed),
    output: nullableObject(object.output),
    errors: array(object.errors).map(parseFieldViolation),
  };
}

function parsePipelineRun(value: unknown): PipelineRun {
  const object = record(value);
  return {
    runId: string(object.runId),
    pipelineId: string(object.pipelineId),
    revisionId: string(object.revisionId),
    state: pipelineRunState(object.state),
    failureSummary: nullableString(object.failureSummary),
    startedAt: nullableString(object.startedAt),
    finishedAt: nullableString(object.finishedAt),
  };
}

export function parsePipelineMonitoring(value: unknown): PipelineMonitoring {
  const object = record(value);
  const counters = record(object.counters);
  const latency = record(object.latency);
  return {
    runId: string(object.runId),
    state: pipelineRunState(object.state),
    counters: {
      received: number(counters.received),
      parsed: number(counters.parsed),
      emitted: number(counters.emitted),
      filtered: number(counters.filtered),
      failed: number(counters.failed),
    },
    eventRatePerSecond: number(object.eventRatePerSecond),
    latency: {
      totalNanos: number(latency.totalNanos),
      processedEvents: number(latency.processedEvents),
      averageNanos: number(latency.averageNanos),
    },
    queueDepth: number(object.queueDepth),
    sequenceGapCount: number(object.sequenceGapCount),
    duplicateCount: number(object.duplicateCount),
    history: array(object.history).map(parseMetricSample),
    deadLetters: array(object.deadLetters).map(parseDeadLetter),
  };
}

function parseMetricSample(value: unknown): MetricSample {
  const object = record(value);
  return {
    timestamp: string(object.timestamp),
    received: number(object.received),
    emitted: number(object.emitted),
    failed: number(object.failed),
  };
}

function parseDeadLetter(value: unknown): DeadLetter {
  const object = record(value);
  return {
    failureId: string(object.failureId),
    stage: string(object.stage),
    category: string(object.category),
    sourceLocation: string(object.sourceLocation),
    safeMessage: string(object.safeMessage),
    retryability: string(object.retryability),
    timestamp: string(object.timestamp),
    payloadEncoding: nullableString(object.payloadEncoding),
    payloadPreview: nullableString(object.payloadPreview),
    payloadTruncated: boolean(object.payloadTruncated),
  };
}

function nullableObject(value: unknown): JsonObject | null {
  if (value === null) return null;
  return record(value) as JsonObject;
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

function nullableString(value: unknown): string | null {
  return value === null ? null : string(value);
}

function pipelineRunState(value: unknown): PipelineRunState {
  const state = string(value);
  if (
    ![
      'CREATED',
      'VALIDATED',
      'STARTING',
      'RUNNING',
      'STOPPING',
      'STOPPED',
      'COMPLETED',
      'FAILED',
    ].includes(state)
  )
    throw new TypeError('Expected a pipeline run state.');
  return state as PipelineRunState;
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
