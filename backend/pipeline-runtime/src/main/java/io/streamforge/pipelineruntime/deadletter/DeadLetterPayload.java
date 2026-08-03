package io.streamforge.pipelineruntime.deadletter;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** A redacted, byte-bounded source fragment retained only when enabled by configuration. */
public record DeadLetterPayload(String encoding, String value, boolean truncated) {
  private static final Pattern SENSITIVE_VALUE =
      Pattern.compile(
          "(?i)(password|token|secret|api[_-]?key)(?:\\\"|')?\\s*[=:]\\s*(?:\\\"[^\\\"]*\\\"|'[^']*'|[^,\\s}]+)");

  public DeadLetterPayload {
    if (encoding == null || encoding.isBlank() || value == null) {
      throw new IllegalArgumentException("dead-letter payload requires encoding and value");
    }
  }

  public static DeadLetterPayload text(String source, int maximumBytes) {
    String redacted = SENSITIVE_VALUE.matcher(source).replaceAll("$1=[REDACTED]");
    ByteArrayOutputStream limited = new ByteArrayOutputStream(Math.min(maximumBytes, 256));
    boolean truncated = false;
    for (int offset = 0; offset < redacted.length(); ) {
      int codePoint = redacted.codePointAt(offset);
      byte[] encoded = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8);
      if (encoded.length > maximumBytes - limited.size()) {
        truncated = true;
        break;
      }
      limited.writeBytes(encoded);
      offset += Character.charCount(codePoint);
    }
    return new DeadLetterPayload("utf-8", limited.toString(StandardCharsets.UTF_8), truncated);
  }

  public static DeadLetterPayload binary(byte[] source, int maximumBytes) {
    return binary(source, source.length, maximumBytes);
  }

  public static DeadLetterPayload binary(byte[] source, int length, int maximumBytes) {
    if (source == null || length < 0 || length > source.length) {
      throw new IllegalArgumentException("binary payload bounds are invalid");
    }
    int kept = Math.min(length, maximumBytes);
    byte[] limited = java.util.Arrays.copyOf(source, kept);
    return new DeadLetterPayload(
        "base64", Base64.getEncoder().encodeToString(limited), length > kept);
  }

  public Map<String, Object> asJson() {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("encoding", encoding);
    fields.put("truncated", truncated);
    fields.put("value", value);
    return Map.copyOf(fields);
  }
}
