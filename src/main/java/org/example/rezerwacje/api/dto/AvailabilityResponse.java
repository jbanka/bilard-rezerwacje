package org.example.rezerwacje.api.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record AvailabilityResponse(
        String date,
        int slotMinutes,
        List<SlotDto> slots
) {
    public record SlotDto(OffsetDateTime start, OffsetDateTime end, boolean available) {
    }
}
