package io.streamforge.common.model;

/** The buy or sell side of an order or the aggressor side of a trade. */
public enum Side {
  BUY('B'),
  SELL('S');

  private final char stpCode;

  Side(char stpCode) {
    this.stpCode = stpCode;
  }

  /** Returns the STP wire code for this side. */
  public char stpCode() {
    return stpCode;
  }

  /** Converts an STP side code to its domain value. */
  public static Side fromStpCode(char code) {
    return switch (code) {
      case 'B' -> BUY;
      case 'S' -> SELL;
      default -> throw new IllegalArgumentException("side must be B or S");
    };
  }

  @Override
  public String toString() {
    return Character.toString(stpCode);
  }
}
