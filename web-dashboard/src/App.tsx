import { Navigate, useRoutes, type RouteObject } from 'react-router-dom'
import { AppShell } from './components/AppShell'
import { PlaceholderPage } from './routes/PlaceholderPage'
import { RouteErrorPage } from './routes/RouteErrorPage'

const routes: RouteObject[] = [
  {
    path: '/',
    element: <AppShell />,
    errorElement: <RouteErrorPage />,
    children: [
      { index: true, element: <Navigate to="dashboard" replace /> },
      { path: 'dashboard', element: <PlaceholderPage title="Dashboard" /> },
      { path: 'pipelines', element: <PlaceholderPage title="Pipelines" /> },
      { path: 'pipelines/new', element: <PlaceholderPage title="New Pipeline" /> },
      { path: 'schema-registry', element: <PlaceholderPage title="Schema Registry" /> },
      { path: 'stream-inspector', element: <PlaceholderPage title="Stream Inspector" /> },
      { path: 'dead-letter-events', element: <PlaceholderPage title="Dead-Letter Events" /> },
      { path: '*', element: <RouteErrorPage /> },
    ],
  },
]

export default function App() {
  return useRoutes(routes)
}
