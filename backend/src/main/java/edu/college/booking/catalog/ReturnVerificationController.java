package edu.college.booking.catalog;

import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class ReturnVerificationController {
    private final ReturnVerificationService service;
    public ReturnVerificationController(ReturnVerificationService service){this.service=service;}
    @GetMapping(value="/api/v1/reservations",params="status=RETURN_SUBMITTED")
    public List<ReservationReturnGateway.ReturnRecord> queue(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){
        return service.queue(page,size);
    }
    @PostMapping("/api/v1/reservations/{reservationId}/return-verification")
    public Map<String,Object> verify(@PathVariable long reservationId,@RequestBody Map<String,Object> body,
            @RequestHeader(value="Idempotency-Key",required=false) String key){return service.verify(reservationId,body,key);}
}
