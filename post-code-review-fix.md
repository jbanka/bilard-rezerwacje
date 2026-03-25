Wszystkie 15 fixów wdrożone, testy zielone. 
Podsumowanie zmian:

CR	        Plik	                        Zmiana
CR-01	    JwtService	                    .claims(Map) → .claim("email", email)
CR-02	    ReservationService	            cancel(): findById → findByIdWithGuests
CR-03	    GlobalExceptionHandler	        Dodano DateTimeException → 400
CR-04	    Reservation, ReservationGuest	Wszystkie @Column(name=...) explicit
CR-05	    ReservationService	            Walidacja endTime > startTime przed sprawdzeniem długości
CR-06/07	SecurityConfig	                Komentarze + frameOptions.sameOrigin()
CR-08	    ReservationRepository	        Magic string 'ACTIVE' → parametr ReservationStatus
CR-09		Reservation	                    getGuests() → unmodifiableList + addGuest()
CR-10		Reservation	                    createdAt → @PrePersist zamiast inicjalizacji w polu
CR-11		AvailabilityService	            LocalTime.parse() → cachowane w konstruktorze
CR-12		CreateReservationRequest	    @Email na elementach listy gości
CR-13		JwtAuthFilter	                catch (JwtException ignored) → log.debug(...)
CR-14		ReservationServiceTest	        Usunięto @MockitoSettings(LENIENT) z klasy
CR-15		ReservationControllerTest	    @Autowired ObjectMapper zamiast new ObjectMapper()


