package io.streamforge.parserengine;

import io.streamforge.common.model.SequenceNumber;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Transport-independent, in-memory sequence classifier keyed by logical source or session.
 *
 * <p>Each new source begins expecting sequence {@code 1}. The tracker retains only the next
 * expected sequence and the most recently accepted sequence; it does not retain event payloads or
 * depend on a transport or storage implementation.
 */
public final class SequenceIntegrityTracker {

  private static final SequenceNumber FIRST_EXPECTED_SEQUENCE = new SequenceNumber(1);

  private final Map<SequenceSource, SourceState> stateBySource = new HashMap<>();

  /** Classifies one sequence number and advances only when it is expected or reveals a gap. */
  public synchronized SequenceIntegrityEvent track(SequenceSource source, SequenceNumber received) {
    if (source == null || received == null) {
      throw new IllegalArgumentException("source and received sequence must not be null");
    }
    SourceState state = stateBySource.get(source);
    if (state == null) {
      return classifyInitial(source, received);
    }

    Optional<SequenceNumber> expected = state.nextExpected;
    if (expected.isEmpty()) {
      return classifyAfterMaximum(source, received, state.lastAccepted);
    }

    long expectedValue = expected.orElseThrow().value();
    long receivedValue = received.value();
    if (receivedValue == expectedValue) {
      advance(state, received);
      return event(source, SequenceIntegrityStatus.EXPECTED, received, expected, 0);
    }
    if (receivedValue > expectedValue) {
      long missingCount = receivedValue - expectedValue;
      advance(state, received);
      return event(source, SequenceIntegrityStatus.GAP_DETECTED, received, expected, missingCount);
    }
    if (received.equals(state.lastAccepted)) {
      return event(source, SequenceIntegrityStatus.DUPLICATE, received, expected, 0);
    }
    return event(source, SequenceIntegrityStatus.LATE_OR_OUT_OF_ORDER, received, expected, 0);
  }

  /**
   * Forgets one source so its next received sequence is classified against startup expectation 1.
   */
  public synchronized void reset(SequenceSource source) {
    if (source == null) {
      throw new IllegalArgumentException("source must not be null");
    }
    stateBySource.remove(source);
  }

  /** Forgets every source and starts all future sources with expected sequence 1. */
  public synchronized void resetAll() {
    stateBySource.clear();
  }

  private SequenceIntegrityEvent classifyInitial(SequenceSource source, SequenceNumber received) {
    long receivedValue = received.value();
    SourceState state = new SourceState(received, nextExpectedAfter(received));
    stateBySource.put(source, state);
    if (receivedValue == FIRST_EXPECTED_SEQUENCE.value()) {
      return event(
          source,
          SequenceIntegrityStatus.EXPECTED,
          received,
          Optional.of(FIRST_EXPECTED_SEQUENCE),
          0);
    }
    return event(
        source,
        SequenceIntegrityStatus.GAP_DETECTED,
        received,
        Optional.of(FIRST_EXPECTED_SEQUENCE),
        receivedValue - FIRST_EXPECTED_SEQUENCE.value());
  }

  private static SequenceIntegrityEvent classifyAfterMaximum(
      SequenceSource source, SequenceNumber received, SequenceNumber lastAccepted) {
    SequenceIntegrityStatus status =
        received.equals(lastAccepted)
            ? SequenceIntegrityStatus.DUPLICATE
            : SequenceIntegrityStatus.LATE_OR_OUT_OF_ORDER;
    return event(source, status, received, Optional.empty(), 0);
  }

  private static void advance(SourceState state, SequenceNumber accepted) {
    state.lastAccepted = accepted;
    state.nextExpected = nextExpectedAfter(accepted);
  }

  private static Optional<SequenceNumber> nextExpectedAfter(SequenceNumber accepted) {
    if (accepted.value() == Long.MAX_VALUE) {
      return Optional.empty();
    }
    return Optional.of(new SequenceNumber(accepted.value() + 1));
  }

  private static SequenceIntegrityEvent event(
      SequenceSource source,
      SequenceIntegrityStatus status,
      SequenceNumber received,
      Optional<SequenceNumber> expected,
      long missingCount) {
    return new SequenceIntegrityEvent(source, status, received, expected, missingCount);
  }

  private static final class SourceState {
    private SequenceNumber lastAccepted;
    private Optional<SequenceNumber> nextExpected;

    private SourceState(SequenceNumber lastAccepted, Optional<SequenceNumber> nextExpected) {
      this.lastAccepted = lastAccepted;
      this.nextExpected = nextExpected;
    }
  }
}
