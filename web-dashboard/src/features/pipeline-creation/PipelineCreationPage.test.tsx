import { fireEvent, screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { renderDashboard } from '../../test/renderDashboard';
import { server } from '../../test/server';

const pipelineId = 'a2e5f0c8-7f56-4f38-b02f-f8ef5820f1c9';

describe('pipeline creation wizard', () => {
  it('creates an STP-to-JSONL pipeline and returns to the pipeline list', async () => {
    server.use(
      http.post('*/api/v1/pipelines/validate', async ({ request }) => {
        const requestBody = await request.json();
        expect(requestBody).toMatchObject({
          configuration: {
            input: { type: 'STP_BINARY' },
            output: { type: 'JSONL' },
          },
        });
        return HttpResponse.json({ valid: true, errors: [] });
      }),
      http.post('*/api/v1/pipelines', async ({ request }) => {
        const requestBody = await request.json();
        expect(requestBody).toMatchObject({ name: 'AAPL STP feed' });
        return HttpResponse.json(createdPipeline());
      }),
      http.get('*/api/v1/pipelines', () =>
        HttpResponse.json({
          items: [pipelineSummary()],
          page: 0,
          size: 20,
          totalItems: 1,
          totalPages: 1,
        }),
      ),
    );

    renderDashboard('/pipelines/new');

    fireEvent.click(screen.getByRole('button', { name: 'Continue' }));
    fireEvent.change(screen.getByLabelText('STP file path'), {
      target: { value: 'fixtures/aapl.stp' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Continue' }));
    fireEvent.click(screen.getByLabelText('Rename symbol to ticker'));
    fireEvent.click(screen.getByRole('button', { name: 'Continue' }));
    fireEvent.click(screen.getByRole('button', { name: 'Continue' }));
    fireEvent.change(screen.getByLabelText('JSONL output path'), {
      target: { value: 'output/aapl.jsonl' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Continue' }));
    fireEvent.click(
      screen.getByRole('button', { name: 'Validate configuration' }),
    );
    expect(
      await screen.findByText('Configuration is valid and ready to save.'),
    ).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Continue' }));
    fireEvent.change(screen.getByLabelText('Pipeline name'), {
      target: { value: 'AAPL STP feed' },
    });
    fireEvent.change(screen.getByLabelText('Description'), {
      target: { value: 'A local STP binary to JSONL feed.' },
    });
    fireEvent.click(
      screen.getByRole('button', { name: 'Save pipeline definition' }),
    );

    expect(
      await screen.findByRole('link', { name: 'AAPL STP feed' }),
    ).toHaveAttribute('href', `/pipelines/${pipelineId}`);
  });

  it('moves to the input step and displays backend validation beside the invalid field', async () => {
    server.use(
      http.post('*/api/v1/pipelines/validate', () =>
        HttpResponse.json(
          {
            detail: 'Request validation failed',
            errors: [
              {
                field: 'configuration.input.path',
                message: 'must point to a readable STP file',
              },
            ],
          },
          { status: 400 },
        ),
      ),
    );

    renderDashboard('/pipelines/new');

    moveToReview();
    fireEvent.click(
      screen.getByRole('button', { name: 'Validate configuration' }),
    );

    expect(
      await screen.findByRole('heading', { name: 'Configure input' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent(
      'must point to a readable STP file',
    );
  });

  it('displays save-time backend validation beside pipeline metadata', async () => {
    server.use(
      http.post('*/api/v1/pipelines', () =>
        HttpResponse.json(
          {
            detail: 'Request validation failed',
            errors: [{ field: 'name', message: 'is required' }],
          },
          { status: 400 },
        ),
      ),
    );

    renderDashboard('/pipelines/new');

    moveToSave();
    fireEvent.click(
      screen.getByRole('button', { name: 'Save pipeline definition' }),
    );

    expect(await screen.findByRole('alert')).toHaveTextContent('is required');
    expect(
      screen.getByRole('textbox', { name: 'Pipeline name' }),
    ).toHaveAttribute('aria-invalid', 'true');
  });

  it('imports a guided configuration without losing the draft across steps', () => {
    renderDashboard('/pipelines/new');

    moveToReview();
    const preview = screen.getByLabelText('Pipeline configuration preview');
    fireEvent.change(screen.getByLabelText('Import JSON configuration'), {
      target: {
        value: (preview.textContent ?? '').replace(
          'fixtures/ticks.stp',
          'fixtures/imported.stp',
        ),
      },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Import JSON' }));
    fireEvent.click(screen.getByRole('button', { name: /2.*Configure input/ }));

    expect(screen.getByLabelText('STP file path')).toHaveValue(
      'fixtures/imported.stp',
    );
  });
});

function moveToReview() {
  for (let index = 0; index < 5; index += 1) {
    fireEvent.click(screen.getByRole('button', { name: 'Continue' }));
  }
}

function moveToSave() {
  for (let index = 0; index < 6; index += 1) {
    fireEvent.click(screen.getByRole('button', { name: 'Continue' }));
  }
}

function pipelineSummary() {
  return {
    id: pipelineId,
    name: 'AAPL STP feed',
    description: 'A local STP binary to JSONL feed.',
    archived: false,
    latestRevisionNumber: 1,
    createdAt: '2026-08-03T12:00:00Z',
    updatedAt: '2026-08-03T12:00:00Z',
  };
}

function createdPipeline() {
  return {
    ...pipelineSummary(),
    version: 0,
    latestRevision: {
      id: '220ad925-c3e8-48ab-b9cc-951c67e42e83',
      revisionNumber: 1,
      createdAt: '2026-08-03T12:00:00Z',
    },
  };
}
