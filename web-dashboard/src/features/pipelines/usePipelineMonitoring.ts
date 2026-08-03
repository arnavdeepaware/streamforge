import { useEffect, useState } from 'react';
import {
  controlPlaneClient,
  pipelineMonitoringEventsUrl,
  type PipelineMonitoring,
} from '../../api/controlPlaneClient';
import { subscribePipelineMonitoring } from '../../api/pipelineMonitoringStream';

export function usePipelineMonitoring(pipelineId: string, runId: string) {
  const [snapshot, setSnapshot] = useState<PipelineMonitoring | null>(null);
  const [connection, setConnection] = useState<
    'connecting' | 'connected' | 'reconnecting'
  >('connecting');
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    setConnection('connecting');
    setError(null);
    void controlPlaneClient
      .getPipelineMonitoring(pipelineId, runId)
      .then((value) => {
        if (active) setSnapshot(value);
      })
      .catch((reason: unknown) => {
        if (active)
          setError(
            reason instanceof Error
              ? reason.message
              : 'Monitoring could not be loaded.',
          );
      });
    const unsubscribe = subscribePipelineMonitoring(
      pipelineMonitoringEventsUrl(pipelineId, runId),
      {
        onSnapshot: (value) => {
          if (active) setSnapshot(value);
        },
        onConnected: () => {
          if (active) setConnection('connected');
        },
        onReconnecting: () => {
          if (active) setConnection('reconnecting');
        },
      },
    );
    return () => {
      active = false;
      unsubscribe();
    };
  }, [pipelineId, runId]);

  return { snapshot, connection, error };
}
