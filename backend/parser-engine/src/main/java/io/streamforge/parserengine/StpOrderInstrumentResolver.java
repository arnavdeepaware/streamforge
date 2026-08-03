package io.streamforge.parserengine;

import io.streamforge.common.model.InstrumentReference;
import io.streamforge.common.model.OrderId;
import java.util.Optional;

/** Resolves the instrument for STP messages that refer to an order without carrying a symbol. */
@FunctionalInterface
public interface StpOrderInstrumentResolver {

  /** Returns the known instrument for an order, or empty when no such association is available. */
  Optional<InstrumentReference> resolve(OrderId orderId);

  /** Returns a resolver for callers that do not maintain order-to-instrument state. */
  static StpOrderInstrumentResolver unavailable() {
    return ignored -> Optional.empty();
  }
}
