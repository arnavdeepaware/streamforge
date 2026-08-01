package io.streamforge.common.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BuildSmokeTest {

    @Test
    void runsTestsWithTheSupportedJavaBaseline() {
        assertThat(Runtime.version().feature()).isBetween(21, 26);
        assertThat(BuildSmokeTest.class.getPackageName()).isEqualTo("io.streamforge.common.model");
    }
}
