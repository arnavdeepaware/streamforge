package io.streamforge.pipelineruntime;

import io.streamforge.common.model.CanonicalEvent;
import io.streamforge.common.model.InstrumentReference;
import io.streamforge.common.model.OrderId;
import io.streamforge.common.model.RawEventReference;
import io.streamforge.parserengine.JsonLinesCanonicalEvent;
import io.streamforge.parserengine.JsonLinesError;
import io.streamforge.parserengine.JsonLinesInputAdapter;
import io.streamforge.parserengine.NormalizedStpEvent;
import io.streamforge.parserengine.SequenceIntegrityEvent;
import io.streamforge.parserengine.SequenceIntegrityStatus;
import io.streamforge.parserengine.SequenceIntegrityTracker;
import io.streamforge.parserengine.SequenceSource;
import io.streamforge.parserengine.StpNormalizationContext;
import io.streamforge.parserengine.StpNormalizationFailure;
import io.streamforge.parserengine.StpNormalizationResult;
import io.streamforge.parserengine.StpNormalizer;
import io.streamforge.parserengine.csv.CsvCanonicalEvent;
import io.streamforge.parserengine.csv.CsvError;
import io.streamforge.parserengine.csv.CsvInputAdapter;
import io.streamforge.pipelineruntime.deadletter.DeadLetterCategory;
import io.streamforge.pipelineruntime.deadletter.DeadLetterConfig;
import io.streamforge.pipelineruntime.deadletter.DeadLetterPayload;
import io.streamforge.pipelineruntime.deadletter.DeadLetterPolicy;
import io.streamforge.pipelineruntime.deadletter.DeadLetterRecord;
import io.streamforge.pipelineruntime.deadletter.JsonLinesDeadLetterStore;
import io.streamforge.pipelineruntime.deadletter.Retryability;
import io.streamforge.pipelineruntime.output.CsvOutputSink;
import io.streamforge.pipelineruntime.output.JsonLinesOutputSink;
import io.streamforge.pipelineruntime.output.OutputRecord;
import io.streamforge.pipelineruntime.output.OutputSink;
import io.streamforge.pipelineruntime.output.OutputSinkException;
import io.streamforge.stp.protocol.AddOrderMessage;
import io.streamforge.stp.protocol.CancelOrderMessage;
import io.streamforge.stp.protocol.ExecuteOrderMessage;
import io.streamforge.stp.protocol.IncrementalStpDecoder;
import io.streamforge.stp.protocol.ParsedStpFrame;
import io.streamforge.stp.protocol.StpDecodeResult;
import io.streamforge.stp.protocol.StpMessage;
import io.streamforge.stp.protocol.StpParseEvent;
import io.streamforge.stp.protocol.StpParseFailure;
import io.streamforge.transform.blueprint.BlueprintPreviewResult;
import io.streamforge.transform.blueprint.CompiledOutputBlueprint;
import io.streamforge.transform.blueprint.OutputBlueprintService;
import io.streamforge.transform.compile.CanonicalTransformationFields;
import io.streamforge.transform.compile.CompiledTransformation;
import io.streamforge.transform.compile.TransformationCompiler;
import io.streamforge.transform.config.TransformationConfigParser;
import io.streamforge.transform.execute.CanonicalEventDocument;
import io.streamforge.transform.execute.TransformationExecutor;
import io.streamforge.transform.execute.TransformationResult;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Runs one saved local pipeline synchronously with direct backpressure and bounded failure
 * retention.
 *
 * <p>The runner reads one source record or bounded STP chunk at a time. It does not use queues or
 * background threads, so a slow transformation or sink naturally prevents input read-ahead.
 */
public final class LocalPipelineRunner {
  private static final int STP_READ_BUFFER_SIZE = 8_192;
  private static final int DEFAULT_MAX_RETAINED_FAILURES = 100;
  private static final String CANONICAL_SCHEMA_VERSION = "1.0";
  private static final String PIPELINE_SCHEMA_VERSION = "1.0";

  private final int maxRetainedFailures;
  private final Clock clock;
  private final PipelineRunObserver observer;

