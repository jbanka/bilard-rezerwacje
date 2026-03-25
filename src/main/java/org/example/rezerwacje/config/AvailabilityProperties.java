package org.example.rezerwacje.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.availability")
public record AvailabilityProperties(String dayStart, String dayEnd, int slotMinutes) {
}
