import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { renderDashboard } from './test/renderDashboard';

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
});