  public LocalPipelineRunner() {
    this(DEFAULT_MAX_RETAINED_FAILURES, Clock.systemUTC(), PipelineRunObserver.NO_OP);
  }

  /** Creates a runner with standard bounds and a live monitoring observer. */
  public LocalPipelineRunner(PipelineRunObserver observer) {
    this(DEFAULT_MAX_RETAINED_FAILURES, Clock.systemUTC(), observer);
  }

  /** Creates a runner that retains at most the requested number of detailed failures per run. */
  public LocalPipelineRunner(int maxRetainedFailures) {
    this(maxRetainedFailures, Clock.systemUTC(), PipelineRunObserver.NO_OP);
  }

  /** Creates a runner with a supplied clock for deterministic diagnostic timestamps. */
  public LocalPipelineRunner(int maxRetainedFailures, Clock clock) {
    this(maxRetainedFailures, clock, PipelineRunObserver.NO_OP);
  }

  /** Creates a runner that synchronously publishes bounded live monitoring snapshots. */
  public LocalPipelineRunner(int maxRetainedFailures, Clock clock, PipelineRunObserver observer) {
    if (maxRetainedFailures < 1) {
      throw new IllegalArgumentException("maximum retained failures must be positive");
    }
    if (clock == null || observer == null) {
      throw new IllegalArgumentException("clock and observer must not be null");
    }
    this.maxRetainedFailures = maxRetainedFailures;
    this.clock = clock;
    this.observer = observer;
  }

  /**
   * Runs a pipeline until successful completion, cancellation, or a terminal input/output failure.
   */
  public PipelineReport run(PipelineRunConfig config, PipelineCancellation cancellation)
      throws PipelineConfigurationException {
    if (config == null || cancellation == null) {
      throw new IllegalArgumentException(
          "pipeline configuration and cancellation must not be null");
    }
    PreparedPipeline prepared = prepare(config);
    try (DeadLetterSession deadLetters = DeadLetterSession.open(config.deadLetterConfig());
        OutputSink sink = outputSink(config.output())) {
      deadLetters.start();
      RunState state =
          new RunState(
              maxRetainedFailures,
              config.identity(),
              config.deadLetterConfig(),
              deadLetters,
              clock,
              observer);
      try {
        sink.start();
      } catch (OutputSinkException exception) {
        state.record(
            PipelineStage.OUTPUT,
            "output",
            Optional.empty(),
            detail(exception),
            DeadLetterCategory.OUTPUT,
            Retryability.RETRYABLE,
            Optional.empty());
        state.markTerminalFailure();
        deadLetters.complete();
        return state.report(cancellation.isCancelled());
      }
      try {
        processInput(config.input(), prepared, sink, cancellation, state);
        if (cancellation.isCancelled()) {
          sink.abort();
        } else {
          sink.complete();
        }
      } catch (Stopped ignored) {
        sink.abort();
      } catch (IOException exception) {
        state.recordTerminal(
            PipelineStage.INPUT, config.input().path().toString(), detail(exception));
        state.markTerminalFailure();
        sink.abort();
      } catch (OutputSinkException exception) {
        state.recordTerminal(PipelineStage.OUTPUT, "output", detail(exception));
        state.markTerminalFailure();
        sink.abort();
      }
      deadLetters.complete();
      return state.report(cancellation.isCancelled());
    } catch (OutputSinkException exception) {
      throw new PipelineConfigurationException("$.deadLetter.path", detail(exception), exception);
    }
  }

