package org.example.rezerwacje.api.dto;

import java.time.OffsetDateTime;

public record ErrorResponse(int status, String error, String message, OffsetDateTime timestamp) {
    public static ErrorResponse of(int status, String error, String message) {
        return new ErrorResponse(status, error, message, OffsetDateTime.now());
    }
}
