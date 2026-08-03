package io.streamforge.controlplane.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.streamforge.controlplane.api.PipelinePreviewRequest;
import io.streamforge.controlplane.api.PipelinePreviewResponse;
import org.junit.jupiter.api.Test;

class PipelinePreviewServiceTest {
  private final ObjectMapper mapper = new ObjectMapper();
  private final PipelinePreviewService previews = new PipelinePreviewService(mapper);

  @Test
  void executesTheProductionTransformAndBlueprintForAValidCanonicalSample() throws Exception {
    PipelinePreviewResponse response =
        previews.preview(new PipelinePreviewRequest(sample(), transformation(), blueprint()));

    assertThat(response.status()).isEqualTo("RENDERED");
    assertThat(response.errors()).isEmpty();
    assertThat(response.transformed().at("/instrument/ticker").asText()).isEqualTo("AAPL");
    assertThat(response.output().at("/event/ticker").asText()).isEqualTo("AAPL");
    assertThat(response.output().at("/event/price").asText()).isEqualTo("123.45");
  }

  private JsonNode sample() throws Exception {
    return mapper.readTree(
        """
        {"metadata":{"eventId":"c0676afdc91e20ff9e2d002343271ce62b63dbf2c9d59d27d23f59fd71a67072","schemaVersion":{"major":1,"minor":0},"source":"jsonl/fixture-1","venue":"XNAS","exchangeTimestamp":1000000000,"sequenceNumber":1,"rawEventReference":"pipeline-aapl:line-1"},"instrument":{"symbol":"AAPL"},"payload":{"type":"ORDER_ADDED","orderId":1001,"side":"BUY","quantity":100,"price":{"mantissa":12345,"scale":2}}}
        """);
  }

  private JsonNode transformation() throws Exception {
    return mapper.readTree(
        """
        {"schemaVersion":"1.0","operations":[{"op":"rename","from":"instrument.symbol","to":"instrument.ticker"}]}
        """);
  }

  private JsonNode blueprint() throws Exception {
    return mapper.readTree(
        """
        {"schemaVersion":"1.0","output":{"kind":"object","fields":{"event":{"kind":"object","fields":{"ticker":{"kind":"reference","source":"transformed","path":"instrument.ticker"},"price":{"kind":"format","source":"canonical","path":"payload.price","format":"FIXED_DECIMAL_PLAIN"}}}}}}
        """);
  }
}
