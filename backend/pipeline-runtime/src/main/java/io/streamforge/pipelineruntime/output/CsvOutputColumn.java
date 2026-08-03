package io.streamforge.pipelineruntime.output;

import io.streamforge.transform.config.FieldPath;

/** One CSV header and the exact nested output-record path used for that column. */
public record CsvOutputColumn(String header, FieldPath path) {
  public CsvOutputColumn {
    if (header == null || header.isBlank() || path == null) {
      throw new IllegalArgumentException("CSV column requires a non-blank header and path");
    }
  }

  /** Creates a column from a validated dotted output-record path. */
  public static CsvOutputColumn of(String header, String path) {
    return new CsvOutputColumn(header, new FieldPath(path));
  }
}
