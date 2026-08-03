package io.streamforge.pipelineruntime;

import io.streamforge.common.model.SourceIdentity;
import io.streamforge.common.model.Venue;
import io.streamforge.parserengine.JsonLinesMode;
import io.streamforge.parserengine.csv.CsvAdapterConfig;
import io.streamforge.parserengine.csv.CsvMode;
import io.streamforge.stp.protocol.StpProtocol;
import java.nio.file.Path;

/** One validated input source consumed sequentially by the local pipeline runner. */
public sealed interface PipelineInput
    permits PipelineInput.StpBinary, PipelineInput.JsonLines, PipelineInput.Csv {

  Path path();

  /** Bounded STP binary file input with externally supplied source identity and venue. */
  record StpBinary(Path path, SourceIdentity source, Venue venue, int maximumFrameSize)
      implements PipelineInput {
    public StpBinary {
      if (path == null || source == null || venue == null) {
        throw new IllegalArgumentException("STP input path, source, and venue must not be null");
      }
      int minimum = StpProtocol.LENGTH_FIELD_WIDTH + StpProtocol.MIN_ENCODED_LENGTH;
      int maximum = StpProtocol.LENGTH_FIELD_WIDTH + StpProtocol.MAX_ENCODED_LENGTH;
      if (maximumFrameSize < minimum || maximumFrameSize > maximum) {
        throw new IllegalArgumentException("STP maximum frame size is outside the protocol bounds");
      }
    }
  }

  /** Canonical JSON Lines input with its existing line-error policy. */
  record JsonLines(Path path, JsonLinesMode mode) implements PipelineInput {
    public JsonLines {
      if (path == null || mode == null) {
        throw new IllegalArgumentException("JSONL input path and mode must not be null");
      }
    }
  }

  /** Configured CSV trade input with its existing row-error policy. */
  record Csv(Path path, CsvAdapterConfig config, CsvMode mode) implements PipelineInput {
    public Csv {
      if (path == null || config == null || mode == null) {
        throw new IllegalArgumentException("CSV input path, config, and mode must not be null");
      }
    }
  }
}
