type PlaceholderPageProps = {
  title: string;
};

export function PlaceholderPage({ title }: PlaceholderPageProps) {
  return (
    <section aria-labelledby="page-title" className="placeholder-page">
      <p className="eyebrow">Planned workspace</p>
      <h2 id="page-title">{title}</h2>
      <p>
        This area is intentionally a placeholder. Its functionality is not
        implemented yet.
      </p>
      <p aria-live="polite" className="status-message" role="status">
        This page is not connected to a control-plane feature yet.
      </p>
    </section>
  );
}