  private PreparedPipeline prepare(PipelineRunConfig config) throws PipelineConfigurationException {
    try {
      Optional<CompiledTransformation> transformation =
          config
              .transformationConfig()
              .map(
                  path -> {
                    try (BufferedReader reader = Files.newBufferedReader(path)) {
                      return new TransformationCompiler()
                          .compile(
                              new TransformationConfigParser().parse(reader),
                              CanonicalTransformationFields.v1());
                    } catch (IOException | RuntimeException exception) {
                      throw new PreparationFailure(path, exception);
                    } catch (Exception exception) {
                      throw new PreparationFailure(path, exception);
                    }
                  });
      OutputBlueprintService blueprintService = new OutputBlueprintService();
      Optional<CompiledOutputBlueprint> blueprint =
          config
              .blueprintConfig()
              .map(
                  path -> {
                    try {
                      return blueprintService.compile(Files.readString(path), transformation);
                    } catch (IOException | RuntimeException exception) {
                      throw new PreparationFailure(path, exception);
                    } catch (Exception exception) {
                      throw new PreparationFailure(path, exception);
                    }
                  });
      return new PreparedPipeline(
          transformation,
          transformation.map(TransformationExecutor::new),
          blueprint,
          blueprintService);
    } catch (PreparationFailure exception) {
      throw new PipelineConfigurationException(
          exception.path.toString(), detail(exception.getCause()), exception.getCause());
    }
  }

  private OutputSink outputSink(PipelineOutput output) {
    return switch (output) {
      case PipelineOutput.JsonLines jsonLines -> new JsonLinesOutputSink(jsonLines.path());
      case PipelineOutput.Csv csv -> new CsvOutputSink(csv.path(), csv.config());
    };
  }

  private void processInput(
      PipelineInput input,
      PreparedPipeline prepared,
      OutputSink sink,
      PipelineCancellation cancellation,
      RunState state)
      throws IOException, OutputSinkException {
    switch (input) {
      case PipelineInput.JsonLines jsonLines ->
          processJsonLines(jsonLines, prepared, sink, cancellation, state);
      case PipelineInput.Csv csv -> processCsv(csv, prepared, sink, cancellation, state);
      case PipelineInput.StpBinary stp -> processStp(stp, prepared, sink, cancellation, state);
    }
  }

  private void processJsonLines(
      PipelineInput.JsonLines input,
      PreparedPipeline prepared,
      OutputSink sink,
      PipelineCancellation cancellation,
      RunState state)
      throws IOException, OutputSinkException {
    try (BufferedReader reader = Files.newBufferedReader(input.path())) {
      new JsonLinesInputAdapter()
          .process(
              reader,
              input.mode(),
              event -> {
                checkCancelled(cancellation);
                state.received++;
                String location = input.path() + ":line " + event.lineNumber();
                switch (event) {
                  case JsonLinesCanonicalEvent canonical -> {
                    state.parsed++;
                    state.normalized++;
                    processCanonical(
                        canonical.event(),
                        location,
                        Optional.of(canonical.sourceText()),
                        prepared,
                        sink,
                        cancellation,
                        state);
                  }
                  case JsonLinesError error ->
                      handleFailure(
                          state,
                          PipelineStage.PARSE,
                          location,
                          Optional.empty(),
                          error.reason() + ": " + error.detail(),
                          DeadLetterCategory.MALFORMED_INPUT,
                          Retryability.NON_RETRYABLE,
                          state.captureText(error.sourceText()));
                }
                state.publishMetrics();
              });
    } catch (Stopped stopped) {
      throw stopped;
    }
  }

  private void processCsv(
      PipelineInput.Csv input,
      PreparedPipeline prepared,
      OutputSink sink,
      PipelineCancellation cancellation,
      RunState state)
      throws IOException, OutputSinkException {
    try (BufferedReader reader = Files.newBufferedReader(input.path())) {
      new CsvInputAdapter()
          .process(
              reader,
              input.config(),
              input.mode(),
              event -> {
                checkCancelled(cancellation);
                state.received++;
                String location = input.path() + ":row " + event.rowNumber();
                switch (event) {
                  case CsvCanonicalEvent canonical -> {
                    state.parsed++;
                    state.normalized++;
                    processCanonical(
                        canonical.event(),
                        location,
                        Optional.of(canonical.sourceText()),
                        prepared,
                        sink,
                        cancellation,
                        state);
                  }
                  case CsvError error ->
                      handleFailure(
                          state,
                          PipelineStage.PARSE,
                          location,
                          Optional.empty(),
                          error.reason() + ": " + error.detail(),
                          DeadLetterCategory.MALFORMED_INPUT,
                          Retryability.NON_RETRYABLE,
                          state.captureText(error.sourceText()));
                }
                state.publishMetrics();
              });
    } catch (Stopped stopped) {
      throw stopped;
    }
  }

