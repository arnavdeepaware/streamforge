package io.streamforge.pipelineruntime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.streamforge.pipelineruntime.output.ParquetColumnType;
import io.streamforge.pipelineruntime.output.ParquetCompression;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PipelineConfigLoaderTest {
  @TempDir Path temporaryDirectory;

  @Test
  void loadsThePublicParquetContractAndDefaultsCompressionAndRowGroupSize() throws Exception {
    Path config = writeConfig("", validColumns());

    PipelineRunConfig loaded = new PipelineConfigLoader().load(config);

    assertThat(loaded.output())
        .isInstanceOfSatisfying(
            PipelineOutput.Parquet.class,
            parquet -> {
              assertThat(parquet.config().compression()).isEqualTo(ParquetCompression.SNAPPY);
              assertThat(parquet.config().rowGroupSizeBytes()).isEqualTo(134_217_728L);
              assertThat(parquet.config().columns())
                  .extracting(column -> column.type())
                  .containsExactly(
                      ParquetColumnType.TIMESTAMP_NANOS, ParquetColumnType.FIXED_DECIMAL);
            });
  }

  @Test
  void rejectsDuplicateColumnsInvalidScaleAndUnsupportedCompression() throws Exception {
    assertThatThrownBy(
            () ->
                new PipelineConfigLoader()
                    .load(
                        writeConfig(
                            "\"compression\":\"GZIP\",",
                            "[{\"name\":\"value\",\"path\":\"metadata.sequenceNumber\","
                                + "\"type\":\"INT64\",\"required\":true}]")))
        .isInstanceOf(PipelineConfigurationException.class)
        .hasMessageContaining("GZIP");

    assertThatThrownBy(
            () ->
                new PipelineConfigLoader()
                    .load(
                        writeConfig(
                            "",
                            "[{\"name\":\"value\",\"path\":\"metadata.sequenceNumber\","
                                + "\"type\":\"INT64\",\"scale\":2,\"required\":true}]")))
        .isInstanceOf(PipelineConfigurationException.class)
        .hasMessageContaining("scale");

    assertThatThrownBy(
            () ->
                new PipelineConfigLoader()
                    .load(
                        writeConfig(
                            "",
                            "[{\"name\":\"value\",\"path\":\"metadata.sequenceNumber\","
                                + "\"type\":\"INT64\",\"required\":true},"
                                + "{\"name\":\"value\",\"path\":\"metadata.exchangeTimestamp\","
                                + "\"type\":\"TIMESTAMP_NANOS\",\"required\":true}]")))
        .isInstanceOf(PipelineConfigurationException.class)
        .hasMessageContaining("unique");
  }

  private Path writeConfig(String parquetOptions, String columns) throws Exception {
    Path config = temporaryDirectory.resolve("pipeline-" + System.nanoTime() + ".json");
    Files.writeString(
        config,
        "{\"schemaVersion\":\"1.0\","
            + "\"input\":{\"type\":\"JSONL\",\"path\":\"input.jsonl\"},"
            + "\"output\":{\"type\":\"PARQUET\",\"path\":\"output.parquet\","
            + "\"parquet\":{"
            + parquetOptions
            + "\"columns\":"
            + columns
            + "}}}");
    return config;
  }

  private static String validColumns() {
    return "[{\"name\":\"exchange_timestamp\",\"path\":\"metadata.exchangeTimestamp\","
        + "\"type\":\"TIMESTAMP_NANOS\",\"required\":true},"
        + "{\"name\":\"price\",\"path\":\"payload.price\","
        + "\"type\":\"FIXED_DECIMAL\",\"scale\":4,\"required\":false}]";
  }
}
