import {
  ArrowRight,
  Braces,
  Download,
  Gauge,
  GitBranch,
  Plus,
  Workflow,
} from 'lucide-react';
import { Link } from 'react-router-dom';
import { exactIntegerText } from '../../api/controlPlaneClient';
import { usePipelines } from '../../api/queries';
import { ErrorState, LoadingState } from '../../components/AsyncState';
import { PageHeader } from '../../components/PageHeader';
import { ResourceStatusBadge } from '../../components/StatusBadge';

const workflow = [
  {
    icon: Plus,
    title: 'Create',
    text: 'Choose a source, safe transformations, and an output sink.',
  },
  {
    icon: GitBranch,
    title: 'Normalize',
    text: 'StreamForge converts input records through the canonical model.',
  },
  {
    icon: Gauge,
    title: 'Monitor',
    text: 'Follow throughput, latency, anomalies, and quarantined records.',
  },
  {
    icon: Download,
    title: 'Download',
    text: 'Collect finite output and the immutable source capture.',
  },
] as const;

export function DashboardPage() {
  const pipelines = usePipelines(0);

  if (pipelines.isPending) return <LoadingState title="Dashboard is loading" />;
  if (pipelines.isError)
    return (
      <ErrorState
        title="Dashboard could not be loaded"
        onRetry={() => void pipelines.refetch()}
      >
        {pipelines.error.message}
      </ErrorState>
    );

  const recent = pipelines.data.items.slice(0, 4);
  return (
    <section className="page-content" aria-labelledby="page-title">
      <PageHeader
        about={{
          purpose:
            'This overview is the starting point for configuring and running local market-data pipelines.',
          prerequisites:
            'Have a supported local STP, CSV, or JSONL source available to the control plane.',
          outcome:
            'A saved pipeline can be started, monitored, and downloaded from its detail page.',
        }}
        actions={
          <Link className="button button--primary" to="/pipelines/new">
            <Plus aria-hidden="true" size={18} />
            New pipeline
          </Link>
        }
        eyebrow="Operations overview"
        title="Welcome to StreamForge"
      >
        Build dependable local data pipelines and understand every run from
        source capture to normalized output.
      </PageHeader>

      <section className="hero-card" aria-labelledby="start-here-title">
        <div>
          <p className="eyebrow">Start here</p>
          <h3 id="start-here-title">Your pipeline workflow, end to end</h3>
          <p>
            StreamForge keeps configuration, execution health, failures, and run
            artifacts together so each local run is easy to inspect.
          </p>
        </div>
        <div className="hero-card__metric" aria-label="Saved pipelines">
          <Workflow aria-hidden="true" size={22} />
          <strong>{exactIntegerText(pipelines.data.totalItems)}</strong>
          <span>saved pipelines</span>
        </div>
      </section>

      <ol className="workflow-grid" aria-label="StreamForge workflow">
        {workflow.map(({ icon: Icon, title, text }, index) => (
          <li key={title}>
            <div className="workflow-step__icon">
              <Icon aria-hidden="true" size={20} />
            </div>
            <span className="workflow-step__number">0{index + 1}</span>
            <h3>{title}</h3>
            <p>{text}</p>
          </li>
        ))}
      </ol>

      <section className="section-block" aria-labelledby="recent-title">
        <div className="section-heading">
          <div>
            <p className="eyebrow">Control plane</p>
            <h3 id="recent-title">Recent pipelines</h3>
          </div>
          <Link className="text-link" to="/pipelines">
            View all pipelines <ArrowRight aria-hidden="true" size={16} />
          </Link>
        </div>
        {recent.length === 0 ? (
          <div className="inline-empty-state">
            <Braces aria-hidden="true" size={24} />
            <div>
              <h4>No pipelines yet</h4>
              <p>Create your first definition to begin a local run.</p>
            </div>
          </div>
        ) : (
          <div className="resource-grid resource-grid--compact">
            {recent.map((pipeline) => (
              <article className="resource-card" key={pipeline.id}>
                <div className="resource-card__heading">
                  <h4>
                    <Link to={`/pipelines/${pipeline.id}`}>
                      {pipeline.name}
                    </Link>
                  </h4>
                  <ResourceStatusBadge archived={pipeline.archived} />
                </div>
                <p>{pipeline.description || 'No description provided.'}</p>
                <Link
                  aria-label={`Open ${pipeline.name}`}
                  className="card-link"
                  to={`/pipelines/${pipeline.id}`}
                >
                  Open pipeline <ArrowRight aria-hidden="true" size={15} />
                </Link>
              </article>
            ))}
          </div>
        )}
      </section>
    </section>
  );
}
