package io.streamforge.transform.execute;

import static org.assertj.core.api.Assertions.assertThat;

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
import io.streamforge.common.model.Trade;
import io.streamforge.common.model.TradeId;
import io.streamforge.common.model.Venue;
import io.streamforge.transform.compile.CanonicalTransformationFields;
import io.streamforge.transform.compile.CompiledTransformation;
import io.streamforge.transform.compile.TransformationCompiler;
import io.streamforge.transform.config.FieldPath;
import io.streamforge.transform.config.TransformationConfigParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TransformationExecutorTest {

  private final TransformationConfigParser parser = new TransformationConfigParser();
  private final TransformationCompiler compiler = new TransformationCompiler();

  @Test
  void executesTheExampleRuleFileInDeclaredOrderWithoutMutatingTheSourceEvent() throws Exception {
    CanonicalEvent event = trade(25, new FixedDecimal(25_005, 2), Side.SELL);

    TransformationResult result = executor(exampleConfiguration()).execute(event);

    assertThat(result).isInstanceOf(TransformationResult.Transformed.class);
    CanonicalEventDocument document = ((TransformationResult.Transformed) result).document();
    assertThat(document.valueAt(new FieldPath("instrument.ticker"))).contains("MSFT");
    assertThat(document.valueAt(new FieldPath("instrument.symbol"))).isEmpty();
    assertThat(document.valueAt(new FieldPath("payload.aggressorSide"))).isEmpty();
    assertThat(document.valueAt(new FieldPath("payload.price")))
        .contains(new FixedDecimal(2_500_500, 4));
    assertThat(document.valueAt(new FieldPath("payload.quantity")))
        .contains(new FixedDecimal(25, 0));
    assertThat(document.valueAt(new FieldPath("enrichment.pipeline"))).contains("trade-example");
    assertThat(document.valueAt(new FieldPath("enrichment.large"))).contains(false);
    assertThat(document.valueAt(new FieldPath("metadata.exchangeTimestamp")))
        .contains(Long.MAX_VALUE);
    assertThat(event.instrument().symbol().value()).isEqualTo("MSFT");
    assertThat(((Trade) event.payload()).price()).isEqualTo(new FixedDecimal(25_005, 2));
    assertThat(((Trade) event.payload()).quantity()).isEqualTo(new Quantity(25));
  }

  @Test
  void returnsFilteredWhenARestrictedConditionIsFalse() throws Exception {
    TransformationExecutor executor =
        executor(
            """
            {"schemaVersion":"1.0","operations":[
              {"op":"filter","condition":{"type":"all","conditions":[
                {"type":"comparison","field":"payload.quantity","operator":"GT","value":{"type":"INT64","value":100}},
                {"type":"not","condition":{"type":"comparison","field":"payload.price","operator":"LT","value":{"type":"FIXED_DECIMAL","mantissa":0,"scale":2}}}
              ]}}
            ]}
            """);

    TransformationResult result =
        executor.execute(trade(25, new FixedDecimal(25_005, 2), Side.SELL));

    assertThat(result).isInstanceOf(TransformationResult.Filtered.class);
  }

  @Test
  void appliesNestedObjectConstantAndExactCast() throws Exception {
    TransformationExecutor executor =
        executor(
            """
            {"schemaVersion":"1.0","operations":[
              {"op":"create_object","path":"enrichment"},
              {"op":"add_constant","path":"enrichment.limit","value":{"type":"STRING","value":"123.4500"}},
              {"op":"cast","path":"enrichment.limit","toType":"FIXED_DECIMAL"}
            ]}
            """);

    TransformationResult result =
        executor.execute(trade(25, new FixedDecimal(25_005, 2), Side.SELL));

    assertThat(result).isInstanceOf(TransformationResult.Transformed.class);
    assertThat(
            ((TransformationResult.Transformed) result)
                .document()
                .valueAt(new FieldPath("enrichment.limit")))
        .contains(new FixedDecimal(1_234_500, 4));
  }

  @Test
  void mapsEnumsAndAppliesConditionalFields() throws Exception {
    TransformationExecutor executor =
        executor(
            """
            {"schemaVersion":"1.0","operations":[
              {"op":"create_object","path":"enrichment"},
              {"op":"enum_map","path":"payload.side","mapping":{"BUY":"B","SELL":"S"}},
              {"op":"conditional_field","path":"enrichment.large","condition":{"type":"comparison","field":"payload.quantity","operator":"GTE","value":{"type":"INT64","value":100}},"whenTrue":{"type":"BOOLEAN","value":true},"whenFalse":{"type":"BOOLEAN","value":false}}
            ]}
            """);

    TransformationResult result = executor.execute(addOrder(100));

    assertThat(result).isInstanceOf(TransformationResult.Transformed.class);
    CanonicalEventDocument document = ((TransformationResult.Transformed) result).document();
    assertThat(document.valueAt(new FieldPath("payload.side"))).contains("B");
    assertThat(document.valueAt(new FieldPath("enrichment.large"))).contains(true);
  }

  @Test
  void returnsAFieldSpecificFailureAndContinuesWithLaterEvents() throws Exception {
    TransformationExecutor executor =
        executor(
            """
            {"schemaVersion":"1.0","operations":[
              {"op":"enum_map","path":"payload.aggressorSide","mapping":{"BUY":"B","SELL":"S"}}
            ]}
            """);

    TransformationResult first = executor.execute(addOrder(100));
    TransformationResult second =
        executor.execute(trade(25, new FixedDecimal(25_005, 2), Side.BUY));

    assertThat(first).isInstanceOf(TransformationResult.Failed.class);
    TransformationFailure failure = ((TransformationResult.Failed) first).failure();
    assertThat(failure.code()).isEqualTo(TransformationFailureCode.MISSING_FIELD);
    assertThat(failure.operationName()).isEqualTo("enum_map");
    assertThat(failure.fieldPath()).isEqualTo("payload.aggressorSide");
    assertThat(second).isInstanceOf(TransformationResult.Transformed.class);
    assertThat(
            ((TransformationResult.Transformed) second)
                .document()
                .valueAt(new FieldPath("payload.aggressorSide")))
        .contains("B");
  }

  @Test
  void rejectsScaleChangesThatWouldLoseFixedPointPrecision() throws Exception {
    TransformationExecutor executor =
        executor(
            """
            {"schemaVersion":"1.0","operations":[
              {"op":"scale_fixed_decimal","path":"payload.price","targetScale":1}
            ]}
            """);

    TransformationResult result =
        executor.execute(trade(25, new FixedDecimal(25_005, 2), Side.SELL));

    assertThat(result).isInstanceOf(TransformationResult.Failed.class);
    TransformationFailure failure = ((TransformationResult.Failed) result).failure();
    assertThat(failure.code()).isEqualTo(TransformationFailureCode.PRECISION_LOSS);
    assertThat(failure.operationName()).isEqualTo("scale_fixed_decimal");
    assertThat(failure.fieldPath()).isEqualTo("payload.price");
  }

  @Test
  void enforcesConfiguredOperationCountLimit() throws Exception {
    TransformationExecutor executor =
        executor(
            """
            {"schemaVersion":"1.0","operations":[
              {"op":"create_object","path":"enrichment"},
              {"op":"add_constant","path":"enrichment.flag","value":{"type":"BOOLEAN","value":true}}
            ]}
            """,
            new TransformationExecutionLimits(16, 1, 512));

    assertFailureCode(
        executor.execute(addOrder(100)),
        TransformationFailureCode.OPERATION_LIMIT_EXCEEDED,
        "plan",
        "$");
  }

  @Test
  void enforcesConfiguredNestingDepthLimit() throws Exception {
    TransformationExecutor executor =
        executor(
            """
            {"schemaVersion":"1.0","operations":[
              {"op":"create_object","path":"enrichment"},
              {"op":"create_object","path":"enrichment.inner"},
              {"op":"create_object","path":"enrichment.inner.deep"}
            ]}
            """,
            new TransformationExecutionLimits(2, 256, 512));

    assertFailureCode(
        executor.execute(addOrder(100)),
        TransformationFailureCode.NESTING_DEPTH_EXCEEDED,
        "create_object",
        "enrichment.inner.deep");
  }

  @Test
  void enforcesConfiguredOutputFieldCountLimit() throws Exception {
    TransformationExecutor executor =
        executor(
            """
            {"schemaVersion":"1.0","operations":[
              {"op":"create_object","path":"enrichment"}
            ]}
            """,
            new TransformationExecutionLimits(16, 256, 18));

    assertFailureCode(
        executor.execute(addOrder(100)),
        TransformationFailureCode.OUTPUT_FIELD_LIMIT_EXCEEDED,
        "create_object",
        "enrichment");
  }

  private void assertFailureCode(
      TransformationResult result,
      TransformationFailureCode expectedCode,
      String operationName,
      String fieldPath) {
    assertThat(result).isInstanceOf(TransformationResult.Failed.class);
    TransformationFailure failure = ((TransformationResult.Failed) result).failure();
    assertThat(failure.code()).isEqualTo(expectedCode);
    assertThat(failure.operationName()).isEqualTo(operationName);
    assertThat(failure.fieldPath()).isEqualTo(fieldPath);
  }

  private TransformationExecutor executor(String json) throws Exception {
    return executor(json, TransformationExecutionLimits.DEFAULT);
  }

  private TransformationExecutor executor(String json, TransformationExecutionLimits limits)
      throws Exception {
    CompiledTransformation compiled =
        compiler.compile(parser.parse(json), CanonicalTransformationFields.v1());
    return new TransformationExecutor(compiled, limits);
  }

  private String exampleConfiguration() throws IOException {
    Path direct = Path.of("schemas/examples/transformation-v1-trade-example.json");
    Path moduleRelative = Path.of("../../schemas/examples/transformation-v1-trade-example.json");
    Path file = Files.exists(direct) ? direct : moduleRelative;
    return Files.readString(file);
  }

  private static CanonicalEvent addOrder(long quantity) {
    return new CanonicalEvent(
        metadata("test/add"),
        new InstrumentReference(new InstrumentSymbol("AAPL")),
        new OrderAdded(
            new OrderId(1), Side.BUY, new Quantity(quantity), new FixedDecimal(12_345, 2)));
  }

  private static CanonicalEvent trade(long quantity, FixedDecimal price, Side aggressorSide) {
    return new CanonicalEvent(
        metadata("test/trade"),
        new InstrumentReference(new InstrumentSymbol("MSFT")),
        new Trade(new TradeId(2), Optional.of(aggressorSide), new Quantity(quantity), price));
  }

  private static EventMetadata metadata(String sourceValue) {
    return EventMetadata.create(
        CanonicalSchemaVersion.V1_0,
        new SourceIdentity(sourceValue),
        new Venue("XNAS"),
        new EventTimestamp(Long.MAX_VALUE),
        Optional.empty(),
        new SequenceNumber(1),
        new RawEventReference("test:raw"));
  }
}
