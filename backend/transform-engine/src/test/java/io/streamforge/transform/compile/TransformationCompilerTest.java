package io.streamforge.transform.compile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.streamforge.transform.config.FieldPath;
import io.streamforge.transform.config.FieldType;
import io.streamforge.transform.config.TransformationConfig;
import io.streamforge.transform.config.TransformationConfigParser;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TransformationCompilerTest {

  private final TransformationConfigParser parser = new TransformationConfigParser();
  private final TransformationCompiler compiler = new TransformationCompiler();

  @Test
  void compilesEverySupportedOperationAndTracksOutputFields() throws Exception {
    TransformationConfig config = parser.parse(validConfiguration());

    CompiledTransformation compiled = compiler.compile(config, CanonicalTransformationFields.v1());

    assertThat(compiled.operations()).hasSize(10);
    assertThat(compiled.outputSchema().fields())
        .containsKeys(
            new FieldPath("metadata.sequenceNumber"),
            new FieldPath("payload.type"),
            new FieldPath("instrument.ticker"),
            new FieldPath("payload.price"),
            new FieldPath("payload.quantity"),
            new FieldPath("enrichment.large"))
        .doesNotContainKeys(
            new FieldPath("instrument.symbol"),
            new FieldPath("payload.aggressorSide"),
            new FieldPath("payload.orderId"));
    assertThat(compiled.outputSchema().fields().get(new FieldPath("payload.quantity")).type())
        .isEqualTo(FieldType.FIXED_DECIMAL);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("semanticFailures")
  void rejectsInvalidConfigurationsBeforeActivation(
      String description,
      String operation,
      ValidationErrorCode expectedCode,
      int expectedOperationIndex) {
    String json =
        """
        {"schemaVersion":"1.0","operations":[%s]}
        """
            .formatted(operation);

    assertThatThrownBy(
            () -> compiler.compile(parser.parse(json), CanonicalTransformationFields.v1()))
        .isInstanceOfSatisfying(
            TransformationValidationException.class,
            exception -> {
              assertThat(exception.code()).isEqualTo(expectedCode);
              assertThat(exception.operationIndex()).isEqualTo(expectedOperationIndex);
            });
  }

  @Test
  void validatesReferencesAgainstPriorOrderedOperations() throws Exception {
    String json =
        """
        {
          "schemaVersion":"1.0",
          "operations":[
            {"op":"rename","from":"instrument.symbol","to":"instrument.ticker"},
            {"op":"filter","condition":{"type":"comparison","field":"instrument.symbol","operator":"EQ","value":{"type":"STRING","value":"AAPL"}}}
          ]
        }
        """;

    assertThatThrownBy(
            () -> compiler.compile(parser.parse(json), CanonicalTransformationFields.v1()))
        .isInstanceOfSatisfying(
            TransformationValidationException.class,
            exception -> {
              assertThat(exception.code()).isEqualTo(ValidationErrorCode.UNKNOWN_FIELD);
              assertThat(exception.operationIndex()).isEqualTo(1);
            });
  }

  private static Stream<Arguments> semanticFailures() {
    return Stream.of(
        Arguments.of(
            "unknown source path",
            "{\"op\":\"remove\",\"path\":\"payload.missing\"}",
            ValidationErrorCode.UNKNOWN_FIELD,
            0),
        Arguments.of(
            "protected metadata",
            "{\"op\":\"remove\",\"path\":\"metadata.source\"}",
            ValidationErrorCode.PROTECTED_FIELD,
            0),
        Arguments.of(
            "parent with protected discriminator",
            "{\"op\":\"rename\",\"from\":\"payload\",\"to\":\"body\"}",
            ValidationErrorCode.PROTECTED_FIELD,
            0),
        Arguments.of(
            "missing target parent",
            "{\"op\":\"add_constant\",\"path\":\"enrichment.flag\",\"value\":{\"type\":\"BOOLEAN\",\"value\":true}}",
            ValidationErrorCode.INVALID_TARGET,
            0),
        Arguments.of(
            "unsupported cast",
            "{\"op\":\"cast\",\"path\":\"payload.side\",\"toType\":\"FIXED_DECIMAL\"}",
            ValidationErrorCode.UNSUPPORTED_CAST,
            0),
        Arguments.of(
            "wrong comparison type",
            "{\"op\":\"filter\",\"condition\":{\"type\":\"comparison\",\"field\":\"payload.quantity\",\"operator\":\"EQ\",\"value\":{\"type\":\"STRING\",\"value\":\"10\"}}}",
            ValidationErrorCode.TYPE_MISMATCH,
            0),
        Arguments.of(
            "ordered boolean comparison",
            "{\"op\":\"create_object\",\"path\":\"flags\"},{\"op\":\"add_constant\",\"path\":\"flags.active\",\"value\":{\"type\":\"BOOLEAN\",\"value\":true}},{\"op\":\"filter\",\"condition\":{\"type\":\"comparison\",\"field\":\"flags.active\",\"operator\":\"GT\",\"value\":{\"type\":\"BOOLEAN\",\"value\":false}}}",
            ValidationErrorCode.INVALID_COMPARISON,
            2));
  }

  private static String validConfiguration() {
    return """
        {
          "schemaVersion": "1.0",
          "operations": [
            {"op":"create_object","path":"enrichment"},
            {"op":"add_constant","path":"enrichment.source","value":{"type":"STRING","value":"feed-a"}},
            {
              "op":"conditional_field",
              "path":"enrichment.large",
              "condition":{"type":"comparison","field":"payload.quantity","operator":"GT","value":{"type":"INT64","value":100}},
              "whenTrue":{"type":"BOOLEAN","value":true},
              "whenFalse":{"type":"BOOLEAN","value":false}
            },
            {"op":"filter","condition":{"type":"comparison","field":"payload.price","operator":"GT","value":{"type":"FIXED_DECIMAL","mantissa":0,"scale":2}}},
            {"op":"enum_map","path":"payload.side","mapping":{"BUY":"B","SELL":"S"}},
            {"op":"scale_fixed_decimal","path":"payload.price","targetScale":4},
            {"op":"cast","path":"payload.quantity","toType":"FIXED_DECIMAL"},
            {"op":"rename","from":"instrument.symbol","to":"instrument.ticker"},
            {"op":"remove","path":"payload.aggressorSide"},
            {"op":"select","fields":["instrument.ticker","payload.price","payload.quantity","enrichment"]}
          ]
        }
        """;
  }
}
