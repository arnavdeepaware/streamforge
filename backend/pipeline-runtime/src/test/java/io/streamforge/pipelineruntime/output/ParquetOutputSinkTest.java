package io.streamforge.pipelineruntime.output;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.streamforge.common.model.FixedDecimal;
import io.streamforge.transform.config.FieldPath;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.example.data.simple.convert.GroupRecordConverter;
import org.apache.parquet.hadoop.ParquetFileReader;
import org.apache.parquet.io.ColumnIOFactory;
import org.apache.parquet.io.LocalInputFile;
import org.apache.parquet.io.MessageColumnIO;
import org.apache.parquet.io.RecordReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ParquetOutputSinkTest {
  @TempDir Path temporaryDirectory;

  @Test
  void writesEverySupportedTypeWithoutLosingNanosecondsOrDecimals() throws Exception {
    Path destination = temporaryDirectory.resolve("output.parquet");
    ParquetOutputSink sink = new ParquetOutputSink(destination, config(ParquetCompression.SNAPPY));

    sink.start();
    sink.write(
        new OutputRecord(
            Map.of(
                "symbol",
                "AAPL",
                "active",
                true,
                "sequence",
                Long.MAX_VALUE,
                "timestamp",
                1_700_000_000_123_456_789L,
                "price",
                new FixedDecimal(12_345L, 2))));
    sink.complete();

    try (ParquetFileReader reader = ParquetFileReader.open(new LocalInputFile(destination))) {
      Group row = firstRow(reader);
      assertThat(row.getString("symbol", 0)).isEqualTo("AAPL");
      assertThat(row.getBoolean("active", 0)).isTrue();
      assertThat(row.getLong("sequence", 0)).isEqualTo(Long.MAX_VALUE);
      assertThat(row.getLong("timestamp", 0)).isEqualTo(1_700_000_000_123_456_789L);
      assertThat(new BigInteger(row.getBinary("price", 0).getBytes()))
          .isEqualTo(BigInteger.valueOf(1_234_500L));
    }
  }

  @Test
  void acceptsOptionalMissingFieldsAndAllCompressionModes() throws Exception {
    for (ParquetCompression compression : ParquetCompression.values()) {
      Path destination = temporaryDirectory.resolve(compression + ".parquet");
      ParquetOutputSink sink = new ParquetOutputSink(destination, config(compression));
      sink.start();
      sink.write(
          new OutputRecord(
              Map.of(
                  "symbol",
                  "AAPL",
                  "active",
                  false,
                  "sequence",
                  Long.MIN_VALUE,
                  "timestamp",
                  1L,
                  "price",
                  new FixedDecimal(123L, 4))));
      sink.complete();
      assertThat(Files.size(destination)).isPositive();
    }
  }

  @Test
  void rejectsMissingWrongAndLossyValuesWithoutPublishing() throws Exception {
    Path destination = temporaryDirectory.resolve("failed.parquet");
    ParquetOutputSink sink =
        new ParquetOutputSink(destination, config(ParquetCompression.UNCOMPRESSED));
    sink.start();

    assertThatThrownBy(() -> sink.write(new OutputRecord(Map.of("symbol", 12L))))
        .isInstanceOfSatisfying(
            OutputSinkException.class,
            exception ->
                assertThat(exception.failure().stage()).isEqualTo(OutputSinkFailureStage.WRITE));
    assertThat(destination).doesNotExist();

    Path lossyDestination = temporaryDirectory.resolve("lossy.parquet");
    ParquetOutputConfig lossyConfig =
        new ParquetOutputConfig(
            ParquetCompression.UNCOMPRESSED,
            ParquetOutputConfig.MINIMUM_ROW_GROUP_SIZE_BYTES,
            List.of(column("price", "price", ParquetColumnType.FIXED_DECIMAL, true, 2)));
    ParquetOutputSink lossy = new ParquetOutputSink(lossyDestination, lossyConfig);
    lossy.start();
    assertThatThrownBy(
            () -> lossy.write(new OutputRecord(Map.of("price", new FixedDecimal(12_345L, 3)))))
        .isInstanceOf(OutputSinkException.class)
        .hasMessageContaining("lossy");
    assertThat(lossyDestination).doesNotExist();
  }

  private static Group firstRow(ParquetFileReader reader) throws Exception {
    MessageColumnIO columns =
        new ColumnIOFactory().getColumnIO(reader.getFooter().getFileMetaData().getSchema());
    RecordReader<Group> records =
        columns.getRecordReader(
            reader.readNextRowGroup(),
            new GroupRecordConverter(reader.getFooter().getFileMetaData().getSchema()));
    return records.read();
  }

  private static ParquetOutputConfig config(ParquetCompression compression) {
    return new ParquetOutputConfig(
        compression,
        ParquetOutputConfig.MINIMUM_ROW_GROUP_SIZE_BYTES,
        List.of(
            column("symbol", "symbol", ParquetColumnType.STRING, true, null),
            column("active", "active", ParquetColumnType.BOOLEAN, true, null),
            column("sequence", "sequence", ParquetColumnType.INT64, true, null),
            column("timestamp", "timestamp", ParquetColumnType.TIMESTAMP_NANOS, true, null),
            column("price", "price", ParquetColumnType.FIXED_DECIMAL, false, 4)));
  }

  private static ParquetOutputColumn column(
      String name, String path, ParquetColumnType type, boolean required, Integer scale) {
    return new ParquetOutputColumn(
        name,
        new FieldPath(path),
        type,
        required,
        scale == null ? OptionalInt.empty() : OptionalInt.of(scale));
  }
}
