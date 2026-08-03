package io.streamforge.parserengine.csv;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Bounded-memory CSV record reader supporting quoted delimiters and quoted line breaks. */
final class CsvRecordReader {

  private final PushbackReader input;
  private final char delimiter;
  private long physicalLine = 1;

  CsvRecordReader(Reader input, char delimiter) {
    this.input =
        new PushbackReader(
            input instanceof BufferedReader buffered ? buffered : new BufferedReader(input), 1);
    this.delimiter = delimiter;
  }

  Optional<CsvRecord> next() throws IOException {
    List<String> fields = new ArrayList<>();
    StringBuilder field = new StringBuilder();
    long startLine = physicalLine;
    boolean inQuotes = false;
    boolean fieldStarted = false;
    boolean afterClosingQuote = false;
    boolean sawCharacter = false;

    while (true) {
      int next = input.read();
      if (next == -1) {
        if (inQuotes) {
          throw syntax(startLine, "quoted field is not closed");
        }
        if (!sawCharacter && fields.isEmpty() && field.isEmpty()) {
          return Optional.empty();
        }
        fields.add(field.toString());
        return Optional.of(new CsvRecord(startLine, List.copyOf(fields)));
      }

      char character = (char) next;
      sawCharacter = true;
      if (inQuotes) {
        if (character == '"') {
          int following = input.read();
          if (following == '"') {
            field.append('"');
          } else {
            inQuotes = false;
            afterClosingQuote = true;
            if (following != -1) {
              input.unread(following);
            }
          }
        } else {
          field.append(character);
          if (character == '\n') {
            physicalLine++;
          }
        }
        continue;
      }

      if (afterClosingQuote) {
        if (character == delimiter) {
          fields.add(field.toString());
          field.setLength(0);
          fieldStarted = false;
          afterClosingQuote = false;
          continue;
        }
        if (character == '\r' || character == '\n') {
          finishLine(character);
          fields.add(field.toString());
          return Optional.of(new CsvRecord(startLine, List.copyOf(fields)));
        }
        throw syntax(
            startLine, "characters after a closing quote must be a delimiter or line break");
      }

      if (character == delimiter) {
        fields.add(field.toString());
        field.setLength(0);
        fieldStarted = false;
      } else if (character == '"') {
        if (fieldStarted || !field.isEmpty()) {
          throw syntax(startLine, "quote must begin a field");
        }
        inQuotes = true;
        fieldStarted = true;
      } else if (character == '\r' || character == '\n') {
        finishLine(character);
        fields.add(field.toString());
        return Optional.of(new CsvRecord(startLine, List.copyOf(fields)));
      } else {
        field.append(character);
        fieldStarted = true;
      }
    }
  }

  private void finishLine(char character) throws IOException {
    if (character == '\r') {
      int following = input.read();
      if (following != '\n' && following != -1) {
        input.unread(following);
      }
    }
    physicalLine++;
  }

  private static CsvSyntaxException syntax(long row, String detail) {
    return new CsvSyntaxException(row, detail);
  }

  record CsvRecord(long startLine, List<String> fields) {}

  static final class CsvSyntaxException extends IOException {
    private final long row;

    CsvSyntaxException(long row, String message) {
      super(message);
      this.row = row;
    }

    long row() {
      return row;
    }
  }
}
