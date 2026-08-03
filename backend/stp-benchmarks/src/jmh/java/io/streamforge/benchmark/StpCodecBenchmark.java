package io.streamforge.benchmark;

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
import io.streamforge.stp.protocol.IncrementalStpDecoder;
import io.streamforge.stp.protocol.MessageType;
import io.streamforge.stp.protocol.StpDecoder;
import io.streamforge.stp.protocol.StpEncoder;
import io.streamforge.stp.protocol.StpMessage;
import io.streamforge.stp.protocol.StpProtocol;
import io.streamforge.stp.protocol.TradeMessage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/** JMH throughput benchmarks for complete-frame and incremental STP codec operations. */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Thread)
public class StpCodecBenchmark {

  private static final int BATCH_SIZE = 1_024;
  private static final int MAXIMUM_FRAME_SIZE =
      StpProtocol.LENGTH_FIELD_WIDTH + StpProtocol.MAX_ENCODED_LENGTH;
  private static final int[] REALISTIC_CHUNK_SIZES = {7, 23, 64, 128, 37, 256};

  @Param({"ADDS_ONLY", "LIFECYCLE", "BALANCED"})
  public String messageMix;

  private StpMessage[] messages;
  private byte[][] encodedFrames;
  private byte[][] chunks;
  private StpEncoder encoder;
  private StpDecoder completeFrameDecoder;
  private IncrementalStpDecoder incrementalDecoder;

  @Setup(Level.Trial)
  public void createFixtures() {
    encoder = new StpEncoder();
    completeFrameDecoder = new StpDecoder();
    messages = new StpMessage[BATCH_SIZE];
    encodedFrames = new byte[BATCH_SIZE][];
    for (int index = 0; index < BATCH_SIZE; index++) {
      StpMessage message = messageFor(index, messageMix);
      messages[index] = message;
      encodedFrames[index] = encoder.encode(message);
    }
    chunks = chunk(concatenate(encodedFrames));
  }

  @Setup(Level.Invocation)
  public void resetIncrementalDecoder() {
    // Sequence validation is deliberately delegated outside this codec throughput benchmark.
    incrementalDecoder = new IncrementalStpDecoder(MAXIMUM_FRAME_SIZE, false);
  }

  @Benchmark
  @OperationsPerInvocation(BATCH_SIZE)
  public void encodeThroughput(Blackhole blackhole) {
    for (StpMessage message : messages) {
      blackhole.consume(encoder.encode(message));
    }
  }

  @Benchmark
  @OperationsPerInvocation(BATCH_SIZE)
  public void decodeCompleteFrames(Blackhole blackhole) {
    for (byte[] frame : encodedFrames) {
      blackhole.consume(completeFrameDecoder.decode(frame));
    }
  }

  @Benchmark
  @OperationsPerInvocation(BATCH_SIZE)
  public void decodeIncrementalChunks(Blackhole blackhole) {
    for (byte[] chunk : chunks) {
      incrementalDecoder.feed(chunk).forEach(blackhole::consume);
    }
    incrementalDecoder.endOfInput().forEach(blackhole::consume);
  }

  private static StpMessage messageFor(int index, String mix) {
    long sequence = index + 1L;
    return switch (mix) {
      case "ADDS_ONLY" -> addOrder(sequence);
      case "LIFECYCLE" -> lifecycleMessage(sequence, index % 3);
      case "BALANCED" -> balancedMessage(sequence, index % 4);
      default -> throw new IllegalArgumentException("unsupported message mix " + mix);
    };
  }

  private static StpMessage lifecycleMessage(long sequence, int typeIndex) {
    return switch (typeIndex) {
      case 0 -> addOrder(sequence);
      case 1 ->
          new ExecuteOrderMessage(
              header(MessageType.EXECUTE_ORDER, sequence), new OrderId(sequence), new Quantity(50));
      default ->
          new CancelOrderMessage(
              header(MessageType.CANCEL_ORDER, sequence), new OrderId(sequence), new Quantity(25));
    };
  }

  private static StpMessage balancedMessage(long sequence, int typeIndex) {
    return switch (typeIndex) {
      case 0 -> addOrder(sequence);
      case 1 ->
          new ExecuteOrderMessage(
              header(MessageType.EXECUTE_ORDER, sequence), new OrderId(sequence), new Quantity(50));
      case 2 ->
          new CancelOrderMessage(
              header(MessageType.CANCEL_ORDER, sequence), new OrderId(sequence), new Quantity(25));
      default ->
          new TradeMessage(
              header(MessageType.TRADE, sequence),
              new TradeId(sequence),
              new InstrumentSymbol("MSFT"),
              Side.SELL,
              new Quantity(75),
              new FixedDecimal(25_005, 2));
    };
  }

  private static AddOrderMessage addOrder(long sequence) {
    return new AddOrderMessage(
        header(MessageType.ADD_ORDER, sequence),
        new OrderId(sequence),
        new InstrumentSymbol("AAPL"),
        Side.BUY,
        new Quantity(100),
        new FixedDecimal(12_345, 2));
  }

  private static FrameHeader header(MessageType messageType, long sequence) {
    return new FrameHeader(
        messageType.encodedLength(),
        messageType,
        new SequenceNumber(sequence),
        new EventTimestamp(sequence * 1_000L));
  }

  private static byte[] concatenate(byte[][] frames) {
    int totalLength = Arrays.stream(frames).mapToInt(frame -> frame.length).sum();
    byte[] output = new byte[totalLength];
    int offset = 0;
    for (byte[] frame : frames) {
      System.arraycopy(frame, 0, output, offset, frame.length);
      offset += frame.length;
    }
    return output;
  }

  private static byte[][] chunk(byte[] input) {
    List<byte[]> chunks = new ArrayList<>();
    int offset = 0;
    int chunkIndex = 0;
    while (offset < input.length) {
      int chunkSize = REALISTIC_CHUNK_SIZES[chunkIndex++ % REALISTIC_CHUNK_SIZES.length];
      int end = Math.min(offset + chunkSize, input.length);
      chunks.add(Arrays.copyOfRange(input, offset, end));
      offset = end;
    }
    return chunks.toArray(byte[][]::new);
  }
}
