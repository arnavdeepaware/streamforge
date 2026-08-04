package io.streamforge.controlplane.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.streamforge.pipelineruntime.deadletter.DeadLetterPolicy;
import org.junit.jupiter.api.Test;

class LocalDeadLetterOptionsTest {
  @Test
  void acceptsPositivePayloadLimitsThroughTheMaximum() {
    assertThat(new LocalDeadLetterOptions(DeadLetterPolicy.QUARANTINE, true, 1))
        .extracting(LocalDeadLetterOptions::maximumPayloadBytes)
        .isEqualTo(1);
    assertThat(
            new LocalDeadLetterOptions(
                DeadLetterPolicy.QUARANTINE, true, LocalDeadLetterOptions.MAXIMUM_PAYLOAD_BYTES))
        .extracting(LocalDeadLetterOptions::maximumPayloadBytes)
        .isEqualTo(LocalDeadLetterOptions.MAXIMUM_PAYLOAD_BYTES);
  }

  @Test
  void rejectsNonPositiveAndExcessivePayloadLimits() {
    assertThatThrownBy(() -> new LocalDeadLetterOptions(DeadLetterPolicy.QUARANTINE, true, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("dead-letter options are invalid");
    assertThatThrownBy(() -> new LocalDeadLetterOptions(DeadLetterPolicy.QUARANTINE, true, -1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("dead-letter options are invalid");
    assertThatThrownBy(
            () ->
                new LocalDeadLetterOptions(
                    DeadLetterPolicy.QUARANTINE,
                    true,
                    LocalDeadLetterOptions.MAXIMUM_PAYLOAD_BYTES + 1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("dead-letter options are invalid");
  }
}
