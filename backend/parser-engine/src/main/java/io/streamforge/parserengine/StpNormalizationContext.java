package io.streamforge.parserengine;

import io.streamforge.common.model.EventTimestamp;
import io.streamforge.common.model.RawEventReference;
import io.streamforge.common.model.SourceIdentity;
import io.streamforge.common.model.Venue;
import java.util.Optional;

/**
 * Immutable caller-supplied provenance and lookup context for one STP frame normalization.
 *
 * <p>The raw-event reference identifies the particular captured frame. Source and venue are
 * injected rather than inferred from STP, which has no fields for either value.
 */
public record StpNormalizationContext(
    SourceIdentity source,
    Venue venue,
    Optional<EventTimestamp> receiveTimestamp,
    RawEventReference rawEventReference,
    StpOrderInstrumentResolver orderInstrumentResolver) {

  public StpNormalizationContext {
    if (source == null
        || venue == null
        || receiveTimestamp == null
        || rawEventReference == null
        || orderInstrumentResolver == null) {
      throw new IllegalArgumentException("STP normalization context fields must not be null");
    }
  }
}
