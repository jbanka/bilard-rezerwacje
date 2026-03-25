package org.example.rezerwacje.dev;

import org.example.rezerwacje.config.JwtService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/dev")
@Profile("dev")
public class DevTokenController {

    private final JwtService jwtService;

    public DevTokenController(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    /**
     * Pomocniczy endpoint do generowania tokenów JWT na potrzeby testów POC.
     * Dostępny WYŁĄCZNIE w profilu 'dev' — nigdy nie trafia na produkcję.
     *
     * Przykład: GET /dev/token?userId=user1&email=user1@example.com
     */
    @GetMapping("/token")
    public ResponseEntity<Map<String, String>> token(
            @RequestParam String userId,
            @RequestParam String email) {

        String token = jwtService.generate(userId, email);
        return ResponseEntity.ok(Map.of(
                "token", token,
                "usage", "Authorization: Bearer " + token
        ));
    }
}
