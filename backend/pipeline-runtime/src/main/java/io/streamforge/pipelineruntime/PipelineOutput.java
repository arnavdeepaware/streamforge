package io.streamforge.pipelineruntime;

import io.streamforge.pipelineruntime.output.CsvOutputConfig;
import java.nio.file.Path;

/** Finite local file output selected by a saved pipeline configuration. */
public sealed interface PipelineOutput permits PipelineOutput.JsonLines, PipelineOutput.Csv {
  Path path();

  /** JSON Lines file output. */
  record JsonLines(Path path) implements PipelineOutput {
    public JsonLines {
      if (path == null) {
        throw new IllegalArgumentException("JSONL output path must not be null");
      }
    }
  }

  /** CSV file output with explicit columns. */
  record Csv(Path path, CsvOutputConfig config) implements PipelineOutput {
    public Csv {
      if (path == null || config == null) {
        throw new IllegalArgumentException("CSV output path and config must not be null");
      }
    }
  }
}
