package io.streamforge.controlplane.execution;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PipelineRunStateTest {
  @Test
  void permitsOnlyTheDocumentedLifecycleTransitions() {
    assertThat(PipelineRunState.CREATED.canTransitionTo(PipelineRunState.VALIDATED)).isTrue();
    assertThat(PipelineRunState.VALIDATED.canTransitionTo(PipelineRunState.STARTING)).isTrue();
    assertThat(PipelineRunState.STARTING.canTransitionTo(PipelineRunState.RUNNING)).isTrue();
    assertThat(PipelineRunState.RUNNING.canTransitionTo(PipelineRunState.STOPPING)).isTrue();
    assertThat(PipelineRunState.STOPPING.canTransitionTo(PipelineRunState.STOPPED)).isTrue();
    assertThat(PipelineRunState.COMPLETED.canTransitionTo(PipelineRunState.RUNNING)).isFalse();
    assertThat(PipelineRunState.FAILED.canTransitionTo(PipelineRunState.STARTING)).isFalse();
  }
}
