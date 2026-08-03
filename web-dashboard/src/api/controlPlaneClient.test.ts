import { LosslessNumber } from 'lossless-json';
import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  controlPlaneClient,
  exactIntegerText,
  exactJsonParse,
  exactJsonStringify,
  parsePipelineMonitoring,
} from './controlPlaneClient';

afterEach(() => vi.unstubAllGlobals());

describe('control-plane exact JSON transport', () => {
  it('preserves Long.MAX_VALUE timestamps, counters, and signed mantissas', () => {
    const parsed = exactJsonParse(
      '{"timestamp":9223372036854775807,"mantissa":-9223372036854775808}',
    ) as { timestamp: LosslessNumber; mantissa: LosslessNumber };

    expect(parsed.timestamp.value).toBe('9223372036854775807');
    expect(parsed.mantissa.value).toBe('-9223372036854775808');
    expect(exactJsonStringify(parsed)).toBe(
      '{"timestamp":9223372036854775807,"mantissa":-9223372036854775808}',
    );
  });

  it('round-trips exact request integers without converting them to strings', async () => {
    const fetch = vi.fn((_input: RequestInfo | URL, init?: RequestInit) => {
      expect(init?.body).toBe(
        '{"sampleEvent":{"exchangeTimestamp":9223372036854775807}}',
      );
      return Promise.resolve(
        new Response(
          '{"status":"RENDERED","input":{},"transformed":{},"output":{},"errors":[]}',
          { status: 200 },
        ),
      );
    });
    vi.stubGlobal('fetch', fetch);

    await controlPlaneClient.previewPipeline(
      {
        sampleEvent: {
          exchangeTimestamp: new LosslessNumber('9223372036854775807'),
        },
      },
      new AbortController().signal,
    );

    expect(fetch).toHaveBeenCalledOnce();
  });

  it('keeps aborted previews distinguishable from API outages', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() =>
        Promise.reject(
          new DOMException('The operation was aborted.', 'AbortError'),
        ),
      ),
    );

    await expect(
      controlPlaneClient.previewPipeline({}, new AbortController().signal),
    ).rejects.toMatchObject({ name: 'AbortError' });
  });

  it('caps monitoring history while retaining exact terminal counters', () => {
    const history = Array.from({ length: 121 }, (_, index) => ({
      timestamp: `2026-08-03T00:00:${String(index).padStart(2, '0')}Z`,
      received: index,
      emitted: index,
      failed: 0,
    }));
    const monitoring = parsePipelineMonitoring(
      exactJsonParse(
        exactJsonStringify({
          runId: 'run-1',
          state: 'COMPLETED',
          counters: {
            received: new LosslessNumber('9223372036854775807'),
            parsed: 1,
            emitted: 1,
            filtered: 0,
            failed: 0,
          },
          eventRatePerSecond: 0,
          latency: {
            totalNanos: new LosslessNumber('9223372036854775807'),
            processedEvents: 1,
            averageNanos: new LosslessNumber('9223372036854775807'),
          },
          queueDepth: 0,
          sequenceGapCount: 0,
          duplicateCount: 0,
          history,
          deadLetters: [],
          outputAvailable: true,
        }),
      ),
    );

    expect(exactIntegerText(monitoring.counters.received)).toBe(
      '9223372036854775807',
    );
    expect(monitoring.history).toHaveLength(120);
    expect(monitoring.history[0].received).toBe(1);
  });
});
