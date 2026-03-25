package org.example.rezerwacje.api.dto;

import org.example.rezerwacje.domain.model.Reservation;
import org.example.rezerwacje.domain.model.ReservationStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ReservationResponse(
        UUID id,
        String ownerId,
        String ownerEmail,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        ReservationStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime cancelledAt,
        List<String> guests
) {
    public static ReservationResponse from(Reservation r) {
        List<String> guestEmails = r.getGuests().stream()
                .map(g -> g.getEmail())
                .toList();
        return new ReservationResponse(
                r.getId(), r.getOwnerId(), r.getOwnerEmail(),
                r.getStartTime(), r.getEndTime(),
                r.getStatus(), r.getCreatedAt(), r.getCancelledAt(),
                guestEmails
        );
    }
}
