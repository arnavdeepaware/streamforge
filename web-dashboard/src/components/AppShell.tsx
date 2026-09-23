import {
  BookOpen,
  Braces,
  ChevronLeft,
  CircleHelp,
  Gauge,
  LayoutDashboard,
  Menu,
  Network,
  PanelLeftOpen,
  Plus,
  X,
} from 'lucide-react';
import {
  useEffect,
  useRef,
  useState,
  type KeyboardEvent as ReactKeyboardEvent,
  type RefObject,
} from 'react';
import { Link, NavLink, Outlet, useLocation } from 'react-router-dom';

const SIDEBAR_PREFERENCE_KEY = 'streamforge.ui.sidebar.v1';

const navigationItems = [
  { label: 'Dashboard', to: '/dashboard', icon: LayoutDashboard },
  { label: 'Pipelines', to: '/pipelines', icon: Network },
  { label: 'New Pipeline', to: '/pipelines/new', icon: Plus },
  { label: 'Field Mapper', to: '/pipelines/mapper', icon: Braces },
  { label: 'Schema Registry', to: '/schema-registry', icon: BookOpen },
] as const;

export function AppShell() {
  const [collapsed, setCollapsed] = useState(readSidebarPreference);
  const [menuOpen, setMenuOpen] = useState(false);
  const [helpOpen, setHelpOpen] = useState(false);
  const menuButtonRef = useRef<HTMLButtonElement>(null);
  const menuPanelRef = useRef<HTMLElement>(null);
  const helpButtonRef = useRef<HTMLButtonElement>(null);
  const helpPanelRef = useRef<HTMLElement>(null);
  const restoreFocusRef = useRef<HTMLElement>(null);
  const location = useLocation();

  useEffect(() => {
    setMenuOpen(false);
    setHelpOpen(false);
  }, [location.pathname]);

  useEffect(() => {
    document.body.classList.toggle('drawer-open', menuOpen || helpOpen);
    return () => document.body.classList.remove('drawer-open');
  }, [helpOpen, menuOpen]);

  useEffect(() => {
    if (menuOpen) focusFirst(menuPanelRef);
  }, [menuOpen]);

  useEffect(() => {
    if (helpOpen) focusFirst(helpPanelRef);
  }, [helpOpen]);

  useEffect(() => {
    if (menuOpen || helpOpen || restoreFocusRef.current === null) return;
    restoreFocusRef.current.focus();
    restoreFocusRef.current = null;
  }, [helpOpen, menuOpen]);

  useEffect(() => {
    if (!menuOpen && !helpOpen) return;
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return;
      event.preventDefault();
      if (helpOpen) {
        restoreFocusRef.current = helpButtonRef.current;
        setHelpOpen(false);
      } else {
        restoreFocusRef.current = menuButtonRef.current;
        setMenuOpen(false);
      }
    };
    document.addEventListener('keydown', closeOnEscape);
    return () => document.removeEventListener('keydown', closeOnEscape);
  }, [helpOpen, menuOpen]);

  function toggleCollapsed() {
    setCollapsed((current) => {
      const next = !current;
      try {
        window.localStorage.setItem(
          SIDEBAR_PREFERENCE_KEY,
          next ? 'collapsed' : 'expanded',
        );
      } catch {
        // Storage can be unavailable in privacy-restricted browser contexts.
      }
      return next;
    });
  }

  return (
    <div className={collapsed ? 'app-shell app-shell--collapsed' : 'app-shell'}>
      <a className="skip-link" href="#main-content">
        Skip to main content
      </a>
      <header className="mobile-header" inert={menuOpen || helpOpen}>
        <button
          aria-controls="primary-sidebar"
          aria-expanded={menuOpen}
          aria-label="Open navigation"
          className="icon-button"
          onClick={() => setMenuOpen(true)}
          ref={menuButtonRef}
          type="button"
        >
          <Menu aria-hidden="true" />
        </button>
        <Brand compact />
        <button
          aria-controls="help-drawer"
          aria-expanded={helpOpen}
          aria-label="Open help"
          className="icon-button"
          onClick={(event) => openHelp(event.currentTarget)}
          type="button"
        >
          <CircleHelp aria-hidden="true" />
        </button>
      </header>

      {menuOpen ? (
        <button
          aria-label="Close navigation"
          className="drawer-backdrop drawer-backdrop--navigation"
          onClick={closeMenu}
          tabIndex={-1}
          type="button"
        />
      ) : null}
      <aside
        aria-label="Primary sidebar"
        className={menuOpen ? 'app-sidebar app-sidebar--open' : 'app-sidebar'}
        id="primary-sidebar"
        inert={helpOpen}
        onKeyDown={(event) =>
          handleDrawerKeyDown(event, menuPanelRef, closeMenu)
        }
        ref={menuPanelRef}
      >
        <div className="app-sidebar__brand-row">
          <Brand compact={collapsed} />
          <button
            aria-label="Close navigation"
            className="icon-button app-sidebar__mobile-close"
            onClick={closeMenu}
            type="button"
          >
            <X aria-hidden="true" />
          </button>
        </div>
        <div className="environment-pill">
          <span aria-hidden="true" />
          <span className="sidebar-label">Local v1</span>
        </div>
        <nav aria-label="Primary navigation" className="app-navigation">
          <p className="navigation-label sidebar-label">Workspace</p>
          <ul>
            {navigationItems.map(({ label, to, icon: Icon }) => (
              <li key={to}>
                <NavLink
                  end={to === '/dashboard' || to === '/pipelines'}
                  title={collapsed ? label : undefined}
                  to={to}
                >
                  <Icon aria-hidden="true" size={19} />
                  <span className="sidebar-label">{label}</span>
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>
        <div className="app-sidebar__footer">
          <button
            aria-controls="help-drawer"
            aria-expanded={helpOpen}
            className="sidebar-action"
            onClick={(event) => openHelp(event.currentTarget)}
            title={collapsed ? 'Help and guidance' : undefined}
            type="button"
          >
            <CircleHelp aria-hidden="true" size={19} />
            <span className="sidebar-label">Help and guidance</span>
          </button>
          <button
            aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
            className="sidebar-action app-sidebar__collapse"
            onClick={toggleCollapsed}
            type="button"
          >
            {collapsed ? (
              <PanelLeftOpen aria-hidden="true" size={19} />
            ) : (
              <ChevronLeft aria-hidden="true" size={19} />
            )}
            <span className="sidebar-label">Collapse sidebar</span>
          </button>
        </div>
      </aside>

      <div className="app-workspace" inert={menuOpen || helpOpen}>
        <div className="workspace-topbar">
          <div className="workspace-topbar__status">
            <span aria-hidden="true" />
            Local control plane
          </div>
          <button
            aria-controls="help-drawer"
            aria-expanded={helpOpen}
            className="button button--quiet"
            onClick={(event) => openHelp(event.currentTarget)}
            type="button"
          >
            <CircleHelp aria-hidden="true" size={17} />
            Help
          </button>
        </div>
        <main id="main-content" tabIndex={-1}>
          <Outlet />
        </main>
      </div>

      {helpOpen ? (
        <button
          aria-label="Close help"
          className="drawer-backdrop"
          onClick={closeHelp}
          tabIndex={-1}
          type="button"
        />
      ) : null}
      <aside
        aria-hidden={!helpOpen}
        aria-label="Help and guidance"
        aria-modal="true"
        className={helpOpen ? 'help-drawer help-drawer--open' : 'help-drawer'}
        id="help-drawer"
        onKeyDown={(event) =>
          handleDrawerKeyDown(event, helpPanelRef, closeHelp)
        }
        ref={helpPanelRef}
        role="dialog"
      >
        <div className="help-drawer__header">
          <div>
            <p className="eyebrow">Learn StreamForge</p>
            <h2>Help and guidance</h2>
          </div>
          <button
            aria-label="Close help"
            className="icon-button"
            onClick={closeHelp}
            type="button"
          >
            <X aria-hidden="true" />
          </button>
        </div>
        <div className="help-drawer__content">
          <section>
            <Gauge aria-hidden="true" size={20} />
            <h3>How StreamForge works</h3>
            <ol>
              <li>Create and validate a typed pipeline definition.</li>
              <li>Start its latest immutable revision locally.</li>
              <li>Monitor health, anomalies, and quarantined records.</li>
              <li>Download output and the run-scoped raw capture.</li>
            </ol>
          </section>
          <section>
            <BookOpen aria-hidden="true" size={20} />
            <h3>Explore features</h3>
            <ul className="help-links">
              <li>
                <Link to="/pipelines/new">Create a pipeline</Link>
              </li>
              <li>
                <Link to="/pipelines/mapper">Learn field mapping</Link>
              </li>
              <li>
                <Link to="/schema-registry">View canonical schemas</Link>
              </li>
            </ul>
          </section>
          <div className="help-note">
            <strong>Local v1</strong>
            <p>
              This release is intentionally unauthenticated and single-node.
              Artifact retention is managed manually.
            </p>
          </div>
        </div>
      </aside>
    </div>
  );

  function closeMenu() {
    restoreFocusRef.current = menuButtonRef.current;
    setMenuOpen(false);
  }

  function openHelp(trigger: HTMLButtonElement) {
    helpButtonRef.current = trigger;
    setHelpOpen(true);
  }

  function closeHelp() {
    restoreFocusRef.current = helpButtonRef.current;
    setHelpOpen(false);
  }
}

function Brand({ compact = false }: { compact?: boolean }) {
  return (
    <Link aria-label="StreamForge dashboard" className="brand" to="/dashboard">
      <span className="brand-mark" aria-hidden="true">
        <span />
        <span />
        <span />
      </span>
      {!compact ? (
        <span className="brand-copy">
          <strong>StreamForge</strong>
          <small>Operations workspace</small>
        </span>
      ) : null}
    </Link>
  );
}

function readSidebarPreference(): boolean {
  try {
    return window.localStorage.getItem(SIDEBAR_PREFERENCE_KEY) === 'collapsed';
  } catch {
    return false;
  }
}

function focusFirst(ref: RefObject<HTMLElement | null>) {
  window.setTimeout(() => {
    focusableElements(ref.current)[0]?.focus();
  }, 0);
}

function handleDrawerKeyDown(
  event: ReactKeyboardEvent<HTMLElement>,
  ref: RefObject<HTMLElement | null>,
  close: () => void,
) {
  if (event.key === 'Escape') {
    event.preventDefault();
    event.stopPropagation();
    close();
    return;
  }
  if (event.key !== 'Tab') return;
  const elements = focusableElements(ref.current);
  if (elements.length === 0) return;
  const first = elements[0];
  const last = elements[elements.length - 1];
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault();
    last.focus();
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault();
    first.focus();
  }
}

function focusableElements(element: HTMLElement | null): HTMLElement[] {
  if (element === null) return [];
  return Array.from(
    element.querySelectorAll<HTMLElement>(
      'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
    ),
  ).filter((candidate) => candidate.getAttribute('aria-hidden') !== 'true');
}
