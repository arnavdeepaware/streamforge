import { afterEach, describe, expect, it, vi } from 'vitest';
import { subscribePipelineMonitoring } from './pipelineMonitoringStream';

class FakeEventSource {
  static instances: FakeEventSource[] = [];
  onerror: (() => void) | null = null;
  private readonly listeners = new Map<string, (event: Event) => void>();

  constructor(url: string) {
    if (url.length === 0) throw new Error('URL is required.');
    FakeEventSource.instances.push(this);
  }

  addEventListener(type: string, listener: (event: Event) => void) {
    this.listeners.set(type, listener);
  }

  close() {}

  emit(type: string, data: string) {
    this.listeners.get(type)?.(new MessageEvent(type, { data }));
  }
}

const snapshot = {
  runId: '8fdc57d0-68b4-4729-b6cc-7034dd465819',
  state: 'FAILED',
  counters: { received: 3, parsed: 2, emitted: 1, filtered: 0, failed: 1 },
  eventRatePerSecond: 2,
  latency: { totalNanos: 30, processedEvents: 2, averageNanos: 15 },
  queueDepth: 0,
  sequenceGapCount: 1,
  duplicateCount: 0,
  history: [],
  deadLetters: [],
  outputAvailable: false,
};

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
  FakeEventSource.instances = [];
});

describe('pipeline monitoring SSE', () => {
  it('reconnects after an interrupted stream and continues delivering failed-run snapshots', () => {
    vi.useFakeTimers();
    vi.stubGlobal('EventSource', FakeEventSource);
    const onSnapshot = vi.fn();
    const onReconnecting = vi.fn();
    const close = subscribePipelineMonitoring('/events', {
      onSnapshot,
      onReconnecting,
      onConnected: vi.fn(),
    });

    FakeEventSource.instances[0].onerror?.();
    expect(onReconnecting).toHaveBeenCalledOnce();
    vi.advanceTimersByTime(2_000);
    expect(FakeEventSource.instances).toHaveLength(2);

    FakeEventSource.instances[1].emit(
      'pipeline-health',
      JSON.stringify(snapshot),
    );
    expect(onSnapshot).toHaveBeenCalledWith(snapshot);
    close();
  });
});
