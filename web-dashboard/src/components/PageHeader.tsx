type PageHeaderProps = {
  eyebrow: string;
  title: string;
  children: string;
};

export function PageHeader({ eyebrow, title, children }: PageHeaderProps) {
  return (
    <header className="page-header">
      <p className="eyebrow">{eyebrow}</p>
      <h2 id="page-title">{title}</h2>
      <p>{children}</p>
    </header>
  );
}
