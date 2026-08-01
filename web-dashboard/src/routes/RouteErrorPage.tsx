import { Link } from 'react-router-dom';

export function RouteErrorPage() {
  return (
    <main className="placeholder-page" id="main-content" tabIndex={-1}>
      <p className="eyebrow">Route unavailable</p>
      <h1>Page unavailable</h1>
      <p role="alert">The requested dashboard route could not be displayed.</p>
      <Link to="/dashboard">Return to Dashboard</Link>
    </main>
  );
}
