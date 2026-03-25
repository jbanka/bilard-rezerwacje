package org.example.rezerwacje.domain.service;

import org.example.rezerwacje.api.dto.AvailabilityResponse;
import org.example.rezerwacje.api.dto.AvailabilityResponse.SlotDto;
import org.example.rezerwacje.config.AvailabilityProperties;
import org.example.rezerwacje.domain.model.Reservation;
import org.example.rezerwacje.domain.repository.ReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.ArrayList;
import java.util.List;

@Service
public class AvailabilityService {

    private final ReservationRepository reservationRepository;
    private final AvailabilityProperties props;

    public AvailabilityService(ReservationRepository reservationRepository,
                                AvailabilityProperties props) {
        this.reservationRepository = reservationRepository;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse forDate(LocalDate date, ZoneOffset zoneOffset) {
        LocalTime start = LocalTime.parse(props.dayStart());
        LocalTime end   = LocalTime.parse(props.dayEnd());

        OffsetDateTime dayStart = OffsetDateTime.of(date, start, zoneOffset);
        OffsetDateTime dayEnd   = OffsetDateTime.of(date, end, zoneOffset);

        List<Reservation> occupied = reservationRepository.findActiveInRange(dayStart, dayEnd);

        List<SlotDto> slots = new ArrayList<>();
        OffsetDateTime cursor = dayStart;

        while (!cursor.plusMinutes(props.slotMinutes()).isAfter(dayEnd)) {
            OffsetDateTime slotEnd = cursor.plusMinutes(props.slotMinutes());
            boolean available = isAvailable(cursor, slotEnd, occupied);
            slots.add(new SlotDto(cursor, slotEnd, available));
            cursor = slotEnd;
        }

        return new AvailabilityResponse(date.toString(), props.slotMinutes(), slots);
    }

    private boolean isAvailable(OffsetDateTime slotStart, OffsetDateTime slotEnd,
                                 List<Reservation> occupied) {
        for (Reservation r : occupied) {
            if (r.getStartTime().isBefore(slotEnd) && r.getEndTime().isAfter(slotStart)) {
                return false;
            }
        }
        return true;
    }
}
