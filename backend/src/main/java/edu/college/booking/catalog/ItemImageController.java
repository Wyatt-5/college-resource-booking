package edu.college.booking.catalog;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class ItemImageController {
    private final ItemImageService service;
    public ItemImageController(ItemImageService service){this.service=service;}
    @GetMapping("/api/v1/items/{itemId}/images")
    public List<ItemImage> list(@PathVariable long itemId){return service.list(itemId);}
    @PostMapping(value="/api/v1/items/{itemId}/images",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ItemImage> upload(@PathVariable long itemId,@RequestPart("file") MultipartFile file)throws IOException{
        ItemImage image=service.upload(itemId,file);return ResponseEntity.created(URI.create(image.url())).body(image);
    }
    @GetMapping("/api/v1/items/{itemId}/images/{imageId}")
    public ResponseEntity<byte[]> read(@PathVariable long itemId,@PathVariable long imageId)throws IOException{
        var content=service.read(itemId,imageId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(content.contentType()))
                .header("X-Content-Type-Options","nosniff").cacheControl(CacheControl.noStore()).body(content.bytes());
    }
    @DeleteMapping("/api/v1/items/{itemId}/images/{imageId}")
    public ResponseEntity<Void> delete(@PathVariable long itemId,@PathVariable long imageId){
        service.delete(itemId,imageId);return ResponseEntity.noContent().build();
    }
}
