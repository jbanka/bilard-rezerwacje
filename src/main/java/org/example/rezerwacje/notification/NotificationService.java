package org.example.rezerwacje.notification;

import org.example.rezerwacje.domain.model.Reservation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    public void notifyCreated(Reservation reservation) {
        String guests = reservation.getGuests().stream()
                .map(g -> g.getEmail())
                .collect(Collectors.joining(", "));
        log.info("[NOTIFY] CREATED  id={} owner={} start={} end={} guests=[{}]",
                reservation.getId(),
                reservation.getOwnerEmail(),
                reservation.getStartTime(),
                reservation.getEndTime(),
                guests);
    }

    public void notifyCancelled(Reservation reservation) {
        String guests = reservation.getGuests().stream()
                .map(g -> g.getEmail())
                .collect(Collectors.joining(", "));
        log.info("[NOTIFY] CANCELLED id={} owner={} cancelledAt={} guests=[{}]",
                reservation.getId(),
                reservation.getOwnerEmail(),
                reservation.getCancelledAt(),
                guests);
    }
}
