package io.streamforge.controlplane.health;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/** Readiness check for the local paths required by the single-node runtime. */
@Component("localStorage")
public final class LocalStorageHealthIndicator implements HealthIndicator {
  private final Path inputRoot;
  private final Path workspaceRoot;
  private final Path artifactRoot;

  public LocalStorageHealthIndicator(
      @Value("${streamforge.local-pipeline.input-root:.}") String inputRoot,
      @Value("${streamforge.local-pipeline.workspace:.streamforge/workspace}") String workspaceRoot,
      @Value("${streamforge.local-pipeline.artifact-root:.streamforge/artifacts}")
          String artifactRoot) {
    this.inputRoot = Path.of(inputRoot).toAbsolutePath().normalize();
    this.workspaceRoot = Path.of(workspaceRoot).toAbsolutePath().normalize();
    this.artifactRoot = Path.of(artifactRoot).toAbsolutePath().normalize();
  }

  @Override
  public Health health() {
    Map<String, String> failures = new LinkedHashMap<>();
    if (!Files.isDirectory(inputRoot) || !Files.isReadable(inputRoot)) {
      failures.put("inputRoot", "must be an existing readable directory");
    }
    verifyWritable("workspaceRoot", workspaceRoot, failures);
    verifyWritable("artifactRoot", artifactRoot, failures);
    if (!failures.isEmpty()) {
      return Health.down().withDetails(failures).build();
    }
    return Health.up()
        .withDetail("inputRoot", "readable")
        .withDetail("workspaceRoot", "writable")
        .withDetail("artifactRoot", "writable")
        .build();
  }

  private static void verifyWritable(String name, Path directory, Map<String, String> failures) {
    Path probe = null;
    try {
      Files.createDirectories(directory);
      probe = Files.createTempFile(directory, ".streamforge-health-", ".tmp");
    } catch (IOException | SecurityException exception) {
      failures.put(name, "cannot create and write files");
    } finally {
      if (probe != null) {
        try {
          Files.deleteIfExists(probe);
        } catch (IOException exception) {
          failures.put(name, "cannot remove temporary files");
        }
      }
    }
  }
}