  private void processStp(
      PipelineInput.StpBinary input,
      PreparedPipeline prepared,
      OutputSink sink,
      PipelineCancellation cancellation,
      RunState state)
      throws IOException, OutputSinkException {
    IncrementalStpDecoder decoder = new IncrementalStpDecoder(input.maximumFrameSize(), false);
    StpNormalizer normalizer = new StpNormalizer();
    Map<OrderId, StpOrderState> orders = new HashMap<>();
    try (InputStream stream = new BufferedInputStream(Files.newInputStream(input.path()))) {
      byte[] bytes = new byte[STP_READ_BUFFER_SIZE];
      int count;
      while (!cancellation.isCancelled() && (count = stream.read(bytes)) != -1) {
        Optional<DeadLetterPayload> chunkPayload = state.captureBinary(bytes, count);
        for (StpParseEvent event : decoder.feed(ByteBuffer.wrap(bytes, 0, count))) {
          processStpEvent(
              event, input, normalizer, orders, chunkPayload, prepared, sink, cancellation, state);
        }
      }
      if (!cancellation.isCancelled()) {
        for (StpParseEvent event : decoder.endOfInput()) {
          processStpEvent(
              event,
              input,
              normalizer,
              orders,
              Optional.empty(),
              prepared,
              sink,
              cancellation,
              state);
        }
      }
    }
  }

  private void processStpEvent(
      StpParseEvent event,
      PipelineInput.StpBinary input,
      StpNormalizer normalizer,
      Map<OrderId, StpOrderState> orders,
      Optional<DeadLetterPayload> rawPayload,
      PreparedPipeline prepared,
      OutputSink sink,
      PipelineCancellation cancellation,
      RunState state)
      throws OutputSinkException {
    checkCancelled(cancellation);
    state.received++;
    long frameNumber = ++state.stpFrameNumber;
    String location = input.path() + ":frame " + frameNumber;
    switch (event) {
      case StpParseFailure failure ->
          handleFailure(
              state,
              PipelineStage.PARSE,
              location,
              Optional.empty(),
              detail(failure.error()),
              DeadLetterCategory.MALFORMED_INPUT,
              Retryability.NON_RETRYABLE,
              rawPayload);
      case ParsedStpFrame parsed -> {
        state.parsed++;
        StpDecodeResult decoded = parsed.result();
        if (decoded instanceof StpMessage message) {
          state.trackSequence(input.source().value(), message.header().sequenceNumber().value());
        }
        if (decoded instanceof AddOrderMessage addOrder) {
          orders.put(
              addOrder.orderId(),
              new StpOrderState(
                  new InstrumentReference(addOrder.symbol()), addOrder.quantity().value()));
        }
        StpNormalizationResult normalized =
            normalizer.normalize(
                decoded,
                new StpNormalizationContext(
                    input.source(),
                    input.venue(),
                    Optional.empty(),
                    new RawEventReference(
                        "stp:" + input.source().value() + ":frame:" + frameNumber),
                    orderId ->
                        Optional.ofNullable(orders.get(orderId)).map(StpOrderState::instrument)));
        switch (normalized) {
          case NormalizedStpEvent success -> {
            state.normalized++;
            processCanonical(
                success.event(), location, Optional.empty(), prepared, sink, cancellation, state);
          }
          case StpNormalizationFailure failure ->
              handleFailure(
                  state,
                  PipelineStage.NORMALIZE,
                  location,
                  Optional.empty(),
                  failure.reason() + ": " + failure.detail(),
                  DeadLetterCategory.NORMALIZATION,
                  Retryability.NON_RETRYABLE,
                  rawPayload);
        }
        if (decoded instanceof CancelOrderMessage cancel) {
          reduceRemainingQuantity(orders, cancel.orderId(), cancel.canceledQuantity().value());
        } else if (decoded instanceof ExecuteOrderMessage execute) {
          reduceRemainingQuantity(orders, execute.orderId(), execute.executedQuantity().value());
        }
      }
    }
    state.publishMetrics();
  }

