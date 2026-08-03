package io.streamforge.common.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MarketEventValidationTest {

  @ParameterizedTest
  @MethodSource("validPayloads")
  void exposesStablePayloadTypes(MarketEvent payload, CanonicalEventType expectedType) {
    assertThat(payload.type()).isEqualTo(expectedType);
  }

  @ParameterizedTest
  @MethodSource("invalidPayloadFactories")
  void rejectsMissingRequiredPayloadFields(Runnable factory) {
    assertThatIllegalArgumentException().isThrownBy(factory::run);
  }

  @ParameterizedTest
  @MethodSource("validQuotes")
  void representsOneSidedAndTwoSidedQuotes(Optional<QuoteLevel> bid, Optional<QuoteLevel> ask) {
    Quote quote = new Quote(bid, ask);

    assertThat(quote.bid()).isEqualTo(bid);
    assertThat(quote.ask()).isEqualTo(ask);
    assertThat(quote.type()).isEqualTo(CanonicalEventType.QUOTE);
  }

  private static Stream<Arguments> validPayloads() {
    QuoteLevel level = new QuoteLevel(new FixedDecimal(12_345, 2), new Quantity(100));
    return Stream.of(
        Arguments.of(
            new OrderAdded(new OrderId(1), Side.BUY, new Quantity(1), new FixedDecimal(1, 0)),
            CanonicalEventType.ORDER_ADDED),
        Arguments.of(
            new OrderExecuted(new OrderId(1), new Quantity(1)), CanonicalEventType.ORDER_EXECUTED),
        Arguments.of(
            new OrderCancelled(new OrderId(1), new Quantity(1)),
            CanonicalEventType.ORDER_CANCELLED),
        Arguments.of(
            new Trade(new TradeId(1), Optional.empty(), new Quantity(1), new FixedDecimal(1, 0)),
            CanonicalEventType.TRADE),
        Arguments.of(new Quote(Optional.of(level), Optional.empty()), CanonicalEventType.QUOTE));
  }

  private static Stream<Runnable> invalidPayloadFactories() {
    Quantity quantity = new Quantity(1);
    FixedDecimal price = new FixedDecimal(1, 0);
    OrderId orderId = new OrderId(1);
    TradeId tradeId = new TradeId(1);
    QuoteLevel level = new QuoteLevel(price, quantity);
    return Stream.of(
        () -> new OrderAdded(null, Side.BUY, quantity, price),
        () -> new OrderAdded(orderId, null, quantity, price),
        () -> new OrderAdded(orderId, Side.BUY, null, price),
        () -> new OrderAdded(orderId, Side.BUY, quantity, null),
        () -> new OrderExecuted(null, quantity),
        () -> new OrderExecuted(orderId, null),
        () -> new OrderCancelled(null, quantity),
        () -> new OrderCancelled(orderId, null),
        () -> new Trade(null, Optional.empty(), quantity, price),
        () -> new Trade(tradeId, null, quantity, price),
        () -> new Trade(tradeId, Optional.empty(), null, price),
        () -> new Trade(tradeId, Optional.empty(), quantity, null),
        () -> new QuoteLevel(null, quantity),
        () -> new QuoteLevel(price, null),
        () -> new Quote(null, Optional.of(level)),
        () -> new Quote(Optional.of(level), null),
        () -> new Quote(Optional.empty(), Optional.empty()));
  }

  private static Stream<Arguments> validQuotes() {
    QuoteLevel bid = new QuoteLevel(new FixedDecimal(100_00, 2), new Quantity(10));
    QuoteLevel ask = new QuoteLevel(new FixedDecimal(100_01, 2), new Quantity(12));
    return Stream.of(
        Arguments.of(Optional.of(bid), Optional.empty()),
        Arguments.of(Optional.empty(), Optional.of(ask)),
        Arguments.of(Optional.of(bid), Optional.of(ask)));
  }
}
