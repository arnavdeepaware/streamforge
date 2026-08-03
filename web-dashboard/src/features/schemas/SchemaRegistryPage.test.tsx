import { http, HttpResponse } from 'msw';
import { fireEvent, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { renderDashboard } from '../../test/renderDashboard';
import { server } from '../../test/server';

describe('schema registry page', () => {
  it('renders schema data from the control-plane API', async () => {
    server.use(
      http.get('*/api/v1/schemas', () =>
        HttpResponse.json({
          items: [
            {
              id: 'f3a1efbb-c364-4d4b-b6c1-58c10e7db316',
              name: 'Canonical event',
              description: 'Versioned canonical market-event schema.',
              archived: false,
              latestRevisionNumber: 1,
              createdAt: '2026-08-03T12:00:00Z',
              updatedAt: '2026-08-03T12:00:00Z',
            },
          ],
          page: 0,
          size: 20,
          totalItems: 1,
          totalPages: 1,
        }),
      ),
    );

    renderDashboard('/schema-registry');

    expect(
      await screen.findByRole('heading', { name: 'Canonical event' }),
    ).toBeInTheDocument();
    expect(
      screen.getByText('Versioned canonical market-event schema.'),
    ).toBeInTheDocument();
  });

  it('navigates schema pages in fixed groups of 20', async () => {
    server.use(
      http.get('*/api/v1/schemas', ({ request }) => {
        const page = Number(new URL(request.url).searchParams.get('page'));
        return HttpResponse.json({
          items: [
            {
              id: `schema-${page}`,
              name: `Schema page ${page + 1}`,
              description: 'A paged schema.',
              archived: false,
              latestRevisionNumber: 1,
              createdAt: '2026-08-03T12:00:00Z',
              updatedAt: '2026-08-03T12:00:00Z',
            },
          ],
          page,
          size: 20,
          totalItems: 21,
          totalPages: 2,
        });
      }),
    );

    renderDashboard('/schema-registry');
    expect(
      await screen.findByRole('heading', { name: 'Schema page 1' }),
    ).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Next' }));
    expect(
      await screen.findByRole('heading', { name: 'Schema page 2' }),
    ).toBeInTheDocument();
    expect(screen.getByText('Page 2 of 2')).toBeInTheDocument();
  });
});
