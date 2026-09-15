package io.streamforge.pipelineruntime.output;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Explicit schema and bounded writer settings for one Parquet output. */
public record ParquetOutputConfig(
    ParquetCompression compression, long rowGroupSizeBytes, List<ParquetOutputColumn> columns) {
  public static final long DEFAULT_ROW_GROUP_SIZE_BYTES = 134_217_728L;
  public static final long MINIMUM_ROW_GROUP_SIZE_BYTES = 1_048_576L;
  public static final long MAXIMUM_ROW_GROUP_SIZE_BYTES = 536_870_912L;

  public ParquetOutputConfig {
    if (compression == null || columns == null || columns.isEmpty()) {
      throw new IllegalArgumentException("Parquet output requires compression and columns");
    }
    if (rowGroupSizeBytes < MINIMUM_ROW_GROUP_SIZE_BYTES
        || rowGroupSizeBytes > MAXIMUM_ROW_GROUP_SIZE_BYTES) {
      throw new IllegalArgumentException("Parquet row-group size is outside the supported bounds");
    }
    columns = List.copyOf(columns);
    Set<String> names = new HashSet<>();
    for (ParquetOutputColumn column : columns) {
      if (column == null || !names.add(column.name())) {
        throw new IllegalArgumentException("Parquet column names must be non-null and unique");
      }
    }
  }
}
