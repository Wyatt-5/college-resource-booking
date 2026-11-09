package edu.college.booking.auth;
import java.io.IOException;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
@RestController
@RequestMapping("/api/v1/users")
public class UserDetailsController {
    private final UserDetailsService service;
    public UserDetailsController(UserDetailsService service){this.service=service;}
    @GetMapping("/me/details") public UserDetailsService.Details mine(Authentication auth){return service.mine(auth);}
    @PatchMapping("/me/details") public UserDetailsService.Details update(Authentication auth,@RequestBody UserDetailsService.Input input){return service.update(auth,input);}
    @PostMapping(value="/me/avatar",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> upload(Authentication auth,@RequestPart("file") MultipartFile file) throws IOException {service.upload(auth,file);return ResponseEntity.noContent().build();}
    @GetMapping("/{id}/avatar") public ResponseEntity<byte[]> avatar(Authentication auth,@PathVariable long id){var image=service.avatar(auth,id);return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.contentType())).header("X-Content-Type-Options","nosniff").cacheControl(CacheControl.noStore()).body(image.bytes());}
}
