import {
  parsePipelineMonitoring,
  type PipelineMonitoring,
} from './controlPlaneClient';

type PipelineMonitoringSubscriber = {
  onSnapshot: (snapshot: PipelineMonitoring) => void;
  onReconnecting: () => void;
  onConnected: () => void;
};

/** Opens a reconnecting SSE subscription without retaining raw pipeline events in the browser. */
export function subscribePipelineMonitoring(
  url: string,
  subscriber: PipelineMonitoringSubscriber,
): () => void {
  let closed = false;
  let attempts = 0;
  let source: EventSource | null = null;
  let retry: number | undefined;

  const connect = () => {
    if (closed) return;
    source = new EventSource(url);
    source.addEventListener('pipeline-health', (event) => {
      try {
        const message = event as MessageEvent<string>;
        const snapshot = parsePipelineMonitoring(JSON.parse(message.data));
        subscriber.onSnapshot(snapshot);
        attempts = 0;
        subscriber.onConnected();
        if (['STOPPED', 'COMPLETED', 'FAILED'].includes(snapshot.state)) {
          closed = true;
          source?.close();
        }
      } catch {
        subscriber.onReconnecting();
      }
    });
    source.onerror = () => {
      source?.close();
      if (closed) return;
      attempts += 1;
      subscriber.onReconnecting();
      retry = window.setTimeout(
        connect,
        Math.min(1_000 * 2 ** attempts, 10_000),
      );
    };
  };

  connect();
  return () => {
    closed = true;
    source?.close();
    if (retry !== undefined) window.clearTimeout(retry);
  };
}
