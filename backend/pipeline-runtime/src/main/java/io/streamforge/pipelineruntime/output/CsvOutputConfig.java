package io.streamforge.pipelineruntime.output;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Explicit deterministic column order and header behavior for one CSV sink. */
public record CsvOutputConfig(List<CsvOutputColumn> columns, boolean includeHeader) {
  public CsvOutputConfig {
    if (columns == null || columns.isEmpty()) {
      throw new IllegalArgumentException("CSV output requires at least one column");
    }
    columns = List.copyOf(columns);
    Set<String> headers = new HashSet<>();
    for (CsvOutputColumn column : columns) {
      if (column == null || !headers.add(column.header())) {
        throw new IllegalArgumentException("CSV column headers must be non-null and unique");
      }
    }
  }
}
