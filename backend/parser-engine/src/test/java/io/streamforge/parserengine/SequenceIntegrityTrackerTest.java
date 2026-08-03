package io.streamforge.parserengine;

import static org.assertj.core.api.Assertions.assertThat;

import io.streamforge.common.model.SequenceNumber;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SequenceIntegrityTrackerTest {

  private static final SequenceSource PRIMARY_SOURCE = new SequenceSource("primary-session");

  @Test
  void classifiesNormalSequencesAsExpected() {
    SequenceIntegrityTracker tracker = new SequenceIntegrityTracker();

    assertThat(tracker.track(PRIMARY_SOURCE, sequence(1)).status())
        .isEqualTo(SequenceIntegrityStatus.EXPECTED);
    SequenceIntegrityEvent second = tracker.track(PRIMARY_SOURCE, sequence(2));

    assertThat(second.status()).isEqualTo(SequenceIntegrityStatus.EXPECTED);
    assertThat(second.expectedSequence()).contains(sequence(2));
    assertThat(second.missingSequenceCount()).isZero();
  }

  @Test
  void reportsStartupAndMidstreamGapsAgainstTheExpectedSequence() {
    SequenceIntegrityTracker tracker = new SequenceIntegrityTracker();

    SequenceIntegrityEvent startupGap = tracker.track(PRIMARY_SOURCE, sequence(4));
    SequenceIntegrityEvent laterGap = tracker.track(PRIMARY_SOURCE, sequence(7));

    assertThat(startupGap.status()).isEqualTo(SequenceIntegrityStatus.GAP_DETECTED);
    assertThat(startupGap.expectedSequence()).contains(sequence(1));
    assertThat(startupGap.missingSequenceCount()).isEqualTo(3);
    assertThat(laterGap.status()).isEqualTo(SequenceIntegrityStatus.GAP_DETECTED);
    assertThat(laterGap.expectedSequence()).contains(sequence(5));
    assertThat(laterGap.missingSequenceCount()).isEqualTo(2);
  }

  @Test
  void distinguishesAnImmediateDuplicateFromALateMessage() {
    SequenceIntegrityTracker tracker = new SequenceIntegrityTracker();
    tracker.track(PRIMARY_SOURCE, sequence(1));
    tracker.track(PRIMARY_SOURCE, sequence(2));

    SequenceIntegrityEvent duplicate = tracker.track(PRIMARY_SOURCE, sequence(2));
    SequenceIntegrityEvent late = tracker.track(PRIMARY_SOURCE, sequence(1));

    assertThat(duplicate.status()).isEqualTo(SequenceIntegrityStatus.DUPLICATE);
    assertThat(late.status()).isEqualTo(SequenceIntegrityStatus.LATE_OR_OUT_OF_ORDER);
  }

  @Test
  void resetRestoresStartupExpectation() {
    SequenceIntegrityTracker tracker = new SequenceIntegrityTracker();
    tracker.track(PRIMARY_SOURCE, sequence(8));

    tracker.reset(PRIMARY_SOURCE);
    SequenceIntegrityEvent afterReset = tracker.track(PRIMARY_SOURCE, sequence(1));

    assertThat(afterReset.status()).isEqualTo(SequenceIntegrityStatus.EXPECTED);
    assertThat(afterReset.expectedSequence()).contains(sequence(1));
  }

  @Test
  void tracksMultipleSourcesIndependently() {
    SequenceIntegrityTracker tracker = new SequenceIntegrityTracker();
    SequenceSource secondarySource = new SequenceSource("secondary-session");
    tracker.track(PRIMARY_SOURCE, sequence(2));

    SequenceIntegrityEvent secondaryFirst = tracker.track(secondarySource, sequence(1));
    SequenceIntegrityEvent primaryExpected = tracker.track(PRIMARY_SOURCE, sequence(3));

    assertThat(secondaryFirst.status()).isEqualTo(SequenceIntegrityStatus.EXPECTED);
    assertThat(primaryExpected.status()).isEqualTo(SequenceIntegrityStatus.EXPECTED);
  }

  @Test
  void handlesTheLargestSupportedSequenceWithoutOverflow() {
    SequenceIntegrityTracker tracker = new SequenceIntegrityTracker();
    tracker.track(PRIMARY_SOURCE, sequence(Long.MAX_VALUE - 1));

    SequenceIntegrityEvent maximum = tracker.track(PRIMARY_SOURCE, sequence(Long.MAX_VALUE));
    SequenceIntegrityEvent duplicate = tracker.track(PRIMARY_SOURCE, sequence(Long.MAX_VALUE));
    SequenceIntegrityEvent late = tracker.track(PRIMARY_SOURCE, sequence(Long.MAX_VALUE - 1));

    assertThat(maximum.status()).isEqualTo(SequenceIntegrityStatus.EXPECTED);
    assertThat(maximum.expectedSequence()).contains(sequence(Long.MAX_VALUE));
    assertThat(duplicate.status()).isEqualTo(SequenceIntegrityStatus.DUPLICATE);
    assertThat(duplicate.expectedSequence()).isEqualTo(Optional.empty());
    assertThat(late.status()).isEqualTo(SequenceIntegrityStatus.LATE_OR_OUT_OF_ORDER);
  }

  private static SequenceNumber sequence(long value) {
    return new SequenceNumber(value);
  }
}
