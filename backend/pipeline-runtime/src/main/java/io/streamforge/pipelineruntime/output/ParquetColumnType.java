package io.streamforge.pipelineruntime.output;

/** Exact scalar types supported by the v1 Parquet contract. */
public enum ParquetColumnType {
  STRING,
  BOOLEAN,
  INT64,
  TIMESTAMP_NANOS,
  FIXED_DECIMAL
}
