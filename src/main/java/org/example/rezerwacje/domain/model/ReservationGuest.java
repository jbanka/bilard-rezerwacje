package org.example.rezerwacje.domain.model;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "reservation_guests")
public class ReservationGuest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "notified_at")
    private OffsetDateTime notifiedAt;

    protected ReservationGuest() {
    }

    public ReservationGuest(Reservation reservation, String email) {
        this.reservation = reservation;
        this.email = email;
    }

    public UUID getId() { return id; }
    public Reservation getReservation() { return reservation; }
    public String getEmail() { return email; }
    public OffsetDateTime getNotifiedAt() { return notifiedAt; }
    public void setNotifiedAt(OffsetDateTime notifiedAt) { this.notifiedAt = notifiedAt; }
}
