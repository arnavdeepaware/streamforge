import type { ReactNode } from 'react';

type StatePanelProps = {
  title: string;
  children: ReactNode;
};

export function LoadingState({ title }: Pick<StatePanelProps, 'title'>) {
  return (
    <section aria-busy="true" aria-live="polite" className="state-panel">
      <p className="eyebrow">Loading</p>
      <h2>{title}</h2>
      <p>Fetching the latest control-plane data.</p>
    </section>
  );
}

export function EmptyState({ title, children }: StatePanelProps) {
  return (
    <section className="state-panel">
      <p className="eyebrow">No records</p>
      <h2>{title}</h2>
      <p>{children}</p>
    </section>
  );
}

type ErrorStateProps = StatePanelProps & {
  onRetry: () => void;
};

export function ErrorState({ title, children, onRetry }: ErrorStateProps) {
  return (
    <section
      aria-live="assertive"
      className="state-panel state-panel--error"
      role="alert"
    >
      <p className="eyebrow">Connection problem</p>
      <h2>{title}</h2>
      <p>{children}</p>
      <button onClick={onRetry} type="button">
        Try again
      </button>
    </section>
  );
}
