package io.streamforge.pipelineruntime.output;

import io.streamforge.common.model.FixedDecimal;
import io.streamforge.transform.config.FieldPath;
import java.util.OptionalInt;

/** One stable Parquet column mapped to a scalar output-record path. */
public record ParquetOutputColumn(
    String name, FieldPath path, ParquetColumnType type, boolean required, OptionalInt scale) {
  public ParquetOutputColumn {
    if (name == null
        || !name.matches("[A-Za-z_][A-Za-z0-9_]*")
        || path == null
        || type == null
        || scale == null) {
      throw new IllegalArgumentException("Parquet column fields are invalid");
    }
    if (type == ParquetColumnType.FIXED_DECIMAL) {
      if (scale.isEmpty() || scale.getAsInt() < 0 || scale.getAsInt() > FixedDecimal.MAX_SCALE) {
        throw new IllegalArgumentException("fixed-decimal Parquet columns require scale 0..18");
      }
    } else if (scale.isPresent()) {
      throw new IllegalArgumentException("scale is only valid for fixed-decimal Parquet columns");
    }
  }
}
