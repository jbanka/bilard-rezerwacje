package org.example.rezerwacje.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.rezerwacje.config.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReservationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper mapper;

    private String tokenUser1;
    private String tokenUser2;

    private static final OffsetDateTime BASE =
            OffsetDateTime.of(2025, 6, 1, 10, 0, 0, 0, ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        tokenUser1 = jwtService.generate("user-1", "user1@example.com");
        tokenUser2 = jwtService.generate("user-2", "user2@example.com");
    }

    @Test
    void createReservation_valid_returns201() throws Exception {
        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer " + tokenUser1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationJson(BASE, BASE.plusMinutes(60))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.ownerId").value("user-1"));
    }

    @Test
    void createReservation_tooShort_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer " + tokenUser1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationJson(BASE, BASE.plusMinutes(20))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createReservation_conflict_returns409() throws Exception {
        String body = reservationJson(BASE, BASE.plusMinutes(60));

        // pierwsza — OK
        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer " + tokenUser1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        // druga nakładająca się — 409
        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer " + tokenUser2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void cancelReservation_ownReservation_returns200() throws Exception {
        // utwórz
        String location = mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer " + tokenUser1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationJson(BASE, BASE.plusMinutes(60))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");

        String id = location.substring(location.lastIndexOf('/') + 1);

        // anuluj
        mockMvc.perform(delete("/api/v1/reservations/" + id)
                        .header("Authorization", "Bearer " + tokenUser1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancelReservation_otherUser_returns403() throws Exception {
        // utwórz jako user1
        String location = mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer " + tokenUser1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationJson(BASE, BASE.plusMinutes(60))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");

        String id = location.substring(location.lastIndexOf('/') + 1);

        // próba anulowania jako user2 — 403
        mockMvc.perform(delete("/api/v1/reservations/" + id)
                        .header("Authorization", "Bearer " + tokenUser2))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAvailability_validDate_returns200WithSlots() throws Exception {
        mockMvc.perform(get("/api/v1/availability")
                        .header("Authorization", "Bearer " + tokenUser1)
                        .param("date", "2025-06-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2025-06-01"))
                .andExpect(jsonPath("$.slotMinutes").value(30))
                .andExpect(jsonPath("$.slots").isArray());
    }

    @Test
    void anyEndpoint_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/reservations"))
                .andExpect(status().isUnauthorized());
    }

    // --- helpers ---

    private String reservationJson(OffsetDateTime start, OffsetDateTime end) throws Exception {
        return mapper.writeValueAsString(Map.of(
                "startTime", start.toString(),
                "endTime", end.toString(),
                "guests", List.of()
        ));
    }
}
