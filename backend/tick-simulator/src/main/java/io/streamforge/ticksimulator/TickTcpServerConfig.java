package io.streamforge.ticksimulator;

/** Immutable configuration for a local TCP STP tick server. */
public record TickTcpServerConfig(
    String host, int port, TickSimulationConfig simulation, long eventsPerSecond) {

  public TickTcpServerConfig {
    if (host == null || host.isBlank()) {
      throw new IllegalArgumentException("host must not be blank");
    }
    if (port < 0 || port > 65_535) {
      throw new IllegalArgumentException("port must be between 0 and 65535");
    }
    if (simulation == null) {
      throw new IllegalArgumentException("simulation must not be null");
    }
    if (eventsPerSecond < 0) {
      throw new IllegalArgumentException("eventsPerSecond must not be negative");
    }
    if (eventsPerSecond > 1_000_000_000L) {
      throw new IllegalArgumentException("eventsPerSecond must not exceed 1000000000");
    }
  }
}
