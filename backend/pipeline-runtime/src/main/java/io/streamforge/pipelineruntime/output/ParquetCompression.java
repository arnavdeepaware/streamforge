package io.streamforge.pipelineruntime.output;

/** Allowlisted Parquet compression codecs. */
public enum ParquetCompression {
  UNCOMPRESSED,
  SNAPPY,
  ZSTD
}
