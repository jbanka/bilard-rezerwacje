package org.example.rezerwacje.domain.service;

import org.example.rezerwacje.api.dto.CreateReservationRequest;
import org.example.rezerwacje.api.exception.ConflictException;
import org.example.rezerwacje.api.exception.ForbiddenException;
import org.example.rezerwacje.api.exception.NotFoundException;
import org.example.rezerwacje.api.exception.ValidationException;
import org.example.rezerwacje.domain.model.Reservation;
import org.example.rezerwacje.domain.model.ReservationGuest;
import org.example.rezerwacje.domain.model.ReservationStatus;
import org.example.rezerwacje.domain.repository.ReservationRepository;
import org.example.rezerwacje.notification.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ReservationService {

    private static final int MIN_SLOT_MINUTES = 30;

    private final ReservationRepository reservationRepository;
    private final NotificationService notificationService;

    public ReservationService(ReservationRepository reservationRepository,
                               NotificationService notificationService) {
        this.reservationRepository = reservationRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public Reservation create(String ownerId, String ownerEmail,
                               CreateReservationRequest request) {
        OffsetDateTime start = request.startTime();
        OffsetDateTime end = request.endTime();

        if (Duration.between(start, end).toMinutes() < MIN_SLOT_MINUTES) {
            throw new ValidationException(
                    "Minimalny czas rezerwacji to " + MIN_SLOT_MINUTES + " minut.");
        }

        if (reservationRepository.countConflicts(start, end) > 0) {
            throw new ConflictException(
                    "Wybrany termin koliduje z istniejącą rezerwacją.");
        }

        Reservation reservation = new Reservation(ownerId, ownerEmail, start, end);

        if (request.guests() != null) {
            request.guests().forEach(email ->
                    reservation.getGuests().add(new ReservationGuest(reservation, email)));
        }

        Reservation saved = reservationRepository.save(reservation);
        notificationService.notifyCreated(saved);
        return saved;
    }

    @Transactional
    public Reservation cancel(UUID id, String callerId) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Rezerwacja nie istnieje: " + id));

        if (!reservation.getOwnerId().equals(callerId)) {
            throw new ForbiddenException("Możesz anulować tylko własne rezerwacje.");
        }

        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw new ConflictException("Rezerwacja jest już anulowana.");
        }

        reservation.setStatus(ReservationStatus.CANCELLED);
        reservation.setCancelledAt(OffsetDateTime.now());

        Reservation saved = reservationRepository.save(reservation);
        notificationService.notifyCancelled(saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public Reservation findById(UUID id) {
        return reservationRepository.findByIdWithGuests(id)
                .orElseThrow(() -> new NotFoundException("Rezerwacja nie istnieje: " + id));
    }

    @Transactional(readOnly = true)
    public List<Reservation> findAll() {
        return reservationRepository.findAllWithGuests();
    }
}
