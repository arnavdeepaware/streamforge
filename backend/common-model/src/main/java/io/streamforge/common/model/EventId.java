package io.streamforge.common.model;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** A stable lowercase SHA-256 identifier for one canonical source event. */
public record EventId(String value) {

  private static final byte[] DOMAIN_SEPARATOR =
      "streamforge:canonical-event-id:v1\0".getBytes(StandardCharsets.US_ASCII);
  private static final int SHA_256_HEX_LENGTH = 64;

  public EventId {
    if (value == null || value.length() != SHA_256_HEX_LENGTH) {
      throw new IllegalArgumentException("event ID must be 64 lowercase hexadecimal characters");
    }
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      if (!((character >= '0' && character <= '9') || (character >= 'a' && character <= 'f'))) {
        throw new IllegalArgumentException("event ID must be 64 lowercase hexadecimal characters");
      }
    }
  }

  /**
   * Derives an ID from a domain separator, the length-prefixed UTF-8 source identity, and the
   * big-endian source sequence number.
   */
  public static EventId deterministic(SourceIdentity source, SequenceNumber sequenceNumber) {
    if (source == null || sequenceNumber == null) {
      throw new IllegalArgumentException("source and sequence number must not be null");
    }

    byte[] sourceBytes = source.value().getBytes(StandardCharsets.UTF_8);
    ByteBuffer identity =
        ByteBuffer.allocate(
            DOMAIN_SEPARATOR.length + Integer.BYTES + sourceBytes.length + Long.BYTES);
    identity.put(DOMAIN_SEPARATOR);
    identity.putInt(sourceBytes.length);
    identity.put(sourceBytes);
    identity.putLong(sequenceNumber.value());
    return new EventId(HexFormat.of().formatHex(sha256(identity.array())));
  }

  @Override
  public String toString() {
    return value;
  }

  private static byte[] sha256(byte[] input) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(input);
    } catch (NoSuchAlgorithmException error) {
      throw new IllegalStateException("Java runtime does not provide SHA-256", error);
    }
  }
}
