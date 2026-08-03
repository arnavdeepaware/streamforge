package io.streamforge.ticksimulator;

import static org.assertj.core.api.Assertions.assertThat;

import io.streamforge.common.model.EventTimestamp;
import io.streamforge.common.model.InstrumentSymbol;
import io.streamforge.common.model.OrderId;
import io.streamforge.common.model.SequenceNumber;
import io.streamforge.stp.protocol.AddOrderMessage;
import io.streamforge.stp.protocol.CancelOrderMessage;
import io.streamforge.stp.protocol.ExecuteOrderMessage;
import io.streamforge.stp.protocol.IncrementalStpDecoder;
import io.streamforge.stp.protocol.ParsedStpFrame;
import io.streamforge.stp.protocol.StpEncoder;
import io.streamforge.stp.protocol.StpMessage;
import io.streamforge.stp.protocol.StpParseEvent;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StpTickEventGeneratorTest {

  @Test
  void identicalSeedsProduceIdenticalEncodedFrames() {
    TickSimulationConfig config = config(7_531L, 200);

    assertThat(encodedEvents(config)).isEqualTo(encodedEvents(config));
  }

  @Test
  void finiteEventsDecodeAndHaveIncreasingSequencesAndMonotonicTimestamps() {
    TickSimulationConfig config = config(42L, 150);
    List<StpMessage> messages = generatedMessages(config);

    assertThat(messages).hasSize(150);
    for (int index = 1; index < messages.size(); index++) {
      StpMessage previous = messages.get(index - 1);
      StpMessage current = messages.get(index);
      assertThat(current.header().sequenceNumber().value())
          .isGreaterThan(previous.header().sequenceNumber().value());
      assertThat(current.header().eventTimestamp().nanosecondsSinceEpoch())
          .isGreaterThanOrEqualTo(previous.header().eventTimestamp().nanosecondsSinceEpoch());
    }

    IncrementalStpDecoder decoder = new IncrementalStpDecoder(49);
    List<StpParseEvent> parsed = decoder.feed(encodedEvents(config));
    assertThat(parsed).hasSize(150).allMatch(ParsedStpFrame.class::isInstance);
    assertThat(decoder.endOfInput()).isEmpty();
  }

  @Test
  void executeAndCancelMessagesOnlyReferenceOrdersWithRemainingQuantity() {
    TickSimulationConfig config =
        new TickSimulationConfig(
            9L,
            List.of(new InstrumentSymbol("AAPL")),
            new FiniteSimulation(300),
            new EventTypeDistribution(1, 10, 10, 1),
            new SequenceNumber(1),
            new EventTimestamp(0),
            1,
            20);
    Map<OrderId, Long> remainingByOrder = new HashMap<>();

    for (StpMessage message : generatedMessages(config)) {
      switch (message) {
        case AddOrderMessage add -> remainingByOrder.put(add.orderId(), add.quantity().value());
        case ExecuteOrderMessage execute ->
            assertAndReduce(
                remainingByOrder, execute.orderId(), execute.executedQuantity().value());
        case CancelOrderMessage cancel ->
            assertAndReduce(remainingByOrder, cancel.orderId(), cancel.canceledQuantity().value());
        default -> {
          // STP v1 trades have no order identifier and therefore do not alter this order map.
        }
      }
    }
  }

  @Test
  void continuousModeDoesNotEndWhileEventsAreRequested() {
    TickSimulationConfig config =
        new TickSimulationConfig(
            1L,
            List.of(new InstrumentSymbol("AAPL")),
            ContinuousSimulation.INSTANCE,
            EventTypeDistribution.defaults(),
            new SequenceNumber(1),
            new EventTimestamp(0),
            1,
            10);
    StpTickEventGenerator generator = new StpTickEventGenerator(config);

    for (int index = 0; index < 100; index++) {
      assertThat(generator.next()).isPresent();
    }
  }

  private static TickSimulationConfig config(long seed, long eventCount) {
    return TickSimulationConfig.finite(
        seed, List.of(new InstrumentSymbol("AAPL"), new InstrumentSymbol("MSFT")), eventCount);
  }

  private static byte[] encodedEvents(TickSimulationConfig config) {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    StpEncoder encoder = new StpEncoder();
    for (StpMessage message : generatedMessages(config)) {
      output.writeBytes(encoder.encode(message));
    }
    return output.toByteArray();
  }

  private static List<StpMessage> generatedMessages(TickSimulationConfig config) {
    StpTickEventGenerator generator = new StpTickEventGenerator(config);
    List<StpMessage> messages = new ArrayList<>();
    while (true) {
      StpMessage message = generator.next().orElse(null);
      if (message == null) {
        return messages;
      }
      messages.add(message);
    }
  }

  private static void assertAndReduce(
      Map<OrderId, Long> remainingByOrder, OrderId orderId, long quantity) {
    Long remaining = remainingByOrder.get(orderId);
    assertThat(remaining).as("remaining quantity for order %s", orderId).isNotNull();
    assertThat(quantity).isLessThanOrEqualTo(remaining);
    if (quantity == remaining) {
      remainingByOrder.remove(orderId);
    } else {
      remainingByOrder.put(orderId, remaining - quantity);
    }
  }
}