  private static void reduceRemainingQuantity(
      Map<OrderId, StpOrderState> orders, OrderId orderId, long quantity) {
    StpOrderState order = orders.get(orderId);
    if (order == null) {
      return;
    }
    long remaining = order.remainingQuantity() - quantity;
    if (remaining <= 0) {
      orders.remove(orderId);
    } else {
      orders.put(orderId, new StpOrderState(order.instrument(), remaining));
    }
  }

  private void processCanonical(
      CanonicalEvent event,
      String location,
      Optional<String> sourceText,
      PreparedPipeline prepared,
      OutputSink sink,
      PipelineCancellation cancellation,
      RunState state) {
    long processingStarted = System.nanoTime();
    try {
      checkCancelled(cancellation);
      CanonicalEventDocument document;
      if (prepared.transformationExecutor.isPresent()) {
        TransformationResult result = prepared.transformationExecutor.orElseThrow().execute(event);
        switch (result) {
          case TransformationResult.Transformed transformed -> document = transformed.document();
          case TransformationResult.Filtered ignored -> {
            state.filtered++;
            return;
          }
          case TransformationResult.Failed failure -> {
            handleFailure(
                state,
                PipelineStage.TRANSFORM,
                location,
                Optional.of(event.metadata().eventId().value()),
                failure.failure().detail(),
                DeadLetterCategory.TRANSFORMATION,
                Retryability.NON_RETRYABLE,
                state.captureText(sourceText));
            return;
          }
        }
      } else {
        document = CanonicalEventDocument.fromCanonicalEvent(event);
      }
      checkCancelled(cancellation);
      OutputRecord record;
      if (prepared.blueprint.isPresent()) {
        BlueprintPreviewResult result =
            prepared.blueprintService.preview(
                prepared.blueprint.orElseThrow(), event, Optional.of(document));
        switch (result) {
          case BlueprintPreviewResult.Rendered rendered ->
              record = OutputRecord.from(rendered.document());
          case BlueprintPreviewResult.Failed failure -> {
            handleFailure(
                state,
                PipelineStage.BLUEPRINT,
                location + failure.failure().location(),
                Optional.of(event.metadata().eventId().value()),
                failure.failure().detail(),
                DeadLetterCategory.BLUEPRINT,
                Retryability.NON_RETRYABLE,
                state.captureText(sourceText));
            return;
          }
        }
      } else {
        record = OutputRecord.from(document);
      }
      try {
        sink.write(record);
        state.emitted++;
      } catch (OutputSinkException exception) {
        handleFailure(
            state,
            PipelineStage.OUTPUT,
            location,
            Optional.of(event.metadata().eventId().value()),
            detail(exception),
            DeadLetterCategory.OUTPUT,
            Retryability.RETRYABLE,
            state.captureText(sourceText));
        state.markTerminalFailure();
        throw Stopped.INSTANCE;
      }
    } finally {
      state.recordProcessing(System.nanoTime() - processingStarted);
    }
  }

  private static void checkCancelled(PipelineCancellation cancellation) {
    if (cancellation.isCancelled()) {
      throw Stopped.INSTANCE;
    }
  }

  private static String detail(Throwable exception) {
    String message = exception.getMessage();
    return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
  }

  private static void handleFailure(
      RunState state,
      PipelineStage stage,
      String location,
      Optional<String> eventId,
      String detail,
      DeadLetterCategory category,
      Retryability retryability,
      Optional<DeadLetterPayload> payload) {
    if (state.record(stage, location, eventId, detail, category, retryability, payload)) {
      state.markTerminalFailure();
      throw Stopped.INSTANCE;
    }
  }

  private record PreparedPipeline(
      Optional<CompiledTransformation> transformation,
      Optional<TransformationExecutor> transformationExecutor,
      Optional<CompiledOutputBlueprint> blueprint,
      OutputBlueprintService blueprintService) {}

