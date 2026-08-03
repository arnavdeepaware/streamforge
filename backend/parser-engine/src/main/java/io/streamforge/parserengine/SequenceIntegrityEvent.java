package io.streamforge.parserengine;

import io.streamforge.common.model.SequenceNumber;
import java.util.Optional;

/** Structured classification of one sequence number received for one logical source or session. */
public record SequenceIntegrityEvent(
    SequenceSource source,
    SequenceIntegrityStatus status,
    SequenceNumber receivedSequence,
    Optional<SequenceNumber> expectedSequence,
    long missingSequenceCount) {

  public SequenceIntegrityEvent {
    if (source == null || status == null || receivedSequence == null || expectedSequence == null) {
      throw new IllegalArgumentException("integrity event fields must not be null");
    }
    if (missingSequenceCount < 0) {
      throw new IllegalArgumentException("missingSequenceCount must not be negative");
    }
    if ((status == SequenceIntegrityStatus.GAP_DETECTED) != (missingSequenceCount > 0)) {
      throw new IllegalArgumentException("only a detected gap may report missing sequences");
    }
  }
}
