import {
  Archive,
  CheckCircle2,
  CircleDashed,
  CircleStop,
  LoaderCircle,
  PlayCircle,
  TriangleAlert,
} from 'lucide-react';
import type { PipelineRunState } from '../api/controlPlaneClient';

type Tone = 'success' | 'warning' | 'danger' | 'info' | 'neutral';

export function ResourceStatusBadge({ archived }: { archived: boolean }) {
  return archived ? (
    <Badge icon={Archive} label="Archived" tone="neutral" />
  ) : (
    <Badge icon={CheckCircle2} label="Active" tone="success" />
  );
}

export function RunStatusBadge({ state }: { state: PipelineRunState }) {
  const presentation: Record<
    PipelineRunState,
    { icon: typeof CircleDashed; tone: Tone }
  > = {
    CREATED: { icon: CircleDashed, tone: 'neutral' },
    VALIDATED: { icon: CheckCircle2, tone: 'info' },
    STARTING: { icon: LoaderCircle, tone: 'info' },
    RUNNING: { icon: PlayCircle, tone: 'success' },
    STOPPING: { icon: LoaderCircle, tone: 'warning' },
    STOPPED: { icon: CircleStop, tone: 'neutral' },
    COMPLETED: { icon: CheckCircle2, tone: 'success' },
    FAILED: { icon: TriangleAlert, tone: 'danger' },
  };
  const { icon, tone } = presentation[state];
  return <Badge icon={icon} label={humanize(state)} tone={tone} />;
}

function Badge({
  icon: Icon,
  label,
  tone,
}: {
  icon: typeof CircleDashed;
  label: string;
  tone: Tone;
}) {
  return (
    <span className={`status-badge status-badge--${tone}`}>
      <Icon aria-hidden="true" size={14} />
      {label}
    </span>
  );
}

function humanize(value: string): string {
  return value.charAt(0) + value.slice(1).toLowerCase().replaceAll('_', ' ');
}
