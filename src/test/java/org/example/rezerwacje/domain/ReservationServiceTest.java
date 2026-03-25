package org.example.rezerwacje.domain;

import org.example.rezerwacje.api.dto.CreateReservationRequest;
import org.example.rezerwacje.api.exception.ConflictException;
import org.example.rezerwacje.api.exception.ForbiddenException;
import org.example.rezerwacje.api.exception.ValidationException;
import org.example.rezerwacje.domain.model.Reservation;
import org.example.rezerwacje.domain.model.ReservationStatus;
import org.example.rezerwacje.domain.repository.ReservationRepository;
import org.example.rezerwacje.domain.service.ReservationService;
import org.example.rezerwacje.notification.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private ReservationService reservationService;

    private static final String OWNER_ID = "user-1";
    private static final String OWNER_EMAIL = "user1@example.com";

    private final OffsetDateTime BASE = OffsetDateTime.of(2025, 4, 1, 10, 0, 0, 0, ZoneOffset.UTC);

    // --- create() ---

    @Test
    void create_validSlot_savesAndNotifies() {
        when(reservationRepository.countConflicts(any(), any(), any())).thenReturn(0L);
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));

        var request = new CreateReservationRequest(BASE, BASE.plusMinutes(60), List.of());

        Reservation result = reservationService.create(OWNER_ID, OWNER_EMAIL, request);

        assertThat(result.getOwnerId()).isEqualTo(OWNER_ID);
        assertThat(result.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
        verify(reservationRepository).save(any());
        verify(notificationService).notifyCreated(any());
    }

    @Test
    void create_tooShort_throwsValidationException() {
        var request = new CreateReservationRequest(BASE, BASE.plusMinutes(29), List.of());

        assertThatThrownBy(() -> reservationService.create(OWNER_ID, OWNER_EMAIL, request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("30");

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void create_exactlyMinimum_succeeds() {
        when(reservationRepository.countConflicts(any(), any(), any())).thenReturn(0L);
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));

        var request = new CreateReservationRequest(BASE, BASE.plusMinutes(30), List.of());

        assertThatCode(() -> reservationService.create(OWNER_ID, OWNER_EMAIL, request))
                .doesNotThrowAnyException();
    }

    @Test
    void create_conflictExists_throwsConflictException() {
        when(reservationRepository.countConflicts(any(), any(), any())).thenReturn(1L);
        var request = new CreateReservationRequest(BASE, BASE.plusMinutes(60), List.of());

        assertThatThrownBy(() -> reservationService.create(OWNER_ID, OWNER_EMAIL, request))
                .isInstanceOf(ConflictException.class);

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void create_touchingReservation_noConflict() {
        when(reservationRepository.countConflicts(BASE, BASE.plusMinutes(60), ReservationStatus.ACTIVE)).thenReturn(0L);
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));

        var request = new CreateReservationRequest(BASE, BASE.plusMinutes(60), List.of());

        assertThatCode(() -> reservationService.create(OWNER_ID, OWNER_EMAIL, request))
                .doesNotThrowAnyException();
    }

    @Test
    void create_withGuests_savesGuests() {
        when(reservationRepository.countConflicts(any(), any(), any())).thenReturn(0L);
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));

        var request = new CreateReservationRequest(
                BASE, BASE.plusMinutes(60),
                List.of("guest1@example.com", "guest2@example.com"));

        Reservation result = reservationService.create(OWNER_ID, OWNER_EMAIL, request);

        assertThat(result.getGuests()).hasSize(2);
    }

    // --- cancel() ---

    @Test
    void cancel_ownReservation_cancelsAndNotifies() {
        UUID id = UUID.randomUUID();
        Reservation r = activeReservation(OWNER_ID);
        when(reservationRepository.findByIdWithGuests(id)).thenReturn(Optional.of(r));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(inv -> inv.getArgument(0));

        Reservation result = reservationService.cancel(id, OWNER_ID);

        assertThat(result.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(result.getCancelledAt()).isNotNull();
        verify(notificationService).notifyCancelled(any());
    }

    @Test
    void cancel_otherOwnersReservation_throwsForbidden() {
        UUID id = UUID.randomUUID();
        Reservation r = activeReservation("other-user");
        when(reservationRepository.findByIdWithGuests(id)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> reservationService.cancel(id, OWNER_ID))
                .isInstanceOf(ForbiddenException.class);

        verify(notificationService, never()).notifyCancelled(any());
    }

    @Test
    void cancel_alreadyCancelled_throwsConflict() {
        UUID id = UUID.randomUUID();
        Reservation r = activeReservation(OWNER_ID);
        r.setStatus(ReservationStatus.CANCELLED);
        when(reservationRepository.findByIdWithGuests(id)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> reservationService.cancel(id, OWNER_ID))
                .isInstanceOf(ConflictException.class);
    }

    // --- helpers ---

    private Reservation activeReservation(String ownerId) {
        Reservation r = new Reservation(ownerId, OWNER_EMAIL, BASE, BASE.plusHours(1));
        r.setStatus(ReservationStatus.ACTIVE);
        return r;
    }
}
