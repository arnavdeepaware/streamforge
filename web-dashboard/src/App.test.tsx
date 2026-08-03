import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { renderDashboard } from './test/renderDashboard';

describe('StreamForge dashboard shell', () => {
  it('renders accessible primary navigation', () => {
    renderDashboard('/stream-inspector');

    expect(
      screen.getByRole('navigation', { name: 'Primary navigation' }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('heading', { name: 'Stream Inspector' }),
    ).toBeInTheDocument();
  });
});
