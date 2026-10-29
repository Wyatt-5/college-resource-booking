package edu.college.booking.catalog;

import java.net.URI;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class ItemController {
    private final ItemService service;
    public ItemController(ItemService service){this.service=service;}
    @GetMapping("/api/v1/items")
    public ResponseEntity<?> list(@RequestParam(required=false) Long locationId,@RequestParam(defaultValue="") String keyword,
                         @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,
                         @RequestParam(defaultValue="false") boolean onlyAvailable,@RequestParam(required=false) String status,
                         @RequestParam(defaultValue="public") String scope, jakarta.servlet.http.HttpServletRequest request){
        ItemPage result=service.list(locationId,keyword,page,size,onlyAvailable,status,scope);
        boolean paged=request.getParameter("page")!=null||request.getParameter("size")!=null;
        return ResponseEntity.ok().header("X-Total-Count",Long.toString(result.totalElements()))
                .body(paged?result:result.content());
    }
    @GetMapping("/api/v1/items/{itemId}")
    public Item detail(@PathVariable long itemId,@RequestParam(defaultValue="false") boolean managed){return service.detail(itemId,managed);}
    @PostMapping("/api/v1/items")
    public ResponseEntity<Item> create(@RequestBody Map<String,Object> body,@RequestHeader(value="Idempotency-Key",required=false) String key){
        Item item=service.create(body,key);return ResponseEntity.created(URI.create("/api/v1/items/"+item.id())).body(item);
    }
    @PatchMapping("/api/v1/items/{itemId}")
    public Item patch(@PathVariable long itemId,@RequestBody Map<String,Object> body){return service.patch(itemId,body);}
    @GetMapping("/api/v1/items/{itemId}/units")
    public List<ItemUnit> units(@PathVariable long itemId){return service.units(itemId);}
    @PostMapping("/api/v1/items/{itemId}/units")
    public ResponseEntity<ItemUnit> addUnit(@PathVariable long itemId,@RequestBody Map<String,Object> body,
                                          @RequestHeader(value="Idempotency-Key",required=false) String key){
        ItemUnit unit=service.addUnit(itemId,body,key);
        return ResponseEntity.created(URI.create("/api/v1/items/"+itemId+"/units/"+unit.id())).body(unit);
    }
    @PatchMapping("/api/v1/items/{itemId}/units/{unitId}")
    public ItemUnit patchUnit(@PathVariable long itemId,@PathVariable long unitId,@RequestBody Map<String,Object> body){
        return service.patchUnit(itemId,unitId,body);
    }
}
