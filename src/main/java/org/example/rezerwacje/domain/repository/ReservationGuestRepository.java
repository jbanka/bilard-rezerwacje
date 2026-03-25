package org.example.rezerwacje.domain.repository;

import org.example.rezerwacje.domain.model.ReservationGuest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReservationGuestRepository extends JpaRepository<ReservationGuest, UUID> {
}
