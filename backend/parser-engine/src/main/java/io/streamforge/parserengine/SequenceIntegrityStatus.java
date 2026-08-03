package io.streamforge.parserengine;

/** The integrity classification of one received STP sequence number. */
public enum SequenceIntegrityStatus {
  EXPECTED,
  GAP_DETECTED,
  DUPLICATE,
  LATE_OR_OUT_OF_ORDER
}
