package io.streamforge.controlplane.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.streamforge.controlplane.api.PipelineRunResponse;
import io.streamforge.controlplane.execution.PipelineExecutionBackend;
import io.streamforge.controlplane.execution.PipelineExecutionCommand;
import io.streamforge.controlplane.execution.PipelineExecutionResult;
import io.streamforge.controlplane.execution.PipelineRunState;
import io.streamforge.controlplane.service.PipelineRunPersistenceService.PreparedRun;
import io.streamforge.pipelineruntime.PipelineCounters;
import io.streamforge.pipelineruntime.PipelineOutcome;
import io.streamforge.pipelineruntime.PipelineReport;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PipelineRunServiceTest {
  @TempDir Path artifactRoot;
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void rejectsDuplicateStartsWhileAHandleIsActive() {
    UUID pipelineId = UUID.randomUUID();
    FakePersistence persistence = new FakePersistence();
    persistence.add(prepared(pipelineId, UUID.randomUUID()));
    PipelineRunService service = service(persistence, (command, listener) -> () -> {});

    service.start(pipelineId, null);

    assertThatThrownBy(() -> service.start(pipelineId, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("active run");
    assertThat(persistence.createCount).isEqualTo(1);
  }

  @Test
  void permitsAnotherStartWhenCompletionPrecedesHandleRegistration() {
    UUID pipelineId = UUID.randomUUID();
    FakePersistence persistence = new FakePersistence();
    PreparedRun first = prepared(pipelineId, UUID.randomUUID());
    PreparedRun second = prepared(pipelineId, UUID.randomUUID());
    persistence.add(first);
    persistence.add(second);
    PipelineExecutionBackend synchronous =
        (command, listener) -> {
          listener.onRunning();
          listener.onCompleted(
              new PipelineExecutionResult(completedReport(), Optional.empty(), Optional.empty()));
          return () -> {};
        };
    PipelineRunService service = service(persistence, synchronous);

    service.start(pipelineId, null);
    service.start(pipelineId, null);

    assertThat(persistence.createCount).isEqualTo(2);
  }

  @Test
  void carriesStopCancellationAcrossHandleRegistration() throws Exception {
    UUID pipelineId = UUID.randomUUID();
    UUID runId = UUID.randomUUID();
    FakePersistence persistence = new FakePersistence();
    persistence.add(prepared(pipelineId, runId));
    CountDownLatch backendEntered = new CountDownLatch(1);
    CountDownLatch releaseHandle = new CountDownLatch(1);
    AtomicBoolean cancelled = new AtomicBoolean();
    PipelineExecutionBackend delayed =
        (command, listener) -> {
          backendEntered.countDown();
          await(releaseHandle);
          return () -> cancelled.set(true);
        };
    PipelineRunService service = service(persistence, delayed);

    try (var executor = Executors.newSingleThreadExecutor()) {
      var start = executor.submit(() -> service.start(pipelineId, null));
      assertThat(backendEntered.await(5, TimeUnit.SECONDS)).isTrue();
      service.stop(pipelineId, runId);
      releaseHandle.countDown();
      start.get(5, TimeUnit.SECONDS);
    }

    assertThat(cancelled).isTrue();
  }

  private PipelineRunService service(
      PipelineRunPersistenceService persistence, PipelineExecutionBackend backend) {
    return new PipelineRunService(
        persistence,
        backend,
        new PipelineRunMonitor(Clock.fixed(Instant.parse("2026-08-03T00:00:00Z"), ZoneOffset.UTC)),
        mapper,
        new SimpleMeterRegistry(),
        artifactRoot.toString());
  }

  private PreparedRun prepared(UUID pipelineId, UUID runId) {
    ObjectNode input = mapper.createObjectNode().put("type", "JSONL").put("path", "input.jsonl");
    ObjectNode output = mapper.createObjectNode().put("type", "JSONL").put("path", "output.jsonl");
    return new PreparedRun(
        response(pipelineId, runId, PipelineRunState.STARTING),
        new PipelineExecutionCommand(
            runId, pipelineId, 1, input, null, null, output, Optional.empty()));
  }

  private static PipelineRunResponse running(UUID pipelineId, UUID runId) {
    return response(pipelineId, runId, PipelineRunState.RUNNING);
  }

  private static PipelineRunResponse completed(UUID pipelineId, UUID runId) {
    return response(pipelineId, runId, PipelineRunState.COMPLETED);
  }

  private static PipelineRunResponse response(UUID pipelineId, UUID runId, PipelineRunState state) {
    return new PipelineRunResponse(
        runId, pipelineId, UUID.randomUUID(), state, null, Instant.EPOCH, null);
  }

  private static PipelineReport completedReport() {
    return new PipelineReport(
        new PipelineCounters(0, 0, 0, 0, 0, 0), List.of(), 0, PipelineOutcome.COMPLETED);
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("latch timed out");
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted while awaiting test coordination", exception);
    }
  }

  private static final class FakePersistence extends PipelineRunPersistenceService {
    private final Deque<PreparedRun> prepared = new ArrayDeque<>();
    private final Map<UUID, UUID> pipelineByRun = new HashMap<>();
    private int createCount;

    private FakePersistence() {
      super(null, null, null, null, null, null, new ObjectMapper());
    }

    private void add(PreparedRun run) {
      prepared.addLast(run);
      pipelineByRun.put(run.response().runId(), run.response().pipelineId());
    }

    @Override
    public PreparedRun createStarting(
        UUID pipelineId, io.streamforge.controlplane.api.DeadLetterOptionsRequest request) {
      createCount++;
      return prepared.removeFirst();
    }

    @Override
    public PipelineRunResponse running(UUID runId) {
      return PipelineRunServiceTest.running(pipelineByRun.get(runId), runId);
    }

    @Override
    public PipelineRunResponse finish(UUID runId, PipelineExecutionResult result) {
      return completed(pipelineByRun.get(runId), runId);
    }

    @Override
    public PipelineRunResponse requestStop(UUID pipelineId, UUID runId) {
      return response(pipelineId, runId, PipelineRunState.STOPPING);
    }
  }
}
