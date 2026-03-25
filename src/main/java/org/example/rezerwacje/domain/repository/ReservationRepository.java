package org.example.rezerwacje.domain.repository;

import org.example.rezerwacje.domain.model.Reservation;
import org.example.rezerwacje.domain.model.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    @Query("""
            SELECT COUNT(r) FROM Reservation r
            WHERE r.status = :status
              AND r.startTime < :endTime
              AND r.endTime   > :startTime
            """)
    long countConflicts(@Param("startTime") OffsetDateTime startTime,
                        @Param("endTime") OffsetDateTime endTime,
                        @Param("status") ReservationStatus status);

    @Query("""
            SELECT r FROM Reservation r
            WHERE r.status = :status
              AND r.startTime < :endTime
              AND r.endTime   > :startTime
            ORDER BY r.startTime
            """)
    List<Reservation> findActiveInRange(@Param("startTime") OffsetDateTime startTime,
                                        @Param("endTime") OffsetDateTime endTime,
                                        @Param("status") ReservationStatus status);

    @Query("SELECT DISTINCT r FROM Reservation r LEFT JOIN FETCH r.guests ORDER BY r.startTime")
    List<Reservation> findAllWithGuests();

    @Query("SELECT r FROM Reservation r LEFT JOIN FETCH r.guests WHERE r.id = :id")
    Optional<Reservation> findByIdWithGuests(@Param("id") UUID id);

    List<Reservation> findByStatusOrderByStartTimeAsc(ReservationStatus status);
}
