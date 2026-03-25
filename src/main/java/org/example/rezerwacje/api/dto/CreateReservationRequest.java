package org.example.rezerwacje.api.dto;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.List;

public record CreateReservationRequest(
        @NotNull OffsetDateTime startTime,
        @NotNull OffsetDateTime endTime,
        List<String> guests
) {
}
