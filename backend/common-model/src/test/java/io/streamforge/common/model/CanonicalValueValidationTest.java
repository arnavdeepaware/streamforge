package io.streamforge.common.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CanonicalValueValidationTest {

  @ParameterizedTest
  @MethodSource("validSources")
  void preservesStableCaseSensitiveSourceIdentities(String value) {
    SourceIdentity source = new SourceIdentity(value);

    assertThat(source.value()).isEqualTo(value);
    assertThat(source.toString()).isEqualTo(value);
  }

  @ParameterizedTest
  @MethodSource("invalidSources")
  void rejectsInvalidSourceIdentities(String value) {
    assertThatIllegalArgumentException().isThrownBy(() -> new SourceIdentity(value));
  }

  @ParameterizedTest
  @MethodSource("validRawReferences")
  void preservesOpaqueRawEventReferences(String value) {
    RawEventReference reference = new RawEventReference(value);

    assertThat(reference.value()).isEqualTo(value);
    assertThat(reference.toString()).isEqualTo(value);
  }

  @ParameterizedTest
  @MethodSource("invalidRawReferences")
  void rejectsInvalidRawEventReferences(String value) {
    assertThatIllegalArgumentException().isThrownBy(() -> new RawEventReference(value));
  }

  @ParameterizedTest
  @MethodSource("validVersions")
  void preservesValidSchemaVersions(int major, int minor, String expected) {
    CanonicalSchemaVersion version = new CanonicalSchemaVersion(major, minor);

    assertThat(version.toString()).isEqualTo(expected);
  }

  @ParameterizedTest
  @MethodSource("invalidVersions")
  void rejectsInvalidSchemaVersions(int major, int minor) {
    assertThatIllegalArgumentException().isThrownBy(() -> new CanonicalSchemaVersion(major, minor));
  }

  private static Stream<String> validSources() {
    return Stream.of("1", "simulator/session-1", "XNYS:ITCH:2026-08-03", "source@example.test");
  }

  private static Stream<String> invalidSources() {
    return Stream.of(
        null,
        "",
        " source",
        "source ",
        "source#1",
        "sourc\u00E9",
        "A".repeat(SourceIdentity.MAX_LENGTH + 1));
  }

  private static Stream<String> validRawReferences() {
    return Stream.of("capture:0", "run-1/source-a/00001@2048");
  }

  private static Stream<String> invalidRawReferences() {
    return Stream.of(
        null,
        "",
        "capture 0",
        "capture\n0",
        "capture:\u00E9",
        "A".repeat(RawEventReference.MAX_LENGTH + 1));
  }

  private static Stream<Arguments> validVersions() {
    return Stream.of(
        Arguments.of(1, 0, "1.0"),
        Arguments.of(Integer.MAX_VALUE, Integer.MAX_VALUE, "2147483647.2147483647"));
  }

  private static Stream<Arguments> invalidVersions() {
    return Stream.of(
        Arguments.of(Integer.MIN_VALUE, 0),
        Arguments.of(0, 0),
        Arguments.of(1, Integer.MIN_VALUE),
        Arguments.of(1, -1));
  }
}
