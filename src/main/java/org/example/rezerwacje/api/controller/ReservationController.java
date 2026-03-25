package org.example.rezerwacje.api.controller;

import jakarta.validation.Valid;
import org.example.rezerwacje.api.dto.CreateReservationRequest;
import org.example.rezerwacje.api.dto.ReservationResponse;
import org.example.rezerwacje.config.UserPrincipal;
import org.example.rezerwacje.domain.service.ReservationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping
    public ResponseEntity<ReservationResponse> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateReservationRequest request) {

        var reservation = reservationService.create(
                principal.userId(), principal.email(), request);
        return ResponseEntity
                .created(URI.create("/api/v1/reservations/" + reservation.getId()))
                .body(ReservationResponse.from(reservation));
    }

    @GetMapping
    public ResponseEntity<List<ReservationResponse>> list() {
        List<ReservationResponse> result = reservationService.findAll().stream()
                .map(ReservationResponse::from)
                .toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReservationResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ReservationResponse.from(reservationService.findById(id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ReservationResponse> cancel(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {

        return ResponseEntity.ok(
                ReservationResponse.from(reservationService.cancel(id, principal.userId())));
    }
}
