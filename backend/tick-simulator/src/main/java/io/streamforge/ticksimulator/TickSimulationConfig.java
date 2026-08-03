package io.streamforge.ticksimulator;

import io.streamforge.common.model.EventTimestamp;
import io.streamforge.common.model.InstrumentSymbol;
import io.streamforge.common.model.SequenceNumber;
import java.util.List;

/** Immutable configuration for a deterministic STP tick simulation. */
public record TickSimulationConfig(
    long randomSeed,
    List<InstrumentSymbol> symbols,
    SimulationMode mode,
    EventTypeDistribution eventTypeDistribution,
    SequenceNumber initialSequenceNumber,
    EventTimestamp initialTimestamp,
    long timestampStepNanoseconds,
    int maximumOpenOrders) {

  public TickSimulationConfig {
    if (symbols == null || symbols.isEmpty()) {
      throw new IllegalArgumentException("symbols must contain at least one symbol");
    }
    symbols = List.copyOf(symbols);
    if (symbols.stream().anyMatch(symbol -> symbol == null)) {
      throw new IllegalArgumentException("symbols must not contain null values");
    }
    if (mode == null) {
      throw new IllegalArgumentException("mode must not be null");
    }
    if (eventTypeDistribution == null) {
      throw new IllegalArgumentException("eventTypeDistribution must not be null");
    }
    if (initialSequenceNumber == null) {
      throw new IllegalArgumentException("initialSequenceNumber must not be null");
    }
    if (initialTimestamp == null) {
      throw new IllegalArgumentException("initialTimestamp must not be null");
    }
    if (timestampStepNanoseconds < 1) {
      throw new IllegalArgumentException("timestampStepNanoseconds must be positive");
    }
    if (maximumOpenOrders < 1) {
      throw new IllegalArgumentException("maximumOpenOrders must be positive");
    }
  }

  /** Returns a practical finite configuration using deterministic defaults. */
  public static TickSimulationConfig finite(
      long randomSeed, List<InstrumentSymbol> symbols, long eventCount) {
    return new TickSimulationConfig(
        randomSeed,
        symbols,
        new FiniteSimulation(eventCount),
        EventTypeDistribution.defaults(),
        new SequenceNumber(1),
        new EventTimestamp(0),
        1_000_000,
        1_000);
  }
}