  private static final class RunState {
    private static final Pattern SENSITIVE_VALUE =
        Pattern.compile("(?i)(password|token|secret|api[_-]?key)\\s*[:=]\\s*[^,\\s]+");
    private static final int MAXIMUM_SAFE_MESSAGE_LENGTH = 512;
    private long received;
    private long parsed;
    private long normalized;
    private long filtered;
    private long emitted;
    private long failed;
    private long stpFrameNumber;
    private long suppressedFailures;
    private long deadLettered;
    private long processingNanos;
    private long processedEventCount;
    private long sequenceGapCount;
    private long duplicateCount;
    private boolean terminalFailure;
    private final int maximumFailures;
    private final PipelineIdentity identity;
    private final Optional<DeadLetterConfig> deadLetterConfig;
    private final DeadLetterSession deadLetters;
    private final Clock clock;
    private final PipelineRunObserver observer;
    private final SequenceIntegrityTracker integrityTracker = new SequenceIntegrityTracker();
    private final List<PipelineFailure> failures = new ArrayList<>();

    private RunState(
        int maximumFailures,
        PipelineIdentity identity,
        Optional<DeadLetterConfig> deadLetterConfig,
        DeadLetterSession deadLetters,
        Clock clock,
        PipelineRunObserver observer) {
      this.maximumFailures = maximumFailures;
      this.identity = identity;
      this.deadLetterConfig = deadLetterConfig;
      this.deadLetters = deadLetters;
      this.clock = clock;
      this.observer = observer;
    }

    private boolean record(
        PipelineStage stage,
        String location,
        Optional<String> eventId,
        String detail,
        DeadLetterCategory category,
        Retryability retryability,
        Optional<DeadLetterPayload> payload) {
      String safeDetail = safeMessage(detail);
      recordTerminal(stage, location, safeDetail);
      if (deadLetterConfig.isEmpty()) {
        return false;
      }
      DeadLetterConfig configuration = deadLetterConfig.orElseThrow();
      if (configuration.policy() == DeadLetterPolicy.FAIL_FAST) {
        return true;
      }
      if (configuration.policy() == DeadLetterPolicy.SKIP) {
        return false;
      }
      Optional<DeadLetterPayload> retainedPayload =
          configuration.includePayload() ? payload : Optional.empty();
      DeadLetterRecord record =
          DeadLetterRecord.create(
              identity.pipelineId(),
              identity.pipelineVersion(),
              stage,
              location,
              eventId,
              category,
              safeDetail,
              retainedPayload,
              clock.instant(),
              retryability,
              CANONICAL_SCHEMA_VERSION,
              PIPELINE_SCHEMA_VERSION);
      try {
        deadLetters.write(record);
        deadLettered++;
        notifyDeadLetter(record);
        return false;
      } catch (OutputSinkException exception) {
        recordTerminal(PipelineStage.OUTPUT, "dead-letter", safeMessage(exception.getMessage()));
        return true;
      }
    }

    private int maximumPayloadBytes() {
      return deadLetterConfig.map(DeadLetterConfig::maximumPayloadBytes).orElse(0);
    }

    private Optional<DeadLetterPayload> captureText(String sourceText) {
      return captureText(Optional.of(sourceText));
    }

    private Optional<DeadLetterPayload> captureText(Optional<String> sourceText) {
      if (!capturesPayload() || sourceText.isEmpty()) {
        return Optional.empty();
      }
      return Optional.of(DeadLetterPayload.text(sourceText.orElseThrow(), maximumPayloadBytes()));
    }

    private Optional<DeadLetterPayload> captureBinary(byte[] source, int length) {
      if (!capturesPayload()) {
        return Optional.empty();
      }
      return Optional.of(DeadLetterPayload.binary(source, length, maximumPayloadBytes()));
    }

    private boolean capturesPayload() {
      return deadLetterConfig.map(DeadLetterConfig::includePayload).orElse(false);
    }

