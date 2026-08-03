package io.streamforge.controlplane.api;

import io.streamforge.controlplane.service.ApiValidationException;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Renders API failures using RFC 9457 problem details with stable field-level validation data. */
@RestControllerAdvice
public final class ApiExceptionHandler {
  @ExceptionHandler(ApiValidationException.class)
  public ResponseEntity<ProblemDetail> validation(ApiValidationException exception) {
    ProblemDetail detail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
    detail.setTitle("Validation failed");
    detail.setProperty("errors", exception.errors());
    return ResponseEntity.badRequest().body(detail);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ProblemDetail> unreadable(HttpMessageNotReadableException exception) {
    ProblemDetail detail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request body must be valid JSON");
    detail.setTitle("Validation failed");
    detail.setProperty("errors", List.of(new FieldViolation("request", "must be valid JSON")));
    return ResponseEntity.badRequest().body(detail);
  }

  @ExceptionHandler(NoSuchElementException.class)
  public ResponseEntity<ProblemDetail> missing(NoSuchElementException exception) {
    ProblemDetail detail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    detail.setTitle("Resource not found");
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(detail);
  }

  @ExceptionHandler({DataIntegrityViolationException.class, IllegalStateException.class})
  public ResponseEntity<ProblemDetail> conflict(RuntimeException exception) {
    ProblemDetail detail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT, "The requested state cannot be created");
    detail.setTitle("Resource conflict");
    return ResponseEntity.status(HttpStatus.CONFLICT).body(detail);
  }
}
