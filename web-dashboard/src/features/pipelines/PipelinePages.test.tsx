import { http, HttpResponse } from 'msw';
import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
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
    );

    renderDashboard(`/pipelines/${pipeline.id}`);

    expect(await screen.findByText('Metadata version')).toBeInTheDocument();
    expect(
      screen.getByRole('heading', { name: pipeline.name }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        'Viewing configuration contents and editing pipelines are not implemented in the dashboard yet.',
      ),
    ).toBeInTheDocument();
  });
});
