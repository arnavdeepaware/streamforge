package io.streamforge.parserengine;

import io.streamforge.common.model.CanonicalEvent;
import io.streamforge.common.model.CanonicalSchemaVersion;
import io.streamforge.common.model.EventMetadata;
import io.streamforge.common.model.InstrumentReference;
import io.streamforge.common.model.OrderAdded;
import io.streamforge.common.model.OrderCancelled;
import io.streamforge.common.model.OrderExecuted;
import io.streamforge.common.model.Trade;
import io.streamforge.stp.protocol.AddOrderMessage;
import io.streamforge.stp.protocol.CancelOrderMessage;
import io.streamforge.stp.protocol.ExecuteOrderMessage;
import io.streamforge.stp.protocol.FrameHeader;
import io.streamforge.stp.protocol.StpDecodeResult;
import io.streamforge.stp.protocol.TradeMessage;
import io.streamforge.stp.protocol.UnknownMessageFrame;
import java.util.Optional;

/** Normalizes validated STP v1 decoded frames without transport or serialization concerns. */
public final class StpNormalizer {

  /** Normalizes one known STP message or reports a typed reason why no canonical event was made. */
  public StpNormalizationResult normalize(
      StpDecodeResult decodedFrame, StpNormalizationContext context) {
    if (decodedFrame == null) {
      return failure(
          StpNormalizationFailureReason.INVALID_INPUT, "decoded STP frame must not be null");
    }
    if (context == null) {
      return failure(
          StpNormalizationFailureReason.INVALID_CONTEXT,
          "STP normalization context must not be null");
    }

    try {
      return switch (decodedFrame) {
        case AddOrderMessage message ->
            success(
                message.header(),
                new InstrumentReference(message.symbol()),
                new OrderAdded(
                    message.orderId(), message.side(), message.quantity(), message.price()),
                context);
        case ExecuteOrderMessage message -> normalizeExecution(message, context);
        case CancelOrderMessage message -> normalizeCancellation(message, context);
        case TradeMessage message ->
            success(
                message.header(),
                new InstrumentReference(message.symbol()),
                new Trade(
                    message.tradeId(),
                    Optional.of(message.aggressorSide()),
                    message.quantity(),
                    message.price()),
                context);
        case UnknownMessageFrame message ->
            failure(
                StpNormalizationFailureReason.UNSUPPORTED_MESSAGE_TYPE,
                "STP message type " + message.messageTypeCode() + " is not assigned by STP v1");
      };
    } catch (RuntimeException error) {
      return failure(StpNormalizationFailureReason.NORMALIZATION_ERROR, detail(error));
    }
  }

  private StpNormalizationResult normalizeExecution(
      ExecuteOrderMessage message, StpNormalizationContext context) {
    return resolveInstrument(message.orderId(), context)
        .<StpNormalizationResult>map(
            instrument ->
                success(
                    message.header(),
                    instrument,
                    new OrderExecuted(message.orderId(), message.executedQuantity()),
                    context))
        .orElseGet(
            () ->
                failure(
                    StpNormalizationFailureReason.INSTRUMENT_NOT_RESOLVED,
                    "no instrument is known for STP order " + message.orderId()));
  }

  private StpNormalizationResult normalizeCancellation(
      CancelOrderMessage message, StpNormalizationContext context) {
    return resolveInstrument(message.orderId(), context)
        .<StpNormalizationResult>map(
            instrument ->
                success(
                    message.header(),
                    instrument,
                    new OrderCancelled(message.orderId(), message.canceledQuantity()),
                    context))
        .orElseGet(
            () ->
                failure(
                    StpNormalizationFailureReason.INSTRUMENT_NOT_RESOLVED,
                    "no instrument is known for STP order " + message.orderId()));
  }

  private static Optional<InstrumentReference> resolveInstrument(
      io.streamforge.common.model.OrderId orderId, StpNormalizationContext context) {
    Optional<InstrumentReference> instrument = context.orderInstrumentResolver().resolve(orderId);
    if (instrument == null) {
      throw new IllegalArgumentException("order instrument resolver must not return null");
    }
    return instrument;
  }

  private static NormalizedStpEvent success(
      FrameHeader header,
      InstrumentReference instrument,
      io.streamforge.common.model.MarketEvent payload,
      StpNormalizationContext context) {
    EventMetadata metadata =
        EventMetadata.create(
            CanonicalSchemaVersion.V1_0,
            context.source(),
            context.venue(),
            header.eventTimestamp(),
            context.receiveTimestamp(),
            header.sequenceNumber(),
            context.rawEventReference());
    return new NormalizedStpEvent(new CanonicalEvent(metadata, instrument, payload));
  }

  private static StpNormalizationFailure failure(
      StpNormalizationFailureReason reason, String detail) {
    return new StpNormalizationFailure(reason, detail);
  }

  private static String detail(RuntimeException error) {
    if (error.getMessage() == null || error.getMessage().isBlank()) {
      return error.getClass().getSimpleName();
    }
    return error.getMessage();
  }
}
