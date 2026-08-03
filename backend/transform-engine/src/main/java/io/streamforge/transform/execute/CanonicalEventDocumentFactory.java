package io.streamforge.transform.execute;

import io.streamforge.common.model.CanonicalEvent;
import io.streamforge.common.model.OrderAdded;
import io.streamforge.common.model.OrderCancelled;
import io.streamforge.common.model.OrderExecuted;
import io.streamforge.common.model.Quote;
import io.streamforge.common.model.QuoteLevel;
import io.streamforge.common.model.Trade;
import java.util.LinkedHashMap;
import java.util.Map;

/** Creates an exact, detached document view of one canonical event. */
final class CanonicalEventDocumentFactory {

  private CanonicalEventDocumentFactory() {}

  static Map<String, Object> mutableDocument(CanonicalEvent event) {
    if (event == null) {
      throw new IllegalArgumentException("canonical event must not be null");
    }
    LinkedHashMap<String, Object> root = new LinkedHashMap<>();
    root.put("metadata", metadata(event));
    root.put("instrument", Map.of("symbol", event.instrument().symbol().value()));
    root.put("payload", payload(event));
    return root;
  }

  private static Map<String, Object> metadata(CanonicalEvent event) {
    var metadata = event.metadata();
    LinkedHashMap<String, Object> values = new LinkedHashMap<>();
    values.put("eventId", metadata.eventId().value());
    values.put(
        "schemaVersion",
        Map.of(
            "major", (long) metadata.schemaVersion().major(),
            "minor", (long) metadata.schemaVersion().minor()));
    values.put("source", metadata.source().value());
    values.put("venue", metadata.venue().value());
    values.put("exchangeTimestamp", metadata.exchangeTimestamp().nanosecondsSinceEpoch());
    metadata
        .receiveTimestamp()
        .ifPresent(value -> values.put("receiveTimestamp", value.nanosecondsSinceEpoch()));
    values.put("sequenceNumber", metadata.sequenceNumber().value());
    values.put("rawEventReference", metadata.rawEventReference().value());
    return values;
  }

  private static Map<String, Object> payload(CanonicalEvent event) {
    LinkedHashMap<String, Object> values = new LinkedHashMap<>();
    values.put("type", event.type().name());
    switch (event.payload()) {
      case OrderAdded order -> {
        values.put("orderId", order.orderId().value());
        values.put("side", order.side().name());
        values.put("quantity", order.quantity().value());
        values.put("price", order.price());
      }
      case OrderExecuted execution -> {
        values.put("orderId", execution.orderId().value());
        values.put("executedQuantity", execution.executedQuantity().value());
      }
      case OrderCancelled cancellation -> {
        values.put("orderId", cancellation.orderId().value());
        values.put("cancelledQuantity", cancellation.cancelledQuantity().value());
      }
      case Trade trade -> addTrade(values, trade);
      case Quote quote -> addQuote(values, quote);
    }
    return values;
  }

  private static void addTrade(Map<String, Object> values, Trade trade) {
    values.put("tradeId", trade.tradeId().value());
    trade.aggressorSide().ifPresent(value -> values.put("aggressorSide", value.name()));
    values.put("quantity", trade.quantity().value());
    values.put("price", trade.price());
  }

  private static void addQuote(Map<String, Object> values, Quote quote) {
    quote.bid().ifPresent(value -> values.put("bid", quoteLevel(value)));
    quote.ask().ifPresent(value -> values.put("ask", quoteLevel(value)));
  }

  private static Map<String, Object> quoteLevel(QuoteLevel level) {
    return Map.of("price", level.price(), "quantity", level.quantity().value());
  }
}
