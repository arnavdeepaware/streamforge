package io.streamforge.stp.protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Non-blocking, bounded STP v1 framing for arbitrarily chunked byte streams.
 *
 * <p>The decoder never allocates from an on-wire length. It allocates one buffer from the validated
 * {@code maximumFrameSize}; larger declared frames are discarded without buffering until their
 * trusted length boundary is reached.
 */
public final class IncrementalStpDecoder {

  private static final int NO_FRAME_SIZE = -1;

  private final int maximumFrameSize;
  private final boolean enforceStreamSequence;
  private final ByteBuffer frameBuffer;
  private final StpDecoder frameDecoder;

  private int expectedFrameSize = NO_FRAME_SIZE;
  private int discardedFrameSize;
  private int discardedFrameBytes;
  private int bytesRemainingToDiscard;
  private long lastSequence;
  private boolean hasAcceptedSequence;
  private boolean ended;

  public IncrementalStpDecoder(int maximumFrameSize) {
    this(maximumFrameSize, true);
  }

  /**
   * Creates a bounded decoder, optionally leaving stream-sequence classification to a caller.
   *
   * <p>When {@code enforceStreamSequence} is false, framing and field validation still apply, but
   * duplicate and out-of-order known messages are emitted for external integrity tracking.
   */
  public IncrementalStpDecoder(int maximumFrameSize, boolean enforceStreamSequence) {
    int minimumFrameSize = StpProtocol.LENGTH_FIELD_WIDTH + StpProtocol.MIN_ENCODED_LENGTH;
    int protocolMaximumFrameSize = StpProtocol.LENGTH_FIELD_WIDTH + StpProtocol.MAX_ENCODED_LENGTH;
    if (maximumFrameSize < minimumFrameSize || maximumFrameSize > protocolMaximumFrameSize) {
      throw new StpValidationException(
          "maximumFrameSize must be between "
              + minimumFrameSize
              + " and "
              + protocolMaximumFrameSize);
    }
    this.maximumFrameSize = maximumFrameSize;
    this.enforceStreamSequence = enforceStreamSequence;
    this.frameBuffer = ByteBuffer.allocate(maximumFrameSize).order(ByteOrder.BIG_ENDIAN);
    this.frameDecoder = new StpDecoder();
  }

  /** Accepts all currently available bytes and returns parse events in wire order. */
  public List<StpParseEvent> feed(byte[] chunk) {
    if (chunk == null) {
      throw new StpValidationException("chunk must not be null");
    }
    return feed(ByteBuffer.wrap(chunk));
  }

  /** Accepts the buffer's remaining bytes without changing the caller's position or limit. */
  public List<StpParseEvent> feed(ByteBuffer chunk) {
    if (chunk == null) {
      throw new StpValidationException("chunk must not be null");
    }
    if (ended) {
      throw new IllegalStateException("endOfInput has already been called");
    }

    ByteBuffer source = chunk.slice();
    List<StpParseEvent> events = new ArrayList<>();
    while (source.hasRemaining()) {
      if (bytesRemainingToDiscard > 0) {
        discardBytes(source);
        continue;
      }

      if (expectedFrameSize == NO_FRAME_SIZE) {
        transfer(source, StpProtocol.LENGTH_FIELD_WIDTH - frameBuffer.position());
        if (frameBuffer.position() < StpProtocol.LENGTH_FIELD_WIDTH) {
          break;
        }
        if (!readAndValidateLength(events)) {
          continue;
        }
      }

      transfer(source, expectedFrameSize - frameBuffer.position());
      if (frameBuffer.position() == expectedFrameSize) {
        decodeBufferedFrame(events);
      }
    }
    return List.copyOf(events);
  }

  /**
   * Marks the stream complete and reports an incomplete prefix or frame. Subsequent feeds are
   * rejected.
   */
  public List<StpParseEvent> endOfInput() {
    if (ended) {
      return List.of();
    }
    ended = true;

    if (bytesRemainingToDiscard > 0) {
      return List.of(
          new StpParseFailure(
              new TruncatedFrameException(discardedFrameSize, discardedFrameBytes), false));
    }
    if (frameBuffer.position() == 0) {
      return List.of();
    }

    int expectedBytes =
        expectedFrameSize == NO_FRAME_SIZE ? StpProtocol.LENGTH_FIELD_WIDTH : expectedFrameSize;
    return List.of(
        new StpParseFailure(
            new TruncatedFrameException(expectedBytes, frameBuffer.position()), false));
  }

  public int maximumFrameSize() {
    return maximumFrameSize;
  }

  /** Returns bytes currently retained in the bounded frame buffer. */
  public int bufferedByteCount() {
    return frameBuffer.position();
  }

  private boolean readAndValidateLength(List<StpParseEvent> events) {
    int encodedLength = Short.toUnsignedInt(frameBuffer.getShort(0));
    if (encodedLength < StpProtocol.MIN_ENCODED_LENGTH) {
      events.add(new StpParseFailure(new InvalidFrameLengthException(encodedLength, null), true));
      frameBuffer.clear();
      return false;
    }

    int declaredFrameSize = StpProtocol.LENGTH_FIELD_WIDTH + encodedLength;
    if (declaredFrameSize > maximumFrameSize) {
      events.add(
          new StpParseFailure(
              new FrameTooLargeException(declaredFrameSize, maximumFrameSize), true));
      discardedFrameSize = declaredFrameSize;
      discardedFrameBytes = StpProtocol.LENGTH_FIELD_WIDTH;
      bytesRemainingToDiscard = encodedLength;
      frameBuffer.clear();
      return false;
    }
    expectedFrameSize = declaredFrameSize;
    return true;
  }

  private void decodeBufferedFrame(List<StpParseEvent> events) {
    frameBuffer.flip();
    try {
      StpDecodeResult result = frameDecoder.decode(frameBuffer);
      if (result instanceof StpMessage message
          && enforceStreamSequence
          && !acceptSequence(message, events)) {
        return;
      }
      events.add(new ParsedStpFrame(result));
    } catch (StpProtocolException error) {
      events.add(new StpParseFailure(error, true));
    } finally {
      frameBuffer.clear();
      expectedFrameSize = NO_FRAME_SIZE;
    }
  }

  private boolean acceptSequence(StpMessage message, List<StpParseEvent> events) {
    long currentSequence = message.header().sequenceNumber().value();
    if (hasAcceptedSequence && currentSequence <= lastSequence) {
      events.add(
          new StpParseFailure(
              new InvalidStreamSequenceException(lastSequence, currentSequence), true));
      return false;
    }
    lastSequence = currentSequence;
    hasAcceptedSequence = true;
    return true;
  }

  private void discardBytes(ByteBuffer source) {
    int bytesToDiscard = Math.min(bytesRemainingToDiscard, source.remaining());
    source.position(source.position() + bytesToDiscard);
    bytesRemainingToDiscard -= bytesToDiscard;
    discardedFrameBytes += bytesToDiscard;
    if (bytesRemainingToDiscard == 0) {
      discardedFrameSize = 0;
      discardedFrameBytes = 0;
    }
  }

  private void transfer(ByteBuffer source, int maximumBytes) {
    int bytesToTransfer = Math.min(maximumBytes, source.remaining());
    int originalLimit = source.limit();
    source.limit(source.position() + bytesToTransfer);
    frameBuffer.put(source);
    source.limit(originalLimit);
  }
}
