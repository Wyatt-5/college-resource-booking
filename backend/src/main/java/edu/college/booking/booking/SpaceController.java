package edu.college.booking.booking;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/spaces")
public class SpaceController {
    private final SpaceService service;
    public SpaceController(SpaceService service) { this.service = service; }

    @GetMapping
    public List<SpaceService.Space> list() { return service.list(); }

    @PostMapping
    public ResponseEntity<SpaceService.Space> create(Authentication authentication,
            @RequestBody SpaceService.SpaceInput input) {
        SpaceService.Space space = service.create(authentication, input);
        return ResponseEntity.created(URI.create("/api/v1/spaces/" + space.id())).body(space);
    }

    @GetMapping("/{id}/slots")
    public List<SpaceService.Slot> slots(@PathVariable long id, @RequestParam LocalDate date) {
        return service.slots(id, date);
    }

    @PostMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SpaceService.Space upload(Authentication authentication, @PathVariable long id,
            @RequestPart("file") MultipartFile file) throws IOException {
        return service.uploadImage(authentication, id, file);
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> image(@PathVariable long id) {
        SpaceService.ImageContent image = service.image(id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType()))
                .header("X-Content-Type-Options", "nosniff").cacheControl(CacheControl.noStore())
                .body(image.bytes());
    }
}
