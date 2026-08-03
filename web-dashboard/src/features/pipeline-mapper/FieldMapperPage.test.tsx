import { fireEvent, screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { renderDashboard } from '../../test/renderDashboard';
import { server } from '../../test/server';

describe('field mapper', () => {
  it('builds a nested mapping and renders the backend preview', async () => {
    server.use(
      http.get('*/api/v1/pipelines/preview/canonical-fields', () =>
        HttpResponse.json([
          { path: 'instrument.symbol', type: 'STRING', protectedField: false },
          {
            path: 'metadata.exchangeTimestamp',
            type: 'TIMESTAMP_NANOS',
            protectedField: true,
          },
        ]),
      ),
      http.post('*/api/v1/pipelines/preview', () =>
        HttpResponse.json({
          status: 'RENDERED',
          input: { instrument: { symbol: 'AAPL' } },
          transformed: { instrument: { symbol: 'AAPL' } },
          output: { event: { symbol: 'AAPL' } },
          errors: [],
        }),
      ),
    );
    renderDashboard('/pipelines/mapper');
    expect(
      await screen.findByRole('button', { name: /instrument.symbol/ }),
    ).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Destination path'), {
      target: { value: 'event.symbol' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Add mapped field' }));
    expect(await screen.findByText('RENDERED')).toBeInTheDocument();
    expect(screen.getAllByText(/"symbol": "AAPL"/)).toHaveLength(3);
  });
});