    private void recordTerminal(PipelineStage stage, String location, String detail) {
      failed++;
      if (failures.size() < maximumFailures) {
        failures.add(new PipelineFailure(stage, location, safeMessage(detail)));
      } else {
        suppressedFailures++;
      }
    }

    private void trackSequence(String source, long sequence) {
      SequenceIntegrityEvent event =
          integrityTracker.track(
              new SequenceSource(source), new io.streamforge.common.model.SequenceNumber(sequence));
      if (event.status() == SequenceIntegrityStatus.GAP_DETECTED) {
        sequenceGapCount += event.missingSequenceCount();
      } else if (event.status() == SequenceIntegrityStatus.DUPLICATE) {
        duplicateCount++;
      }
    }

    private void recordProcessing(long elapsedNanos) {
      processingNanos += Math.max(0, elapsedNanos);
      processedEventCount++;
    }

    private void markTerminalFailure() {
      terminalFailure = true;
    }

    private void publishMetrics() {
      try {
        observer.onMetrics(
            new PipelineRunMetrics(
                new PipelineCounters(received, parsed, normalized, filtered, emitted, failed),
                processingNanos,
                processedEventCount,
                sequenceGapCount,
                duplicateCount,
                0));
      } catch (RuntimeException ignored) {
        // Observability must not change pipeline processing semantics.
      }
    }

    private void notifyDeadLetter(DeadLetterRecord record) {
      try {
        observer.onDeadLetter(record);
      } catch (RuntimeException ignored) {
        // Observability must not change pipeline processing semantics.
      }
    }

    private static String safeMessage(String detail) {
      String normalized =
          detail == null || detail.isBlank()
              ? "unspecified failure"
              : detail.replaceAll("[\\r\\n\\t]+", " ");
      String redacted = SENSITIVE_VALUE.matcher(normalized).replaceAll("$1=[REDACTED]");
      return redacted.length() <= MAXIMUM_SAFE_MESSAGE_LENGTH
          ? redacted
          : redacted.substring(0, MAXIMUM_SAFE_MESSAGE_LENGTH) + "...";
    }

    private PipelineReport report(boolean cancelled) {
      publishMetrics();
      return new PipelineReport(
          new PipelineCounters(received, parsed, normalized, filtered, emitted, failed),
          failures,
          suppressedFailures,
          cancelled
              ? PipelineOutcome.CANCELLED
              : terminalFailure ? PipelineOutcome.FAILED : PipelineOutcome.COMPLETED);
    }
  }

  private static final class DeadLetterSession implements AutoCloseable {
    private final Optional<JsonLinesDeadLetterStore> store;

    private DeadLetterSession(Optional<JsonLinesDeadLetterStore> store) {
      this.store = store;
    }

    private static DeadLetterSession open(Optional<DeadLetterConfig> configuration) {
      Optional<JsonLinesDeadLetterStore> store =
          configuration
              .filter(value -> value.policy() == DeadLetterPolicy.QUARANTINE)
              .map(value -> new JsonLinesDeadLetterStore(value.path().orElseThrow()));
      return new DeadLetterSession(store);
    }

    private void start() throws OutputSinkException {
      if (store.isPresent()) {
        store.orElseThrow().start();
      }
    }

    private void write(DeadLetterRecord record) throws OutputSinkException {
      if (store.isEmpty()) {
        throw new IllegalStateException("dead-letter store is not configured");
      }
      store.orElseThrow().write(record);
    }

    private void complete() throws OutputSinkException {
      if (store.isPresent()) {
        store.orElseThrow().complete();
      }
    }

    @Override
    public void close() {
      store.ifPresent(JsonLinesDeadLetterStore::close);
    }
  }

  private record StpOrderState(InstrumentReference instrument, long remainingQuantity) {}

  private static final class Stopped extends RuntimeException {
    private static final Stopped INSTANCE = new Stopped();

    private Stopped() {
      super(null, null, false, false);
    }
  }

  private static final class PreparationFailure extends RuntimeException {
    private final Path path;

    private PreparationFailure(Path path, Throwable cause) {
      super(cause);
      this.path = path;
    }
  }
}
