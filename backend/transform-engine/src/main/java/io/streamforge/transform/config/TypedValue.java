package io.streamforge.transform.config;

import io.streamforge.common.model.FixedDecimal;

/** Closed set of literal values representable in transformation configuration. */
public sealed interface TypedValue
    permits TypedValue.StringValue,
        TypedValue.BooleanValue,
        TypedValue.Int64Value,
        TypedValue.FixedDecimalValue,
        TypedValue.EnumValue,
        TypedValue.TimestampNanosValue {

  FieldType type();

  record StringValue(String value) implements TypedValue {
    public StringValue {
      if (value == null) {
        throw new IllegalArgumentException("string value must not be null");
      }
    }

    @Override
    public FieldType type() {
      return FieldType.STRING;
    }
  }

  record BooleanValue(boolean value) implements TypedValue {
    @Override
    public FieldType type() {
      return FieldType.BOOLEAN;
    }
  }

  record Int64Value(long value) implements TypedValue {
    @Override
    public FieldType type() {
      return FieldType.INT64;
    }
  }

  record FixedDecimalValue(FixedDecimal value) implements TypedValue {
    public FixedDecimalValue {
      if (value == null) {
        throw new IllegalArgumentException("fixed-decimal value must not be null");
      }
    }

    @Override
    public FieldType type() {
      return FieldType.FIXED_DECIMAL;
    }
  }

  record EnumValue(String value) implements TypedValue {
    public EnumValue {
      if (value == null || value.isBlank()) {
        throw new IllegalArgumentException("enum value must not be blank");
      }
    }

    @Override
    public FieldType type() {
      return FieldType.ENUM;
    }
  }

  record TimestampNanosValue(long value) implements TypedValue {
    public TimestampNanosValue {
      if (value < 0) {
        throw new IllegalArgumentException("timestamp nanoseconds must be nonnegative");
      }
    }

    @Override
    public FieldType type() {
      return FieldType.TIMESTAMP_NANOS;
    }
  }
}
