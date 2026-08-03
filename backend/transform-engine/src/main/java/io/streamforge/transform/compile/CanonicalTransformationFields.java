package io.streamforge.transform.compile;

import io.streamforge.transform.config.FieldPath;
import io.streamforge.transform.config.FieldType;
import java.util.ArrayList;
import java.util.List;

/** Version-1 canonical event field catalog for transformation compilation. */
public final class CanonicalTransformationFields {

  private CanonicalTransformationFields() {}

  /** Returns the canonical v1 union of envelope and payload fields. */
  public static TransformationFieldSchema v1() {
    List<FieldDefinition> fields = new ArrayList<>();
    add(fields, "metadata", FieldType.OBJECT, true);
    add(fields, "metadata.eventId", FieldType.STRING, true);
    add(fields, "metadata.schemaVersion", FieldType.OBJECT, true);
    add(fields, "metadata.schemaVersion.major", FieldType.INT64, true);
    add(fields, "metadata.schemaVersion.minor", FieldType.INT64, true);
    add(fields, "metadata.source", FieldType.STRING, true);
    add(fields, "metadata.venue", FieldType.STRING, true);
    add(fields, "metadata.exchangeTimestamp", FieldType.TIMESTAMP_NANOS, true);
    add(fields, "metadata.receiveTimestamp", FieldType.TIMESTAMP_NANOS, true);
    add(fields, "metadata.sequenceNumber", FieldType.INT64, true);
    add(fields, "metadata.rawEventReference", FieldType.STRING, true);

    add(fields, "instrument", FieldType.OBJECT, false);
    add(fields, "instrument.symbol", FieldType.STRING, false);

    add(fields, "payload", FieldType.OBJECT, false);
    add(fields, "payload.type", FieldType.ENUM, true);
    add(fields, "payload.orderId", FieldType.INT64, false);
    add(fields, "payload.tradeId", FieldType.INT64, false);
    add(fields, "payload.side", FieldType.ENUM, false);
    add(fields, "payload.aggressorSide", FieldType.ENUM, false);
    add(fields, "payload.quantity", FieldType.INT64, false);
    add(fields, "payload.executedQuantity", FieldType.INT64, false);
    add(fields, "payload.cancelledQuantity", FieldType.INT64, false);
    add(fields, "payload.price", FieldType.FIXED_DECIMAL, false);
    add(fields, "payload.bid", FieldType.OBJECT, false);
    add(fields, "payload.bid.price", FieldType.FIXED_DECIMAL, false);
    add(fields, "payload.bid.quantity", FieldType.INT64, false);
    add(fields, "payload.ask", FieldType.OBJECT, false);
    add(fields, "payload.ask.price", FieldType.FIXED_DECIMAL, false);
    add(fields, "payload.ask.quantity", FieldType.INT64, false);
    return TransformationFieldSchema.of(fields);
  }

  private static void add(
      List<FieldDefinition> fields, String path, FieldType type, boolean protectedField) {
    fields.add(new FieldDefinition(new FieldPath(path), type, protectedField));
  }
}
