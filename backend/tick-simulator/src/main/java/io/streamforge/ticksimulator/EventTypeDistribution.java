package io.streamforge.ticksimulator;

/** Non-negative integer weights used to select simulated STP event types. */
public record EventTypeDistribution(
    int addOrderWeight, int executeOrderWeight, int cancelOrderWeight, int tradeWeight) {

  public EventTypeDistribution {
    if (addOrderWeight < 0 || executeOrderWeight < 0 || cancelOrderWeight < 0 || tradeWeight < 0) {
      throw new IllegalArgumentException("event type weights must not be negative");
    }
    int totalWeight =
        Math.addExact(
            Math.addExact(addOrderWeight, executeOrderWeight),
            Math.addExact(cancelOrderWeight, tradeWeight));
    if (totalWeight == 0) {
      throw new IllegalArgumentException("at least one event type weight must be positive");
    }
  }

  /** Returns the combined event selection weight. */
  public int totalWeight() {
    return Math.addExact(
        Math.addExact(addOrderWeight, executeOrderWeight),
        Math.addExact(cancelOrderWeight, tradeWeight));
  }

  /** Returns the default distribution, biased toward creating orders. */
  public static EventTypeDistribution defaults() {
    return new EventTypeDistribution(55, 20, 15, 10);
  }
}
