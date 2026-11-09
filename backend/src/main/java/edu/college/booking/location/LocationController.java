package edu.college.booking.location;

import java.util.List;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.http.CacheControl;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LocationController {

    private final LocationService service;

    public LocationController(LocationService service) {
        this.service = service;
    }

    @org.springframework.web.bind.annotation.PatchMapping("/api/v1/locations/{id}")
    public Location update(Authentication authentication, @PathVariable long id, @org.springframework.web.bind.annotation.RequestBody LocationService.LocationInput input) {
        return service.update(authentication, id, input);
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/api/v1/locations/{id}")
    public ResponseEntity<Void> remove(Authentication authentication, @PathVariable long id) {
        service.remove(authentication, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value="/api/v1/locations/{id}/image", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> upload(Authentication authentication, @PathVariable long id, @RequestPart("file") MultipartFile file) throws IOException {
        service.uploadImage(authentication, id, file);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/locations/{id}/image")
    public ResponseEntity<byte[]> image(@PathVariable long id) {
        var image = service.image(id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType()))
                .header("X-Content-Type-Options", "nosniff").cacheControl(CacheControl.noStore()).body(image.bytes());
    }

    @GetMapping("/api/v1/locations")
    public List<Location> listLocations() {
        return service.listLocations();
    }
}
