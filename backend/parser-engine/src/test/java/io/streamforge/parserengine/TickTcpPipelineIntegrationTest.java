package io.streamforge.parserengine;

import static org.assertj.core.api.Assertions.assertThat;

import io.streamforge.common.model.InstrumentSymbol;
import io.streamforge.stp.protocol.ParsedStpFrame;
import io.streamforge.stp.protocol.StpMessage;
import io.streamforge.stp.protocol.StpParseEvent;
import io.streamforge.ticksimulator.TickSimulationConfig;
import io.streamforge.ticksimulator.TickTcpServer;
import io.streamforge.ticksimulator.TickTcpServerConfig;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TickTcpPipelineIntegrationTest {

  @Test
  void streamsEveryConfiguredEventToTheIncrementalParserInOrder() throws Exception {
    TickSimulationConfig simulation =
        TickSimulationConfig.finite(
            9_001L, List.of(new InstrumentSymbol("AAPL"), new InstrumentSymbol("MSFT")), 64);
    List<String> serverReports = new ArrayList<>();
    List<StpParseEvent> parsedEvents = new ArrayList<>();

    try (TickTcpServer server =
        new TickTcpServer(
            new TickTcpServerConfig("127.0.0.1", 0, simulation, 0), serverReports::add)) {
      server.start();

      StpParserClientResult result =
          new StpTcpParserClient().parse("127.0.0.1", server.port(), parsedEvents::add);
      server.awaitFirstConnectionCompletion();

      assertThat(result.parsedFrames()).isEqualTo(64);
      assertThat(result.parseFailures()).isZero();
      assertThat(parsedEvents).hasSize(64).allMatch(ParsedStpFrame.class::isInstance);
      for (int index = 1; index < parsedEvents.size(); index++) {
        long previousSequence = sequenceNumber(parsedEvents.get(index - 1));
        long currentSequence = sequenceNumber(parsedEvents.get(index));
        assertThat(currentSequence).isGreaterThan(previousSequence);
      }
      assertThat(serverReports).noneMatch(report -> report.startsWith("connection error"));
    }
  }

  private static long sequenceNumber(StpParseEvent event) {
    return ((StpMessage) ((ParsedStpFrame) event).result()).header().sequenceNumber().value();
  }
}
