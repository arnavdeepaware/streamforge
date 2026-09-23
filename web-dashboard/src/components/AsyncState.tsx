import type { ReactNode } from 'react';
import { AlertTriangle, Inbox, RefreshCw } from 'lucide-react';

type StatePanelProps = {
  title: string;
  children: ReactNode;
};

export function LoadingState({ title }: Pick<StatePanelProps, 'title'>) {
  return (
    <section
      aria-busy="true"
      aria-live="polite"
      className="state-panel state-panel--loading"
    >
      <h2 className="visually-hidden">{title}</h2>
      <div aria-hidden="true" className="skeleton skeleton--eyebrow" />
      <div aria-hidden="true" className="skeleton skeleton--title" />
      <div aria-hidden="true" className="skeleton skeleton--copy" />
      <div aria-hidden="true" className="skeleton-grid">
        <div className="skeleton skeleton--card" />
        <div className="skeleton skeleton--card" />
        <div className="skeleton skeleton--card" />
      </div>
    </section>
  );
}

export function EmptyState({ title, children }: StatePanelProps) {
  return (
    <section className="state-panel">
      <Inbox aria-hidden="true" className="state-panel__icon" size={28} />
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
      <AlertTriangle
        aria-hidden="true"
        className="state-panel__icon"
        size={28}
      />
      <p className="eyebrow">Connection problem</p>
      <h2>{title}</h2>
      <p>{children}</p>
      <button
        className="button button--primary"
        onClick={onRetry}
        type="button"
      >
        <RefreshCw aria-hidden="true" size={17} />
        Try again
      </button>
    </section>
  );
}
