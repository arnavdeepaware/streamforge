package io.streamforge.ticksimulator;

import io.streamforge.common.model.EventTimestamp;
import io.streamforge.common.model.FixedDecimal;
import io.streamforge.common.model.InstrumentSymbol;
import io.streamforge.common.model.OrderId;
import io.streamforge.common.model.Quantity;
import io.streamforge.common.model.SequenceNumber;
import io.streamforge.common.model.Side;
import io.streamforge.common.model.TradeId;
import io.streamforge.stp.protocol.AddOrderMessage;
import io.streamforge.stp.protocol.CancelOrderMessage;
import io.streamforge.stp.protocol.ExecuteOrderMessage;
import io.streamforge.stp.protocol.FrameHeader;
import io.streamforge.stp.protocol.MessageType;
import io.streamforge.stp.protocol.StpMessage;
import io.streamforge.stp.protocol.TradeMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;

/** Deterministically produces logically consistent STP v1 tick events without network I/O. */
public final class StpTickEventGenerator {

  private final TickSimulationConfig config;
  private final SplittableRandom random;
  private final List<ActiveOrder> activeOrders = new ArrayList<>();
  private long nextSequence;
  private long nextTimestamp;
  private long nextOrderId = 1;
  private long nextTradeId = 1;
  private long generatedEventCount;
  private boolean sequenceExhausted;
  private boolean timestampExhausted;
  private boolean orderIdExhausted;
  private boolean tradeIdExhausted;

  public StpTickEventGenerator(TickSimulationConfig config) {
    if (config == null) {
      throw new IllegalArgumentException("config must not be null");
    }
    this.config = config;
    this.random = new SplittableRandom(config.randomSeed());
    this.nextSequence = config.initialSequenceNumber().value();
    this.nextTimestamp = config.initialTimestamp().nanosecondsSinceEpoch();
  }

  /** Returns the next event, or empty when a finite simulation has completed. */
  public Optional<StpMessage> next() {
    if (config.mode() instanceof FiniteSimulation finite
        && generatedEventCount >= finite.eventCount()) {
      return Optional.empty();
    }
    ensureCountersAvailable();

    StpMessage event =
        switch (selectEventType()) {
          case ADD_ORDER -> addOrder();
          case EXECUTE_ORDER -> executeOrder();
          case CANCEL_ORDER -> cancelOrder();
          case TRADE -> trade();
        };
    generatedEventCount++;
    advanceEnvelope();
    return Optional.of(event);
  }

  private MessageType selectEventType() {
    if (activeOrders.isEmpty()) {
      return MessageType.ADD_ORDER;
    }
    if (activeOrders.size() >= config.maximumOpenOrders()) {
      return selectClosingEventType();
    }
    return selectWeightedEventType(
        config.eventTypeDistribution().addOrderWeight(),
        config.eventTypeDistribution().executeOrderWeight(),
        config.eventTypeDistribution().cancelOrderWeight(),
        config.eventTypeDistribution().tradeWeight());
  }

  private MessageType selectClosingEventType() {
    EventTypeDistribution distribution = config.eventTypeDistribution();
    int closingWeight = distribution.executeOrderWeight() + distribution.cancelOrderWeight();
    if (closingWeight == 0) {
      return MessageType.CANCEL_ORDER;
    }
    return random.nextInt(closingWeight) < distribution.executeOrderWeight()
        ? MessageType.EXECUTE_ORDER
        : MessageType.CANCEL_ORDER;
  }

  private MessageType selectWeightedEventType(int add, int execute, int cancel, int trade) {
    int selected =
        random.nextInt(Math.addExact(Math.addExact(add, execute), Math.addExact(cancel, trade)));
    if (selected < add) {
      return MessageType.ADD_ORDER;
    }
    selected -= add;
    if (selected < execute) {
      return MessageType.EXECUTE_ORDER;
    }
    selected -= execute;
    return selected < cancel ? MessageType.CANCEL_ORDER : MessageType.TRADE;
  }

