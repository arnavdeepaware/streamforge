package io.streamforge.transform.blueprint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.streamforge.common.model.CanonicalEvent;
import io.streamforge.common.model.CanonicalSchemaVersion;
import io.streamforge.common.model.EventMetadata;
import io.streamforge.common.model.EventTimestamp;
import io.streamforge.common.model.FixedDecimal;
import io.streamforge.common.model.InstrumentReference;
import io.streamforge.common.model.InstrumentSymbol;
import io.streamforge.common.model.OrderAdded;
import io.streamforge.common.model.OrderId;
import io.streamforge.common.model.Quantity;
import io.streamforge.common.model.RawEventReference;
import io.streamforge.common.model.SequenceNumber;
import io.streamforge.common.model.Side;
import io.streamforge.common.model.SourceIdentity;
import io.streamforge.common.model.Venue;
import io.streamforge.transform.compile.CanonicalTransformationFields;
import io.streamforge.transform.compile.CompiledTransformation;
import io.streamforge.transform.compile.TransformationCompiler;
import io.streamforge.transform.config.TransformationConfigParser;
import io.streamforge.transform.execute.CanonicalEventDocument;
import io.streamforge.transform.execute.TransformationExecutor;
import io.streamforge.transform.execute.TransformationResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OutputBlueprintServiceTest {
  private final OutputBlueprintService service = new OutputBlueprintService();

  @Test
  void rendersTheAaplExampleAsTypedNestedOutput() throws Exception {
    CompiledOutputBlueprint blueprint = service.compile(example(), Optional.empty());

    BlueprintPreviewResult result = service.preview(blueprint, aapl(), Optional.empty());

    assertThat(result).isInstanceOf(BlueprintPreviewResult.Rendered.class);
    Map<String, Object> root = ((BlueprintPreviewResult.Rendered) result).document().root();
    Map<String, Object> event = object(root, "event");
    Map<String, Object> order = object(root, "order");
    assertThat(event.get("symbol")).isEqualTo("AAPL");
    assertThat(event.get("sequence")).isEqualTo(1L);
    assertThat(event.get("occurredAt")).isInstanceOf(String.class);
    assertThat(order.get("id")).isEqualTo(1001L);
    assertThat(order.get("quantity")).isEqualTo(100L);
    assertThat(order.get("price")).isEqualTo("123.45");
    assertThat(root.get("flags")).isEqualTo(java.util.List.of("market-data", true));
    assertThat(root.get("version")).isEqualTo(1L);
  }

  @Test
  void preservesExactDecimalLiteralsAndOmitsFalseConditionalFields() throws Exception {
    CompiledOutputBlueprint blueprint =
        service.compile(
            """
            {"schemaVersion":"1.0","output":{"kind":"object","fields":{
              "price":{"kind":"literal","value":123.4500},
              "optional":{"kind":"conditional","condition":{"type":"comparison","source":"canonical","path":"payload.side","operator":"EQ","value":{"type":"ENUM","value":"SELL"}},"value":{"kind":"literal","value":"omit"}}
            }}}
            """,
            Optional.empty());

    BlueprintPreviewResult result = service.preview(blueprint, aapl(), Optional.empty());

    assertThat(result).isInstanceOf(BlueprintPreviewResult.Rendered.class);
    Map<String, Object> root = ((BlueprintPreviewResult.Rendered) result).document().root();
    assertThat(root.get("price")).isEqualTo(new FixedDecimal(1_234_500, 4));
    assertThat(root).doesNotContainKey("optional");
  }

  @Test
  void validatesAndRendersTransformedReferences() throws Exception {
    CompiledTransformation transformation =
        new TransformationCompiler()
            .compile(
                new TransformationConfigParser()
                    .parse(
                        """
                        {"schemaVersion":"1.0","operations":[{"op":"rename","from":"instrument.symbol","to":"instrument.ticker"}]}
                        """),
                CanonicalTransformationFields.v1());
    TransformationResult transformed = new TransformationExecutor(transformation).execute(aapl());
    CanonicalEventDocument document = ((TransformationResult.Transformed) transformed).document();
    CompiledOutputBlueprint blueprint =
        service.compile(
            """
            {"schemaVersion":"1.0","output":{"kind":"object","fields":{"ticker":{"kind":"reference","source":"transformed","path":"instrument.ticker"}}}}
            """,
            Optional.of(transformation));

    BlueprintPreviewResult result = service.preview(blueprint, aapl(), Optional.of(document));

    assertThat(((BlueprintPreviewResult.Rendered) result).document().root())
        .containsEntry("ticker", "AAPL");
  }

  @Test
  void reportsMissingVariantFieldsWithoutReturningPartialOutput() throws Exception {
    CompiledOutputBlueprint blueprint = service.compile(example(), Optional.empty());
    CanonicalEvent incompatible =
        new CanonicalEvent(
            metadata(),
            new InstrumentReference(new InstrumentSymbol("MSFT")),
            new io.streamforge.common.model.Trade(
                new io.streamforge.common.model.TradeId(1),
                Optional.empty(),
                new Quantity(1),
                new FixedDecimal(1, 0)));

    BlueprintPreviewResult result = service.preview(blueprint, incompatible, Optional.empty());

    assertThat(result).isInstanceOf(BlueprintPreviewResult.Failed.class);
    assertThat(((BlueprintPreviewResult.Failed) result).failure().location())
        .isEqualTo("$.order.id");
  }

  @Test
  void rejectsUnknownReferencesAndStaticLimitsBeforePreview() {
    assertThatThrownBy(
            () ->
                service.compile(
                    """
                    {"schemaVersion":"1.0","output":{"kind":"object","fields":{"x":{"kind":"reference","source":"canonical","path":"payload.missing"}}}}
                    """,
                    Optional.empty()))
        .isInstanceOfSatisfying(
            OutputBlueprintValidationException.class,
            exception ->
                assertThat(exception.code()).isEqualTo(BlueprintValidationCode.UNKNOWN_FIELD));
    OutputBlueprintService limited = new OutputBlueprintService(new OutputBlueprintLimits(16, 1));
    assertThatThrownBy(() -> limited.compile(example(), Optional.empty()))
        .isInstanceOfSatisfying(
            OutputBlueprintValidationException.class,
            exception ->
                assertThat(exception.code()).isEqualTo(BlueprintValidationCode.LIMIT_EXCEEDED));
  }

  @Test
  void rejectsUnknownConditionPropertiesWithTheirLocation() {
    assertThatThrownBy(
            () ->
                service.compile(
                    """
                    {"schemaVersion":"1.0","output":{"kind":"object","fields":{"x":{"kind":"conditional","condition":{"type":"comparison","source":"canonical","path":"payload.side","operator":"EQ","value":{"type":"ENUM","value":"BUY","expression":"ignored"}},"value":{"kind":"literal","value":true}}}}}
                    """,
                    Optional.empty()))
        .isInstanceOfSatisfying(
            OutputBlueprintConfigException.class,
            exception -> {
              assertThat(exception.code()).isEqualTo(BlueprintConfigErrorCode.UNKNOWN_PROPERTY);
              assertThat(exception.location())
                  .isEqualTo("$.output.fields.x.condition.value.expression");
            });
  }

  private static Map<String, Object> object(Map<String, Object> root, String key) {
    @SuppressWarnings("unchecked")
    Map<String, Object> value = (Map<String, Object>) root.get(key);
    return value;
  }

  private static String example() throws Exception {
    Path direct = Path.of("schemas/examples/aapl-output-blueprint-v1.json");
    Path module = Path.of("../../schemas/examples/aapl-output-blueprint-v1.json");
    return Files.readString(Files.exists(direct) ? direct : module);
  }

  private static CanonicalEvent aapl() {
    return new CanonicalEvent(
        metadata(),
        new InstrumentReference(new InstrumentSymbol("AAPL")),
        new OrderAdded(
            new OrderId(1001), Side.BUY, new Quantity(100), new FixedDecimal(12_345, 2)));
  }

  private static EventMetadata metadata() {
    return EventMetadata.create(
        CanonicalSchemaVersion.V1_0,
        new SourceIdentity("blueprint/test"),
        new Venue("XNAS"),
        new EventTimestamp(1_000_000_000L),
        Optional.empty(),
        new SequenceNumber(1),
        new RawEventReference("blueprint:test"));
  }
}
