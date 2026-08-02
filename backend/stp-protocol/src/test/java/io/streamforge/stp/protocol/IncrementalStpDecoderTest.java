package io.streamforge.stp.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class IncrementalStpDecoderTest {

  private static final int MAXIMUM_FRAME_SIZE = 49;

  @ParameterizedTest
  @MethodSource("everyGoldenVectorSplit")
  void decodesEveryPossibleTwoChunkSplit(byte[] frame, int splitPoint) {
    IncrementalStpDecoder decoder = new IncrementalStpDecoder(MAXIMUM_FRAME_SIZE);
    StpDecodeResult expected = new StpDecoder().decode(frame);

    List<StpParseEvent> events = new ArrayList<>();
    events.addAll(decoder.feed(Arrays.copyOfRange(frame, 0, splitPoint)));
    events.addAll(decoder.feed(Arrays.copyOfRange(frame, splitPoint, frame.length)));

    assertThat(events).containsExactly(new ParsedStpFrame(expected));
    assertThat(decoder.endOfInput()).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("goldenFrames")
  void decodesAFrameFedOneByteAtATime(byte[] frame) {
    IncrementalStpDecoder decoder = new IncrementalStpDecoder(MAXIMUM_FRAME_SIZE);
    List<StpParseEvent> events = new ArrayList<>();

    for (byte value : frame) {
      events.addAll(decoder.feed(new byte[] {value}));
    }

    assertThat(events).containsExactly(new ParsedStpFrame(new StpDecoder().decode(frame)));
  }

  @Test
  void decodesConcatenatedFramesInWireOrder() {
    List<byte[]> frames = goldenFrames().toList();
    byte[] concatenated = concatenate(frames);
    IncrementalStpDecoder decoder = new IncrementalStpDecoder(MAXIMUM_FRAME_SIZE);

    List<StpParseEvent> events = decoder.feed(concatenated);

    assertThat(events)
        .containsExactlyElementsOf(
            frames.stream()
                .map(frame -> new ParsedStpFrame(new StpDecoder().decode(frame)))
                .toList());
  }

  @Test
  void decodesArbitrarilyFragmentedConcatenatedFrames() {
    byte[] concatenated = concatenate(goldenFrames().toList());
    IncrementalStpDecoder decoder = new IncrementalStpDecoder(MAXIMUM_FRAME_SIZE);
    List<StpParseEvent> events = new ArrayList<>();
    int[] chunkSizes = {0, 1, 7, 2, 19, 3, 31, 5};
    int offset = 0;
    int chunkIndex = 0;

    while (offset < concatenated.length) {
      int chunkSize = chunkSizes[chunkIndex++ % chunkSizes.length];
      int end = Math.min(offset + chunkSize, concatenated.length);
      events.addAll(decoder.feed(Arrays.copyOfRange(concatenated, offset, end)));
      offset = end;
    }

    assertThat(events).hasSize(4);
    assertThat(events).allSatisfy(event -> assertThat(event).isInstanceOf(ParsedStpFrame.class));
  }

  @Test
  void ignoresEmptyChunksAndDoesNotModifyInputBuffers() {
    IncrementalStpDecoder decoder = new IncrementalStpDecoder(MAXIMUM_FRAME_SIZE);
    ByteBuffer empty = ByteBuffer.allocate(0);

    assertThat(decoder.feed(new byte[0])).isEmpty();
    assertThat(decoder.feed(empty)).isEmpty();
    assertThat(empty.position()).isZero();
  }

  @Test
  void emitsUnknownFramesAndContinuesWithKnownFrames() {
    byte[] unknown = {0, 3, 'X', (byte) 0xFF, 0};
    byte[] addOrder = goldenFrames().findFirst().orElseThrow();
    IncrementalStpDecoder decoder = new IncrementalStpDecoder(MAXIMUM_FRAME_SIZE);

    List<StpParseEvent> events = decoder.feed(concatenate(List.of(unknown, addOrder)));

    assertThat(events)
        .containsExactly(
            new ParsedStpFrame(new UnknownMessageFrame(3, 'X')),
            new ParsedStpFrame(new StpDecoder().decode(addOrder)));
  }

  @Test
  void reportsIncompleteHeaderAtEndOfInput() {
    IncrementalStpDecoder decoder = new IncrementalStpDecoder(MAXIMUM_FRAME_SIZE);
    decoder.feed(new byte[] {0});

    assertThat(decoder.endOfInput())
        .singleElement()
        .isInstanceOfSatisfying(
            StpParseFailure.class,
            failure -> {
              assertThat(failure.recoverable()).isFalse();
              assertThat(failure.error()).isInstanceOf(TruncatedFrameException.class);
            });
  }

  @Test
  void reportsIncompleteFrameAtEndOfInput() {
    byte[] frame = goldenFrames().findFirst().orElseThrow();
    IncrementalStpDecoder decoder = new IncrementalStpDecoder(MAXIMUM_FRAME_SIZE);
    decoder.feed(Arrays.copyOf(frame, frame.length - 1));

    assertThat(decoder.endOfInput())
        .singleElement()
        .isInstanceOfSatisfying(
            StpParseFailure.class,
            failure -> assertThat(failure.error()).isInstanceOf(TruncatedFrameException.class));
  }

  @Test
  void rejectsZeroLengthAndRecoversAtTheNextPrefix() {
    byte[] execute = goldenFrames().skip(1).findFirst().orElseThrow();
    IncrementalStpDecoder decoder = new IncrementalStpDecoder(MAXIMUM_FRAME_SIZE);

    List<StpParseEvent> events = decoder.feed(concatenate(List.of(new byte[] {0, 0}, execute)));

    assertThat(events).hasSize(2);
    assertThat(events.getFirst()).isInstanceOf(StpParseFailure.class);
    assertThat(((StpParseFailure) events.getFirst()).recoverable()).isTrue();
    assertThat(events.getLast()).isEqualTo(new ParsedStpFrame(new StpDecoder().decode(execute)));
  }

  @Test
  void skipsAnOversizedFrameWithoutBufferingItAndThenRecovers() {
    int configuredMaximum = 31;
    int oversizedEncodedLength = 40;
    byte[] oversizedFrame = new byte[StpProtocol.LENGTH_FIELD_WIDTH + oversizedEncodedLength];
    oversizedFrame[0] = 0;
    oversizedFrame[1] = (byte) oversizedEncodedLength;
    byte[] execute = goldenFrames().skip(1).findFirst().orElseThrow();
    IncrementalStpDecoder decoder = new IncrementalStpDecoder(configuredMaximum);

    List<StpParseEvent> events = decoder.feed(concatenate(List.of(oversizedFrame, execute)));

    assertThat(events).hasSize(2);
    assertThat(events.getFirst())
        .isInstanceOfSatisfying(
            StpParseFailure.class,
            failure -> {
              assertThat(failure.recoverable()).isTrue();
              assertThat(failure.error()).isInstanceOf(FrameTooLargeException.class);
            });
    assertThat(events.getLast()).isEqualTo(new ParsedStpFrame(new StpDecoder().decode(execute)));
    assertThat(decoder.bufferedByteCount()).isZero();
    assertThat(decoder.maximumFrameSize()).isEqualTo(configuredMaximum);
  }

  @Test
  void neverBuffersPayloadFromAnOversizedDeclaration() {
    IncrementalStpDecoder decoder = new IncrementalStpDecoder(31);

    List<StpParseEvent> events = decoder.feed(new byte[] {0, 100});
    decoder.feed(new byte[20]);

    assertThat(events)
        .singleElement()
        .isInstanceOfSatisfying(
            StpParseFailure.class,
            failure -> assertThat(failure.error()).isInstanceOf(FrameTooLargeException.class));
    assertThat(decoder.bufferedByteCount()).isZero();
    assertThat(decoder.endOfInput())
        .singleElement()
        .isInstanceOfSatisfying(
            StpParseFailure.class,
            failure -> assertThat(failure.error()).isInstanceOf(TruncatedFrameException.class));
  }

  @Test
  void rejectsACompleteMalformedFrameAndContinuesAtItsDeclaredBoundary() {
    byte[] malformed = {0, 1, 'A'};
    byte[] execute = goldenFrames().skip(1).findFirst().orElseThrow();
    IncrementalStpDecoder decoder = new IncrementalStpDecoder(MAXIMUM_FRAME_SIZE);

    List<StpParseEvent> events = decoder.feed(concatenate(List.of(malformed, execute)));

    assertThat(events).hasSize(2);
    assertThat(events.getFirst())
        .isInstanceOfSatisfying(
            StpParseFailure.class,
            failure -> assertThat(failure.error()).isInstanceOf(InvalidFrameLengthException.class));
    assertThat(events.getLast()).isEqualTo(new ParsedStpFrame(new StpDecoder().decode(execute)));
  }

  @Test
  void rejectsAnInvalidFieldAndContinuesAtTheNextDeclaredBoundary() {
    byte[] invalidAddOrder = goldenFrames().findFirst().orElseThrow();
    invalidAddOrder[StpProtocol.SIDE_OFFSET] = 'X';
    byte[] execute = goldenFrames().skip(1).findFirst().orElseThrow();
    IncrementalStpDecoder decoder = new IncrementalStpDecoder(MAXIMUM_FRAME_SIZE);

    List<StpParseEvent> events = decoder.feed(concatenate(List.of(invalidAddOrder, execute)));

    assertThat(events).hasSize(2);
    assertThat(events.getFirst())
        .isInstanceOfSatisfying(
            StpParseFailure.class,
            failure -> {
              assertThat(failure.recoverable()).isTrue();
              assertThat(failure.error()).isInstanceOf(InvalidFieldEncodingException.class);
            });
    assertThat(events.getLast()).isEqualTo(new ParsedStpFrame(new StpDecoder().decode(execute)));
  }

  @Test
  void rejectsNonIncreasingKnownSequencesAndContinues() {
    byte[] addOrder = goldenFrames().findFirst().orElseThrow();
    IncrementalStpDecoder decoder = new IncrementalStpDecoder(MAXIMUM_FRAME_SIZE);

    List<StpParseEvent> events = decoder.feed(concatenate(List.of(addOrder, addOrder)));

    assertThat(events).hasSize(2);
    assertThat(events.getFirst()).isInstanceOf(ParsedStpFrame.class);
    assertThat(events.getLast())
        .isInstanceOfSatisfying(
            StpParseFailure.class,
            failure ->
                assertThat(failure.error()).isInstanceOf(InvalidStreamSequenceException.class));
  }

  @Test
  void validatesConfiguredFrameBounds() {
    assertThatThrownBy(() -> new IncrementalStpDecoder(2))
        .isInstanceOf(StpValidationException.class);
    assertThatThrownBy(
            () ->
                new IncrementalStpDecoder(
                    StpProtocol.LENGTH_FIELD_WIDTH + StpProtocol.MAX_ENCODED_LENGTH + 1))
        .isInstanceOf(StpValidationException.class);
  }

  private static Stream<Arguments> everyGoldenVectorSplit() {
    return goldenFrames()
        .flatMap(
            frame ->
                IntStream.rangeClosed(0, frame.length)
                    .mapToObj(splitPoint -> Arguments.of(frame, splitPoint)));
  }

  private static Stream<byte[]> goldenFrames() {
    return Stream.of(
        hex(
            "00 2F 41 00 00 00 00 00 00 00 01 00 00 00 00 3B 9A CA 00 00 00 00 00 00 00 03 E9 41 41 50 4C 20 20 20 20 42 00 00 00 64 00 00 00 00 00 00 30 39 02"),
        hex(
            "00 1D 45 00 00 00 00 00 00 00 02 00 00 00 00 3B 9A CA 64 00 00 00 00 00 00 03 E9 00 00 00 28"),
        hex(
            "00 1D 43 00 00 00 00 00 00 00 03 00 00 00 00 3B 9A CA C8 00 00 00 00 00 00 03 E9 00 00 00 3C"),
        hex(
            "00 2F 54 00 00 00 00 00 00 00 04 00 00 00 00 3B 9A CB 2C 00 00 00 00 00 00 13 89 4D 53 46 54 20 20 20 20 53 00 00 00 19 00 00 00 00 00 00 61 AD 02"));
  }

  private static byte[] hex(String value) {
    return HexFormat.of().parseHex(value.replace(" ", ""));
  }

  private static byte[] concatenate(List<byte[]> frames) {
    int totalLength = frames.stream().mapToInt(frame -> frame.length).sum();
    byte[] concatenated = new byte[totalLength];
    int offset = 0;
    for (byte[] frame : frames) {
      System.arraycopy(frame, 0, concatenated, offset, frame.length);
      offset += frame.length;
    }
    return concatenated;
  }
}
