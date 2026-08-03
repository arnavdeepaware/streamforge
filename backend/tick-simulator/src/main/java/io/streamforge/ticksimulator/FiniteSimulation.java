package io.streamforge.ticksimulator;

/** A simulation that ends after exactly {@code eventCount} events. */
public record FiniteSimulation(long eventCount) implements SimulationMode {

  public FiniteSimulation {
    if (eventCount < 0) {
      throw new IllegalArgumentException("eventCount must not be negative");
    }
  }
}
