import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { renderDashboard } from './test/renderDashboard';
import { server } from './test/server';

describe('StreamForge dashboard shell', () => {
  it('renders accessible primary navigation', () => {
    renderDashboard('/not-a-v1-route');

    expect(
      screen.getByRole('navigation', { name: 'Primary navigation' }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('heading', { name: 'Page unavailable' }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('link', { name: 'Stream Inspector' }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('link', { name: 'Dead-Letter Events' }),
    ).not.toBeInTheDocument();
  });

  it('opens contextual help, traps Escape, and restores trigger focus', async () => {
    renderDashboard('/not-a-v1-route');
    const helpButton = screen.getByRole('button', { name: 'Help' });

    fireEvent.click(helpButton);

    const dialog = screen.getByRole('dialog', { name: 'Help and guidance' });
    expect(dialog).toBeInTheDocument();
    await waitFor(() =>
      expect(
        within(dialog).getByRole('button', { name: 'Close help' }),
      ).toHaveFocus(),
    );
    fireEvent.keyDown(dialog, { key: 'Escape' });
    await waitFor(() => expect(helpButton).toHaveFocus());
  });

  it('persists the desktop sidebar preference locally', () => {
    window.localStorage.removeItem('streamforge.ui.sidebar.v1');
    renderDashboard('/not-a-v1-route');

    fireEvent.click(screen.getByRole('button', { name: 'Collapse sidebar' }));

    expect(window.localStorage.getItem('streamforge.ui.sidebar.v1')).toBe(
      'collapsed',
    );
    fireEvent.click(screen.getByRole('button', { name: 'Expand sidebar' }));
    expect(window.localStorage.getItem('streamforge.ui.sidebar.v1')).toBe(
      'expanded',
    );
  });

  it('renders the overview from the existing pipeline-list response', async () => {
    server.use(
      http.get('*/api/v1/pipelines', () =>
        HttpResponse.json({
          items: [
            {
              id: 'pipeline-1',
              name: 'Equities intake',
              description: 'Normalizes local STP records.',
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

    renderDashboard('/dashboard');

    expect(
      await screen.findByRole('heading', { name: 'Welcome to StreamForge' }),
    ).toBeInTheDocument();
    expect(screen.getByText('1')).toBeInTheDocument();
    expect(
      screen.getByRole('link', { name: 'Equities intake' }),
    ).toHaveAttribute('href', '/pipelines/pipeline-1');
  });
});
