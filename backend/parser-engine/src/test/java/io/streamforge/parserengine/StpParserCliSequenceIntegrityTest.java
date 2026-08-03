package io.streamforge.parserengine;

import static org.assertj.core.api.Assertions.assertThat;

import io.streamforge.common.model.EventTimestamp;
import io.streamforge.common.model.FixedDecimal;
import io.streamforge.common.model.InstrumentSymbol;
import io.streamforge.common.model.OrderId;
import io.streamforge.common.model.Quantity;
import io.streamforge.common.model.SequenceNumber;
import io.streamforge.common.model.Side;
import io.streamforge.stp.protocol.AddOrderMessage;
import io.streamforge.stp.protocol.FrameHeader;
import io.streamforge.stp.protocol.MessageType;
import io.streamforge.stp.protocol.StpEncoder;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class StpParserCliSequenceIntegrityTest {

  @Test
  void reportsVisibleIntegrityClassificationsForNonMonotonicFrames() throws Exception {
    try (ServerSocket server = new ServerSocket(0)) {
      AtomicReference<Throwable> serverFailure = new AtomicReference<>();
      Thread writer =
          Thread.ofPlatform()
              .start(
                  () -> {
                    try (Socket client = server.accept();
                        OutputStream output = client.getOutputStream()) {
                      StpEncoder encoder = new StpEncoder();
                      for (long sequence : new long[] {1, 3, 3, 2}) {
                        output.write(encoder.encode(addOrder(sequence)));
                      }
                    } catch (Throwable error) {
                      serverFailure.set(error);
                    }
                  });
      ByteArrayOutputStream standardOutput = new ByteArrayOutputStream();
      ByteArrayOutputStream standardError = new ByteArrayOutputStream();

      int exitCode =
          StpParserCli.run(
              new String[] {
                "--host",
                "127.0.0.1",
                "--port",
                Integer.toString(server.getLocalPort()),
                "--report-sequence-integrity",
                "--source",
                "fixture-session"
              },
              new PrintStream(standardOutput),
              new PrintStream(standardError));
      writer.join();

      assertThat(serverFailure.get()).isNull();
      assertThat(exitCode).isZero();
      assertThat(standardOutput.toString())
          .contains("status=EXPECTED")
          .contains("status=GAP_DETECTED")
          .contains("status=DUPLICATE")
          .contains("status=LATE_OR_OUT_OF_ORDER");
      assertThat(standardError.toString()).contains("4 parsed frames, 0 parse failures");
    }
  }

  private static AddOrderMessage addOrder(long sequence) {
    return new AddOrderMessage(
        new FrameHeader(
            MessageType.ADD_ORDER.encodedLength(),
            MessageType.ADD_ORDER,
            new SequenceNumber(sequence),
            new EventTimestamp(0)),
        new OrderId(sequence),
        new InstrumentSymbol("AAPL"),
        Side.BUY,
        new Quantity(1),
        new FixedDecimal(100, 2));
  }
}
