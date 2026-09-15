package io.streamforge.pipelineruntime;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;

/** Creates an immutable, checksummed source copy before decoding begins. */
final class RawCapture {
  private RawCapture() {}

  static PipelineInput capture(PipelineInput input, PipelineRunArtifacts artifacts, Clock clock)
      throws IOException {
    Files.createDirectories(artifacts.directory());
    Path destination = artifacts.rawCapture();
    if (Files.exists(destination) || Files.exists(artifacts.rawCaptureManifest())) {
      throw new IOException("raw capture artifacts already exist for run " + artifacts.runId());
    }
    Path temporary = Files.createTempFile(artifacts.directory(), ".raw-capture-", ".tmp");
    MessageDigest digest = sha256();
    long length = 0;
    try {
      try (InputStream source = Files.newInputStream(input.path());
          OutputStream target = Files.newOutputStream(temporary)) {
        byte[] buffer = new byte[8192];
        int count;
        while ((count = source.read(buffer)) != -1) {
          target.write(buffer, 0, count);
          digest.update(buffer, 0, count);
          length = Math.addExact(length, count);
        }
      }
      publish(temporary, destination);
      String manifest =
          "{\"runId\":\""
              + artifacts.runId()
              + "\",\"inputType\":\""
              + inputType(input)
              + "\",\"originalFileName\":\""
              + json(input.path().getFileName().toString())
              + "\",\"byteLength\":"
              + length
              + ",\"sha256\":\""
              + HexFormat.of().formatHex(digest.digest())
              + "\",\"capturedAt\":\""
              + clock.instant()
              + "\",\"storedFileName\":\"raw-input.capture\"}\n";
      Path manifestTemporary =
          Files.createTempFile(artifacts.directory(), ".raw-manifest-", ".tmp");
      try {
        Files.writeString(manifestTemporary, manifest);
        publish(manifestTemporary, artifacts.rawCaptureManifest());
      } finally {
        Files.deleteIfExists(manifestTemporary);
      }
      return withPath(input, destination);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  private static PipelineInput withPath(PipelineInput input, Path path) {
    return switch (input) {
      case PipelineInput.StpBinary value ->
          new PipelineInput.StpBinary(
              path, value.source(), value.venue(), value.maximumFrameSize());
      case PipelineInput.JsonLines value -> new PipelineInput.JsonLines(path, value.mode());
      case PipelineInput.Csv value -> new PipelineInput.Csv(path, value.config(), value.mode());
    };
  }

  private static String inputType(PipelineInput input) {
    return switch (input) {
      case PipelineInput.StpBinary ignored -> "STP_BINARY";
      case PipelineInput.JsonLines ignored -> "JSONL";
      case PipelineInput.Csv ignored -> "CSV";
    };
  }

  private static void publish(Path source, Path destination) throws IOException {
    try {
      Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException exception) {
      Files.move(source, destination);
    }
  }

  private static MessageDigest sha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private static String json(String value) {
    StringBuilder escaped = new StringBuilder(value.length());
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      switch (character) {
        case '\"' -> escaped.append("\\\"");
        case '\\' -> escaped.append("\\\\");
        case '\b' -> escaped.append("\\b");
        case '\f' -> escaped.append("\\f");
        case '\n' -> escaped.append("\\n");
        case '\r' -> escaped.append("\\r");
        case '\t' -> escaped.append("\\t");
        default -> {
          if (character < 0x20) {
            escaped.append("\\u").append(String.format("%04x", (int) character));
          } else {
            escaped.append(character);
          }
        }
      }
    }
    return escaped.toString();
  }
}
