import { Link } from 'react-router-dom';
import { ArrowLeft, MapPinOff } from 'lucide-react';

export function RouteErrorPage() {
  return (
    <section className="placeholder-page" aria-labelledby="unavailable-title">
      <MapPinOff
        aria-hidden="true"
        className="placeholder-page__icon"
        size={30}
      />
      <p className="eyebrow">Route unavailable</p>
      <h2 id="unavailable-title">Page unavailable</h2>
      <p role="alert">The requested dashboard route could not be displayed.</p>
      <Link className="button button--primary" to="/dashboard">
        <ArrowLeft aria-hidden="true" size={17} /> Return to Dashboard
      </Link>
    </section>
  );
}
