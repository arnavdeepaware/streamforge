package io.streamforge.common.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BuildSmokeTest {

  @Test
  void runsTestsWithTheSupportedJavaBaseline() {
    assertThat(Runtime.version().feature()).isBetween(21, 26);
    assertThat(BuildSmokeTest.class.getPackageName()).isEqualTo("io.streamforge.common.model");
  }
}
