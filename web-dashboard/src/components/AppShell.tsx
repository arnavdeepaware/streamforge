import { NavLink, Outlet } from 'react-router-dom'

const navigationItems = [
  { label: 'Dashboard', to: '/dashboard' },
  { label: 'Pipelines', to: '/pipelines' },
  { label: 'New Pipeline', to: '/pipelines/new' },
  { label: 'Schema Registry', to: '/schema-registry' },
  { label: 'Stream Inspector', to: '/stream-inspector' },
  { label: 'Dead-Letter Events', to: '/dead-letter-events' },
]

export function AppShell() {
  return (
    <div className="app-shell">
      <a className="skip-link" href="#main-content">
        Skip to main content
      </a>
      <header className="app-header">
        <p className="eyebrow">Market-data normalization</p>
        <h1>StreamForge</h1>
      </header>
      <nav aria-label="Primary navigation" className="app-navigation">
        <ul>
          {navigationItems.map(({ label, to }) => (
            <li key={to}>
              <NavLink to={to}>{label}</NavLink>
            </li>
          ))}
        </ul>
      </nav>
      <main id="main-content" tabIndex={-1}>
        <Outlet />
      </main>
    </div>
  )
}
