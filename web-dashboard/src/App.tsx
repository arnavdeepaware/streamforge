import { Navigate, useRoutes, type RouteObject } from 'react-router-dom';
import { AppShell } from './components/AppShell';
import { PipelineDetailPage } from './features/pipelines/PipelineDetailPage';
import { PipelineListPage } from './features/pipelines/PipelineListPage';
import { PipelineCreationPage } from './features/pipeline-creation/PipelineCreationPage';
import { SchemaRegistryPage } from './features/schemas/SchemaRegistryPage';
import { PlaceholderPage } from './routes/PlaceholderPage';
import { RouteErrorPage } from './routes/RouteErrorPage';

const routes: RouteObject[] = [
  {
    path: '/',
    element: <AppShell />,
    errorElement: <RouteErrorPage />,
    children: [
      { index: true, element: <Navigate to="dashboard" replace /> },
      { path: 'dashboard', element: <PipelineListPage compact /> },
      { path: 'pipelines', element: <PipelineListPage /> },
      {
        path: 'pipelines/new',
        element: <PipelineCreationPage />,
      },
      { path: 'pipelines/:pipelineId', element: <PipelineDetailPage /> },
      {
        path: 'schema-registry',
        element: <SchemaRegistryPage />,
      },
      {
        path: 'stream-inspector',
        element: <PlaceholderPage title="Stream Inspector" />,
      },
      {
        path: 'dead-letter-events',
        element: <PlaceholderPage title="Dead-Letter Events" />,
      },
      { path: '*', element: <RouteErrorPage /> },
    ],
  },
];

export default function App() {
  return useRoutes(routes);
}
