package io.streamforge.common.model;

/** Discriminator for the version-1 canonical event payload hierarchy. */
public enum CanonicalEventType {
  ORDER_ADDED,
  ORDER_EXECUTED,
  ORDER_CANCELLED,
  TRADE,
  QUOTE
}
