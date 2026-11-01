package edu.college.booking.booking;

import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ItemReservationController {
    private final ItemReservationService service;

    public ItemReservationController(ItemReservationService service) {
        this.service = service;
    }

    @PostMapping("/reservations")
    public ResponseEntity<ItemReservation> create(Authentication authentication,
            @RequestBody ItemReservationService.CreateRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        ItemReservation created = service.create(authentication, body, key);
        return ResponseEntity.created(URI.create("/api/v1/reservations/" + created.id())).body(created);
    }

    @GetMapping("/me/reservations")
    public ItemReservationService.ReservationPage mine(Authentication authentication,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.mine(authentication, page, size);
    }
}
