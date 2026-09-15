import { Navigate, useRoutes, type RouteObject } from 'react-router-dom';
import { AppShell } from './components/AppShell';
import { PipelineDetailPage } from './features/pipelines/PipelineDetailPage';
import { PipelineListPage } from './features/pipelines/PipelineListPage';
import { PipelineCreationPage } from './features/pipeline-creation/PipelineCreationPage';
import { FieldMapperPage } from './features/pipeline-mapper/FieldMapperPage';
import { SchemaRegistryPage } from './features/schemas/SchemaRegistryPage';
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
      { path: 'pipelines/mapper', element: <FieldMapperPage /> },
      {
        path: 'schema-registry',
        element: <SchemaRegistryPage />,
      },
      { path: '*', element: <RouteErrorPage /> },
    ],
  },
];

export default function App() {
  return useRoutes(routes);
}
