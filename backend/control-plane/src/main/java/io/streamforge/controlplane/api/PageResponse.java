package io.streamforge.controlplane.api;

import java.util.List;

/** Stable offset-page response used by version one list endpoints. */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {
  public PageResponse {
    items = List.copyOf(items);
  }
}
