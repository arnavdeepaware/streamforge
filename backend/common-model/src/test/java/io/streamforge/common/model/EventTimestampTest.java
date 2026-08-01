package io.streamforge.common.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.stream.LongStream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class EventTimestampTest {

  @ParameterizedTest
  @MethodSource("validNanoseconds")
  void preservesNanosecondsWithoutTruncation(long nanoseconds) {
    EventTimestamp timestamp = new EventTimestamp(nanoseconds);

    assertThat(timestamp.nanosecondsSinceEpoch()).isEqualTo(nanoseconds);
    assertThat(timestamp.toString()).isEqualTo(nanoseconds + "ns since Unix epoch");
    assertThat(timestamp).isEqualTo(new EventTimestamp(nanoseconds));
  }

  @ParameterizedTest
  @MethodSource("invalidNanoseconds")
  void rejectsNegativeNanoseconds(long nanoseconds) {
    assertThatIllegalArgumentException().isThrownBy(() -> new EventTimestamp(nanoseconds));
  }

  private static LongStream validNanoseconds() {
    return LongStream.of(0L, 1L, 1_000_000_001L, Long.MAX_VALUE);
  }

  private static LongStream invalidNanoseconds() {
    return LongStream.of(-1L, Long.MIN_VALUE);
  }
}