  private AddOrderMessage addOrder() {
    if (orderIdExhausted) {
      throw new IllegalStateException("order ID range is exhausted");
    }
    ActiveOrder order =
        new ActiveOrder(
            new OrderId(nextOrderId),
            config.symbols().get(random.nextInt(config.symbols().size())),
            random.nextBoolean() ? Side.BUY : Side.SELL,
            new Quantity(random.nextLong(1, 1_001)),
            new FixedDecimal(random.nextLong(10_000, 100_001), 2));
    activeOrders.add(order);
    advanceOrderId();
    return new AddOrderMessage(
        header(MessageType.ADD_ORDER),
        order.id(),
        order.symbol(),
        order.side(),
        order.remainingQuantity(),
        order.price());
  }

  private ExecuteOrderMessage executeOrder() {
    ActiveOrder order = activeOrder();
    Quantity quantity = selectedQuantity(order.remainingQuantity());
    reduceOrRemove(order, quantity);
    return new ExecuteOrderMessage(header(MessageType.EXECUTE_ORDER), order.id(), quantity);
  }

  private CancelOrderMessage cancelOrder() {
    ActiveOrder order = activeOrder();
    Quantity quantity = selectedQuantity(order.remainingQuantity());
    reduceOrRemove(order, quantity);
    return new CancelOrderMessage(header(MessageType.CANCEL_ORDER), order.id(), quantity);
  }

  private TradeMessage trade() {
    if (tradeIdExhausted) {
      throw new IllegalStateException("trade ID range is exhausted");
    }
    ActiveOrder order = activeOrder();
    Quantity quantity = selectedQuantity(order.remainingQuantity());
    TradeMessage message =
        new TradeMessage(
            header(MessageType.TRADE),
            new TradeId(nextTradeId),
            order.symbol(),
            order.side() == Side.BUY ? Side.SELL : Side.BUY,
            quantity,
            order.price());
    advanceTradeId();
    return message;
  }

  private ActiveOrder activeOrder() {
    return activeOrders.get(random.nextInt(activeOrders.size()));
  }

  private Quantity selectedQuantity(Quantity remaining) {
    return new Quantity(random.nextLong(1, remaining.value() + 1));
  }

  private void reduceOrRemove(ActiveOrder order, Quantity quantity) {
    long remaining = order.remainingQuantity().value() - quantity.value();
    if (remaining == 0) {
      activeOrders.remove(order);
    } else {
      order.remainingQuantity = new Quantity(remaining);
    }
  }

  private FrameHeader header(MessageType type) {
    return new FrameHeader(
        type.encodedLength(),
        type,
        new SequenceNumber(nextSequence),
        new EventTimestamp(nextTimestamp));
  }

  private void ensureCountersAvailable() {
    if (sequenceExhausted) {
      throw new IllegalStateException("sequence number range is exhausted");
    }
    if (timestampExhausted) {
      throw new IllegalStateException("timestamp range is exhausted");
    }
  }

  private void advanceEnvelope() {
    sequenceExhausted = nextSequence == Long.MAX_VALUE;
    if (!sequenceExhausted) {
      nextSequence++;
    }
    timestampExhausted = nextTimestamp > Long.MAX_VALUE - config.timestampStepNanoseconds();
    if (!timestampExhausted) {
      nextTimestamp += config.timestampStepNanoseconds();
    }
  }

  private void advanceOrderId() {
    orderIdExhausted = nextOrderId == Long.MAX_VALUE;
    if (!orderIdExhausted) {
      nextOrderId++;
    }
  }

  private void advanceTradeId() {
    tradeIdExhausted = nextTradeId == Long.MAX_VALUE;
    if (!tradeIdExhausted) {
      nextTradeId++;
    }
  }

  private static final class ActiveOrder {
    private final OrderId id;
    private final InstrumentSymbol symbol;
    private final Side side;
    private Quantity remainingQuantity;
    private final FixedDecimal price;

    private ActiveOrder(
        OrderId id,
        InstrumentSymbol symbol,
        Side side,
        Quantity remainingQuantity,
        FixedDecimal price) {
      this.id = id;
      this.symbol = symbol;
      this.side = side;
      this.remainingQuantity = remainingQuantity;
      this.price = price;
    }

    private OrderId id() {
      return id;
    }

    private InstrumentSymbol symbol() {
      return symbol;
    }

    private Side side() {
      return side;
    }

    private Quantity remainingQuantity() {
      return remainingQuantity;
    }

    private FixedDecimal price() {
      return price;
    }
  }
}
