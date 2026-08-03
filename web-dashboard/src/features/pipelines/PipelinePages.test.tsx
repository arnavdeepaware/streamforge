import { http, HttpResponse } from 'msw';
import { fireEvent, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { renderDashboard } from '../../test/renderDashboard';
import { server } from '../../test/server';

const pipeline = {
  id: '8fdc57d0-68b4-4729-b6cc-7034dd465819',
  name: 'US equities ingest',
  description: 'Normalizes STP traffic.',
  archived: false,
  latestRevisionNumber: 3,
  createdAt: '2026-08-03T12:00:00Z',
  updatedAt: '2026-08-03T13:00:00Z',
};

describe('pipeline dashboard pages', () => {
  it('renders a loading state before the pipeline list arrives', () => {
    server.use(
      http.get('*/api/v1/pipelines', async () => {
        await new Promise(() => undefined);
      }),
    );

    renderDashboard('/pipelines');

    expect(
      screen.getByRole('heading', { name: 'Pipelines is loading' }),
    ).toBeInTheDocument();
  });

  it('renders pipeline data from the control-plane API', async () => {
    server.use(
      http.get('*/api/v1/pipelines', () =>
        HttpResponse.json({
          items: [pipeline],
          page: 0,
          size: 20,
          totalItems: 1,
          totalPages: 1,
        }),
      ),
    );

    renderDashboard('/pipelines');

    expect(
      await screen.findByRole('link', { name: pipeline.name }),
    ).toHaveAttribute('href', `/pipelines/${pipeline.id}`);
    expect(screen.getByText('Latest revision')).toBeInTheDocument();
    expect(screen.getByText('Active')).toBeInTheDocument();
  });

  it('renders an empty state when the API has no pipelines', async () => {
    server.use(
      http.get('*/api/v1/pipelines', () =>
        HttpResponse.json({
          items: [],
          page: 0,
          size: 20,
          totalItems: 0,
          totalPages: 0,
        }),
      ),
    );

    renderDashboard('/pipelines');

    expect(
      await screen.findByRole('heading', { name: 'No pipelines yet' }),
    ).toBeInTheDocument();
  });

  it('renders an API failure with a retry control', async () => {
    server.use(
      http.get('*/api/v1/pipelines', () =>
        HttpResponse.json(
          { detail: 'The control-plane service is unavailable.' },
          { status: 503 },
        ),
      ),
    );

    renderDashboard('/pipelines');

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'The control-plane service is unavailable.',
    );
    expect(
      screen.getByRole('button', { name: 'Try again' }),
    ).toBeInTheDocument();
  });

  it('requests 20-item pages and exposes accessible previous and next controls', async () => {
    server.use(
      http.get('*/api/v1/pipelines', ({ request }) => {
        const page = Number(new URL(request.url).searchParams.get('page'));
        return HttpResponse.json({
          items: [
            { ...pipeline, id: `pipeline-${page}`, name: `Page ${page + 1}` },
          ],
          page,
          size: 20,
          totalItems: 41,
          totalPages: 3,
        });
      }),
    );

    renderDashboard('/pipelines?page=2');
    expect(
      await screen.findByRole('link', { name: 'Page 2' }),
    ).toBeInTheDocument();
    expect(screen.getByText('Page 2 of 3')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Previous' }));
    expect(
      await screen.findByRole('link', { name: 'Page 1' }),
    ).toBeInTheDocument();
  });

  it('renders a pipeline definition from the API', async () => {
    server.use(
      http.get(`*/api/v1/pipelines/${pipeline.id}`, () =>
        HttpResponse.json({
          ...pipeline,
          version: 2,
          latestRevision: {
            id: '441e3c21-5dcb-47ed-b049-79a7e1c54135',
            revisionNumber: 3,
            createdAt: '2026-08-03T13:00:00Z',
          },
        }),
      ),
      http.get(
        `*/api/v1/pipelines/${pipeline.id}/runs/latest`,
        () => new HttpResponse(null, { status: 204 }),
      ),
    );

    renderDashboard(`/pipelines/${pipeline.id}`);

    expect(await screen.findByText('Metadata version')).toBeInTheDocument();
    expect(
      screen.getByRole('heading', { name: pipeline.name }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: 'Start pipeline' }),
    ).toBeInTheDocument();
  });

  it('shows failed pipeline counters and the safe dead-letter detail', async () => {
    vi.stubGlobal(
      'EventSource',
      class {
        addEventListener() {}
        close() {}
      },
    );
    server.use(
      http.get(`*/api/v1/pipelines/${pipeline.id}`, () =>
        HttpResponse.json({
          ...pipeline,
          version: 2,
          latestRevision: {
            id: '441e3c21-5dcb-47ed-b049-79a7e1c54135',
            revisionNumber: 3,
            createdAt: '2026-08-03T13:00:00Z',
          },
        }),
      ),
      http.get(
        `*/api/v1/pipelines/${pipeline.id}/runs/latest`,
        () => new HttpResponse(null, { status: 204 }),
      ),
      http.post(`*/api/v1/pipelines/${pipeline.id}/runs`, () =>
        HttpResponse.json({
          runId: 'run-1',
          pipelineId: pipeline.id,
          revisionId: '441e3c21-5dcb-47ed-b049-79a7e1c54135',
          state: 'FAILED',
          failureSummary: 'output unavailable',
          startedAt: '2026-08-03T13:00:00Z',
          finishedAt: '2026-08-03T13:00:01Z',
        }),
      ),
      http.get(`*/api/v1/pipelines/${pipeline.id}/runs/run-1/monitoring`, () =>
        HttpResponse.json({
          runId: 'run-1',
          state: 'FAILED',
          counters: {
            received: 3,
            parsed: 2,
            emitted: 1,
            filtered: 0,
            failed: 1,
          },
          eventRatePerSecond: 2,
          latency: { totalNanos: 30, processedEvents: 2, averageNanos: 15 },
          queueDepth: 0,
          sequenceGapCount: 1,
          duplicateCount: 0,
          history: [],
          deadLetters: [
            {
              failureId: 'failure-1',
              stage: 'PARSE',
              category: 'MALFORMED_INPUT',
              sourceLocation: 'sample.stp:frame 2',
              safeMessage: 'invalid frame',
              retryability: 'NON_RETRYABLE',
              timestamp: '2026-08-03T13:00:00Z',
              payloadEncoding: 'base64',
              payloadPreview: 'AAE=',
              payloadTruncated: false,
            },
          ],
          outputAvailable: false,
        }),
      ),
    );

    renderDashboard(`/pipelines/${pipeline.id}`);
    await screen.findByRole('button', { name: 'Start pipeline' });
    screen.getByRole('button', { name: 'Start pipeline' }).click();

    expect(await screen.findByText('Pipeline health')).toBeInTheDocument();
    expect(screen.getByText('Sequence gaps')).toBeInTheDocument();
    expect(await screen.findByText('PARSE: invalid frame')).toBeInTheDocument();
    vi.unstubAllGlobals();
  });

  it('restores an externally completed run and its conditional download', async () => {
    vi.stubGlobal(
      'EventSource',
      class {
        addEventListener() {}
        close() {}
      },
    );
    const run = {
      runId: 'external-run',
      pipelineId: pipeline.id,
      revisionId: '441e3c21-5dcb-47ed-b049-79a7e1c54135',
      state: 'COMPLETED',
      failureSummary: null,
      startedAt: '2026-08-03T13:00:00Z',
      finishedAt: '2026-08-03T13:00:01Z',
    };
    server.use(
      http.get(`*/api/v1/pipelines/${pipeline.id}`, () =>
        HttpResponse.json({
          ...pipeline,
          version: 2,
          latestRevision: {
            id: run.revisionId,
            revisionNumber: 3,
            createdAt: '2026-08-03T13:00:00Z',
          },
        }),
      ),
      http.get(`*/api/v1/pipelines/${pipeline.id}/runs/latest`, () =>
        HttpResponse.json(run),
      ),
      http.get(
        `*/api/v1/pipelines/${pipeline.id}/runs/${run.runId}/monitoring`,
        () =>
          HttpResponse.json({
            runId: run.runId,
            state: 'COMPLETED',
            counters: {
              received: 10_001,
              parsed: 10_000,
              emitted: 10_000,
              filtered: 0,
              failed: 1,
            },
            eventRatePerSecond: 0,
            latency: { totalNanos: 1, processedEvents: 1, averageNanos: 1 },
            queueDepth: 0,
            sequenceGapCount: 0,
            duplicateCount: 0,
            history: [],
            deadLetters: [],
            outputAvailable: true,
          }),
      ),
    );

    renderDashboard(`/pipelines/${pipeline.id}`);

    expect(await screen.findByText('Pipeline health')).toBeInTheDocument();
    expect(
      await screen.findByRole('link', { name: 'Download finite output' }),
    ).toHaveAttribute(
      'href',
      `/api/v1/pipelines/${pipeline.id}/runs/${run.runId}/output`,
    );
    vi.unstubAllGlobals();
  });
});
