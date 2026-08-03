package io.streamforge.transform.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TransformationConfigParserTest {

  private final TransformationConfigParser parser = new TransformationConfigParser();

  @Test
  void parsesEverySupportedOperationIntoClosedTypes() throws Exception {
    TransformationConfig config = parser.parse(validConfiguration());

    assertThat(config.schemaVersion()).isEqualTo(TransformationSchemaVersion.V1_0);
    assertThat(config.operations())
        .hasSize(10)
        .extracting(operation -> operation.getClass().getSimpleName())
        .containsExactly(
            "CreateObject",
            "AddConstant",
            "ConditionalField",
            "Filter",
            "EnumMap",
            "ScaleFixedDecimal",
            "Cast",
            "Rename",
            "Remove",
            "Select");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidConfigurations")
  void rejectsMalformedAndUnsupportedConfigurations(
      String description,
      String json,
      ConfigurationErrorCode expectedCode,
      String expectedLocation) {
    assertThatThrownBy(() -> parser.parse(json))
        .isInstanceOfSatisfying(
            TransformationConfigException.class,
            exception -> {
              assertThat(exception.code()).isEqualTo(expectedCode);
              assertThat(exception.location()).isEqualTo(expectedLocation);
            });
  }

  @Test
  void executableExpressionsCannotBeRepresented() {
    String json =
        """
        {
          "schemaVersion": "1.0",
          "operations": [{
            "op": "filter",
            "condition": {
              "type": "comparison",
              "field": "payload.quantity",
              "operator": "GT",
              "value": {"type": "INT64", "value": 10},
              "expression": "Runtime.getRuntime().exec('command')"
            }
          }]
        }
        """;

    assertThatThrownBy(() -> parser.parse(json))
        .isInstanceOfSatisfying(
            TransformationConfigException.class,
            exception -> {
              assertThat(exception.code()).isEqualTo(ConfigurationErrorCode.UNKNOWN_PROPERTY);
              assertThat(exception.location()).isEqualTo("$.operations[0].condition.expression");
            });
  }

  private static Stream<Arguments> invalidConfigurations() {
    return Stream.of(
        Arguments.of("malformed JSON", "{", ConfigurationErrorCode.MALFORMED_JSON, "$"),
        Arguments.of(
            "unsupported version",
            """
            {"schemaVersion":"2.0","operations":[{"op":"remove","path":"payload.price"}]}
            """,
            ConfigurationErrorCode.UNSUPPORTED_VERSION,
            "$.schemaVersion"),
        Arguments.of(
            "unknown operation",
            """
            {"schemaVersion":"1.0","operations":[{"op":"script","code":"event.price"}]}
            """,
            ConfigurationErrorCode.UNKNOWN_OPERATION,
            "$.operations[0].op"),
        Arguments.of(
            "unknown operation property",
            """
            {"schemaVersion":"1.0","operations":[{"op":"remove","path":"payload.price","eval":"x"}]}
            """,
            ConfigurationErrorCode.UNKNOWN_PROPERTY,
            "$.operations[0].eval"),
        Arguments.of(
            "forbidden field segment",
            """
            {"schemaVersion":"1.0","operations":[{"op":"remove","path":"payload.getClass"}]}
            """,
            ConfigurationErrorCode.INVALID_VALUE,
            "$.operations[0].path"),
        Arguments.of(
            "floating int64",
            """
            {"schemaVersion":"1.0","operations":[{"op":"add_constant","path":"tag","value":{"type":"INT64","value":1.5}}]}
            """,
            ConfigurationErrorCode.INVALID_VALUE,
            "$.operations[0].value.value"),
        Arguments.of(
            "unknown condition",
            """
            {"schemaVersion":"1.0","operations":[{"op":"filter","condition":{"type":"invoke","method":"x"}}]}
            """,
            ConfigurationErrorCode.UNKNOWN_CONDITION,
            "$.operations[0].condition.type"),
        Arguments.of(
            "duplicate JSON key",
            """
            {"schemaVersion":"1.0","schemaVersion":"1.0","operations":[{"op":"remove","path":"payload.price"}]}
            """,
            ConfigurationErrorCode.MALFORMED_JSON,
            "$"),
        Arguments.of(
            "unknown root property",
            """
            {"schemaVersion":"1.0","operations":[{"op":"remove","path":"payload.price"}],"engine":"javascript"}
            """,
            ConfigurationErrorCode.UNKNOWN_PROPERTY,
            "$.engine"));
  }

  static String validConfiguration() {
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
            {
              "op":"filter",
              "condition":{"type":"all","conditions":[
                {"type":"comparison","field":"payload.price","operator":"GT","value":{"type":"FIXED_DECIMAL","mantissa":0,"scale":2}},
                {"type":"not","condition":{"type":"comparison","field":"payload.side","operator":"EQ","value":{"type":"ENUM","value":"SELL"}}}
              ]}
            },
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
