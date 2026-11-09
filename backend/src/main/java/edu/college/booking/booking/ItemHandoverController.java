package edu.college.booking.booking;

import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class ItemHandoverController {
    private final ItemHandoverService service;

    public ItemHandoverController(ItemHandoverService service) {
        this.service = service;
    }

    @PostMapping("/api/v1/reservations/{reservationId}/pickup")
    public ItemHandoverService.HandoverResult pickup(Authentication authentication,
            @PathVariable long reservationId,
            @RequestBody ItemHandoverService.PickupRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return service.pickup(authentication, reservationId, body, key);
    }

    @PostMapping(value = "/api/v1/reservations/{reservationId}/return-submission",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ItemHandoverService.HandoverResult submitReturn(Authentication authentication,
            @PathVariable long reservationId,
            @RequestPart(value = "photos", required = false) List<MultipartFile> photos,
            @RequestParam(value = "remark", required = false) String remark,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return service.submitReturn(authentication, reservationId, photos, remark, key);
    }

    @GetMapping("/api/v1/return-photos/{photoId}")
    public ResponseEntity<byte[]> readPhoto(Authentication authentication,
            @PathVariable long photoId) {
        var photo = service.readPhoto(authentication, photoId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(photo.contentType()))
                .body(photo.bytes());
    }
}
