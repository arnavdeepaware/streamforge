package io.streamforge.common.model;

/** Stable canonical reference to the instrument associated with a market event. */
public record InstrumentReference(InstrumentSymbol symbol) {

  public InstrumentReference {
    if (symbol == null) {
      throw new IllegalArgumentException("instrument symbol must not be null");
    }
  }

  @Override
  public String toString() {
    return symbol.toString();
  }
}
