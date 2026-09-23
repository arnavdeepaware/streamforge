import type { ReactNode } from 'react';
import { CircleHelp } from 'lucide-react';

type PageHeaderProps = {
  eyebrow: string;
  title: string;
  children: string;
  actions?: ReactNode;
  about?: {
    purpose: string;
    prerequisites?: string;
    outcome: string;
  };
};

export function PageHeader({
  eyebrow,
  title,
  children,
  actions,
  about,
}: PageHeaderProps) {
  return (
    <header className="page-header">
      <div className="page-header__main">
        <div>
          <p className="eyebrow">{eyebrow}</p>
          <h2 id="page-title">{title}</h2>
          <p className="page-header__summary">{children}</p>
        </div>
        {actions ? <div className="page-header__actions">{actions}</div> : null}
      </div>
      {about ? (
        <details className="about-disclosure">
          <summary>
            <CircleHelp aria-hidden="true" size={18} />
            About this page
          </summary>
          <div className="about-disclosure__content">
            <p>{about.purpose}</p>
            {about.prerequisites ? (
              <p>
                <strong>Before you begin:</strong> {about.prerequisites}
              </p>
            ) : null}
            <p>
              <strong>Expected result:</strong> {about.outcome}
            </p>
          </div>
        </details>
      ) : null}
    </header>
  );
}
