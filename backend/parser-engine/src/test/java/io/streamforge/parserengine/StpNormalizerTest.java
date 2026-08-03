package io.streamforge.parserengine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import io.streamforge.common.model.CanonicalEvent;
import io.streamforge.common.model.CanonicalSchemaVersion;
import io.streamforge.common.model.EventMetadata;
import io.streamforge.common.model.EventTimestamp;
import io.streamforge.common.model.FixedDecimal;
import io.streamforge.common.model.InstrumentReference;
import io.streamforge.common.model.InstrumentSymbol;
import io.streamforge.common.model.OrderAdded;
import io.streamforge.common.model.OrderCancelled;
import io.streamforge.common.model.OrderExecuted;
import io.streamforge.common.model.OrderId;
import io.streamforge.common.model.Quantity;
import io.streamforge.common.model.RawEventReference;
import io.streamforge.common.model.SequenceNumber;
import io.streamforge.common.model.Side;
import io.streamforge.common.model.SourceIdentity;
import io.streamforge.common.model.Trade;
import io.streamforge.common.model.TradeId;
import io.streamforge.common.model.Venue;
import io.streamforge.stp.protocol.AddOrderMessage;
import io.streamforge.stp.protocol.CancelOrderMessage;
import io.streamforge.stp.protocol.ExecuteOrderMessage;
import io.streamforge.stp.protocol.FrameHeader;
import io.streamforge.stp.protocol.MessageType;
import io.streamforge.stp.protocol.StpDecodeResult;
import io.streamforge.stp.protocol.StpMessage;
import io.streamforge.stp.protocol.TradeMessage;
import io.streamforge.stp.protocol.UnknownMessageFrame;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class StpNormalizerTest {

  private final StpNormalizer normalizer = new StpNormalizer();

  @ParameterizedTest
  @MethodSource("stpMessages")
  void normalizesEveryStpV1MessageWithoutLosingFields(
      StpMessage message, InstrumentReference resolvedInstrument, CanonicalEvent expected) {
    StpNormalizationResult result = normalizer.normalize(message, context(resolvedInstrument));

    assertThat(result).isEqualTo(new NormalizedStpEvent(expected));
  }

  @ParameterizedTest
  @MethodSource("priceAndTimestampBoundaries")
  void preservesExactFixedPointPricesAndNanosecondTimestamps(
      StpMessage message, InstrumentReference resolvedInstrument) {
    NormalizedStpEvent result =
        (NormalizedStpEvent) normalizer.normalize(message, context(resolvedInstrument));

    assertThat(result.event().metadata().exchangeTimestamp())
        .isEqualTo(message.header().eventTimestamp());
    assertThat(result.event().metadata().sequenceNumber())
        .isEqualTo(message.header().sequenceNumber());
    if (message instanceof AddOrderMessage addOrder) {
      OrderAdded payload = (OrderAdded) result.event().payload();
      assertThat(payload.price()).isEqualTo(addOrder.price());
      assertThat(payload.price().mantissa()).isEqualTo(Long.MIN_VALUE);
      assertThat(payload.price().scale()).isEqualTo(FixedDecimal.MAX_SCALE);
    }
    if (message instanceof TradeMessage trade) {
      Trade payload = (Trade) result.event().payload();
      assertThat(payload.price()).isEqualTo(trade.price());
      assertThat(payload.price().mantissa()).isEqualTo(Long.MAX_VALUE);
      assertThat(payload.price().scale()).isZero();
    }
  }

  @ParameterizedTest
  @MethodSource("messages")
  void generatesDeterministicEvents(StpMessage message) {
    StpNormalizationContext context = context(aapl());

    NormalizedStpEvent first = (NormalizedStpEvent) normalizer.normalize(message, context);
    NormalizedStpEvent second = (NormalizedStpEvent) normalizer.normalize(message, context);
    NormalizedStpEvent differentSource =
        (NormalizedStpEvent)
            normalizer.normalize(
                message,
                new StpNormalizationContext(
                    new SourceIdentity("other-source/session-1"),
                    new Venue("XNAS"),
                    Optional.of(new EventTimestamp(1_000_000_999L)),
                    new RawEventReference("capture:frame-1"),
                    ignored -> Optional.of(aapl())));

    assertThat(first.event()).isEqualTo(second.event());
    assertThat(first.event().metadata().eventId())
        .isNotEqualTo(differentSource.event().metadata().eventId());
  }

  @ParameterizedTest
  @MethodSource("unresolvableOrderMessages")
  void reportsMissingInstrumentsForOrderMessagesWithoutOnWireSymbols(StpMessage message) {
    StpNormalizationResult result = normalizer.normalize(message, context(null));

    assertThat(result)
        .isEqualTo(
            new StpNormalizationFailure(
                StpNormalizationFailureReason.INSTRUMENT_NOT_RESOLVED,
                "no instrument is known for STP order " + orderId(message)));
  }

  @ParameterizedTest
  @MethodSource("invalidInput")
  void returnsTypedFailuresForInvalidInputOrContext(
      StpDecodeResult decodedFrame,
      StpNormalizationContext context,
      StpNormalizationFailureReason expectedReason) {
    StpNormalizationResult result = normalizer.normalize(decodedFrame, context);

    assertThat(result).isInstanceOf(StpNormalizationFailure.class);
    assertThat(((StpNormalizationFailure) result).reason()).isEqualTo(expectedReason);
  }

  @ParameterizedTest
  @MethodSource("orderMessages")
  void returnsTypedFailuresWhenAnOrderResolverMisbehaves(StpMessage message) {
    StpNormalizationContext context =
        new StpNormalizationContext(
            new SourceIdentity("stp/source-1"),
            new Venue("XNAS"),
            Optional.empty(),
            new RawEventReference("capture:frame-1"),
            ignored -> null);

    StpNormalizationResult result = normalizer.normalize(message, context);

    assertThat(result)
        .isEqualTo(
            new StpNormalizationFailure(
                StpNormalizationFailureReason.NORMALIZATION_ERROR,
                "order instrument resolver must not return null"));
  }

  @ParameterizedTest
  @MethodSource("invalidContexts")
  void rejectsIncompleteContextAtConstruction(
      SourceIdentity source,
      Venue venue,
      Optional<EventTimestamp> receiveTimestamp,
      RawEventReference rawEventReference,
      StpOrderInstrumentResolver resolver) {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new StpNormalizationContext(
                    source, venue, receiveTimestamp, rawEventReference, resolver));
  }

  private static Stream<Arguments> stpMessages() {
    return Stream.of(
        Arguments.of(
            addOrder(1, 1_000_000_000L),
            aapl(),
            expected(
                1,
                1_000_000_000L,
                aapl(),
                new OrderAdded(
                    new OrderId(1001), Side.BUY, new Quantity(100), new FixedDecimal(12_345, 2)))),
        Arguments.of(
            executeOrder(2, 1_000_000_100L),
            aapl(),
            expected(
                2, 1_000_000_100L, aapl(), new OrderExecuted(new OrderId(1001), new Quantity(40)))),
        Arguments.of(
            cancelOrder(3, 1_000_000_200L),
            aapl(),
            expected(
                3,
                1_000_000_200L,
                aapl(),
                new OrderCancelled(new OrderId(1001), new Quantity(60)))),
        Arguments.of(
            trade(4, 1_000_000_300L),
            msft(),
            expected(
                4,
                1_000_000_300L,
                msft(),
                new Trade(
                    new TradeId(5001),
                    Optional.of(Side.SELL),
                    new Quantity(25),
                    new FixedDecimal(25_005, 2)))));
  }

  private static Stream<Arguments> priceAndTimestampBoundaries() {
    return Stream.of(
        Arguments.of(
            new AddOrderMessage(
                header(MessageType.ADD_ORDER, Long.MAX_VALUE, Long.MAX_VALUE),
                new OrderId(Long.MAX_VALUE),
                new InstrumentSymbol("AAPL"),
                Side.BUY,
                new Quantity(Quantity.MAX_VALUE),
                new FixedDecimal(Long.MIN_VALUE, FixedDecimal.MAX_SCALE)),
            aapl()),
        Arguments.of(
            new TradeMessage(
                header(MessageType.TRADE, Long.MAX_VALUE, Long.MAX_VALUE),
                new TradeId(Long.MAX_VALUE),
                new InstrumentSymbol("MSFT"),
                Side.SELL,
                new Quantity(Quantity.MAX_VALUE),
                new FixedDecimal(Long.MAX_VALUE, 0)),
            msft()));
  }

  private static Stream<StpMessage> messages() {
    return Stream.of(
        addOrder(1, 1_000_000_000L),
        executeOrder(2, 1_000_000_100L),
        cancelOrder(3, 1_000_000_200L),
        trade(4, 1_000_000_300L));
  }

  private static Stream<StpMessage> unresolvableOrderMessages() {
    return Stream.of(executeOrder(2, 1_000_000_100L), cancelOrder(3, 1_000_000_200L));
  }

  private static Stream<StpMessage> orderMessages() {
    return unresolvableOrderMessages();
  }

  private static Stream<Arguments> invalidInput() {
    return Stream.of(
        Arguments.of(null, context(aapl()), StpNormalizationFailureReason.INVALID_INPUT),
        Arguments.of(addOrder(1, 1), null, StpNormalizationFailureReason.INVALID_CONTEXT),
        Arguments.of(
            new UnknownMessageFrame(1, 'X'),
            context(aapl()),
            StpNormalizationFailureReason.UNSUPPORTED_MESSAGE_TYPE));
  }

  private static Stream<Arguments> invalidContexts() {
    StpOrderInstrumentResolver resolver = ignored -> Optional.of(aapl());
    return Stream.of(
        Arguments.of(
            null,
            new Venue("XNAS"),
            Optional.empty(),
            new RawEventReference("capture:1"),
            resolver),
        Arguments.of(
            new SourceIdentity("stp/source-1"),
            null,
            Optional.empty(),
            new RawEventReference("capture:1"),
            resolver),
        Arguments.of(
            new SourceIdentity("stp/source-1"),
            new Venue("XNAS"),
            null,
            new RawEventReference("capture:1"),
            resolver),
        Arguments.of(
            new SourceIdentity("stp/source-1"),
            new Venue("XNAS"),
            Optional.empty(),
            null,
            resolver),
        Arguments.of(
            new SourceIdentity("stp/source-1"),
            new Venue("XNAS"),
            Optional.empty(),
            new RawEventReference("capture:1"),
            null));
  }

  private static StpNormalizationContext context(InstrumentReference resolvedInstrument) {
    return new StpNormalizationContext(
        new SourceIdentity("stp/source-1"),
        new Venue("XNAS"),
        Optional.of(new EventTimestamp(1_000_000_999L)),
        new RawEventReference("capture:frame-1"),
        ignored -> Optional.ofNullable(resolvedInstrument));
  }

  private static CanonicalEvent expected(
      long sequence,
      long timestamp,
      InstrumentReference instrument,
      io.streamforge.common.model.MarketEvent payload) {
    return new CanonicalEvent(
        EventMetadata.create(
            CanonicalSchemaVersion.V1_0,
            new SourceIdentity("stp/source-1"),
            new Venue("XNAS"),
            new EventTimestamp(timestamp),
            Optional.of(new EventTimestamp(1_000_000_999L)),
            new SequenceNumber(sequence),
            new RawEventReference("capture:frame-1")),
        instrument,
        payload);
  }

  private static OrderId orderId(StpMessage message) {
    return switch (message) {
      case ExecuteOrderMessage execute -> execute.orderId();
      case CancelOrderMessage cancel -> cancel.orderId();
      default -> throw new IllegalArgumentException("message does not contain an order ID");
    };
  }

  private static AddOrderMessage addOrder(long sequence, long timestamp) {
    return new AddOrderMessage(
        header(MessageType.ADD_ORDER, sequence, timestamp),
        new OrderId(1001),
        new InstrumentSymbol("AAPL"),
        Side.BUY,
        new Quantity(100),
        new FixedDecimal(12_345, 2));
  }

  private static ExecuteOrderMessage executeOrder(long sequence, long timestamp) {
    return new ExecuteOrderMessage(
        header(MessageType.EXECUTE_ORDER, sequence, timestamp),
        new OrderId(1001),
        new Quantity(40));
  }

  private static CancelOrderMessage cancelOrder(long sequence, long timestamp) {
    return new CancelOrderMessage(
        header(MessageType.CANCEL_ORDER, sequence, timestamp), new OrderId(1001), new Quantity(60));
  }

  private static TradeMessage trade(long sequence, long timestamp) {
    return new TradeMessage(
        header(MessageType.TRADE, sequence, timestamp),
        new TradeId(5001),
        new InstrumentSymbol("MSFT"),
        Side.SELL,
        new Quantity(25),
        new FixedDecimal(25_005, 2));
  }

  private static FrameHeader header(MessageType type, long sequence, long timestamp) {
    return new FrameHeader(
        type.encodedLength(), type, new SequenceNumber(sequence), new EventTimestamp(timestamp));
  }

  private static InstrumentReference aapl() {
    return new InstrumentReference(new InstrumentSymbol("AAPL"));
  }

  private static InstrumentReference msft() {
    return new InstrumentReference(new InstrumentSymbol("MSFT"));
  }
}
