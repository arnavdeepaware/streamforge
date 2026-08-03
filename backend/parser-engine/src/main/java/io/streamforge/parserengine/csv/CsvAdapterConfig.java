package io.streamforge.parserengine.csv;

import io.streamforge.common.model.Side;
import io.streamforge.common.model.SourceIdentity;
import io.streamforge.common.model.Venue;
import java.util.Map;
import java.util.Optional;

/** Immutable explicit column mapping for the initial CSV trade adapter. */
public record CsvAdapterConfig(
    char delimiter,
    boolean hasHeader,
    String timestampColumn,
    CsvTimestampFormat timestampFormat,
    String symbolColumn,
    Optional<String> venueColumn,
    Optional<Venue> constantVenue,
    Optional<String> priceMantissaColumn,
    Optional<String> decimalPriceColumn,
    int priceScale,
    String quantityColumn,
    String sideColumn,
    Map<String, Side> sideMapping,
    SourceIdentity source) {

  public CsvAdapterConfig {
    if (delimiter == '"' || delimiter == '\r' || delimiter == '\n' || delimiter == 0) {
      throw new IllegalArgumentException("delimiter must be a nonzero non-quote character");
    }
    requireColumn(timestampColumn, "timestampColumn");
    if (timestampFormat == null) {
      throw new IllegalArgumentException("timestampFormat must not be null");
    }
    requireColumn(symbolColumn, "symbolColumn");
    if (venueColumn == null || constantVenue == null) {
      throw new IllegalArgumentException("venue mapping optionals must not be null");
    }
    if (venueColumn.isPresent() == constantVenue.isPresent()) {
      throw new IllegalArgumentException("configure exactly one of venueColumn or constantVenue");
    }
    if (priceMantissaColumn == null || decimalPriceColumn == null) {
      throw new IllegalArgumentException("price mapping optionals must not be null");
    }
    if (priceMantissaColumn.isPresent() == decimalPriceColumn.isPresent()) {
      throw new IllegalArgumentException(
          "configure exactly one of priceMantissaColumn or decimalPriceColumn");
    }
    if (priceScale < 0 || priceScale > 18) {
      throw new IllegalArgumentException("priceScale must be between 0 and 18");
    }
    requireColumn(quantityColumn, "quantityColumn");
    requireColumn(sideColumn, "sideColumn");
    if (sideMapping == null || sideMapping.isEmpty()) {
      throw new IllegalArgumentException("sideMapping must not be empty");
    }
    sideMapping = Map.copyOf(sideMapping);
    if (source == null) {
      throw new IllegalArgumentException("source must not be null");
    }
  }

  private static void requireColumn(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
  }
}
