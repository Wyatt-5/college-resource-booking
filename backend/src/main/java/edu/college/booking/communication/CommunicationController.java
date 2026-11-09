package edu.college.booking.communication;

import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class CommunicationController {
    private final CommunicationService service;
    public CommunicationController(CommunicationService service){this.service=service;}
    @GetMapping("/announcements")
    public CommunicationService.Page<CommunicationService.Announcement> announcements(Authentication auth,
            @RequestParam(defaultValue="false") boolean managed,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){
        return service.announcements(auth,managed,page,size);
    }
    @PostMapping("/announcements")
    public ResponseEntity<CommunicationService.Announcement> create(Authentication auth,@RequestBody CommunicationService.AnnouncementInput input){
        var result=service.createAnnouncement(auth,input);return ResponseEntity.created(URI.create("/api/v1/announcements/"+result.id())).body(result);
    }
    @PutMapping("/announcements/{id}")
    public CommunicationService.Announcement update(Authentication auth,@PathVariable long id,@RequestBody CommunicationService.AnnouncementInput input){return service.updateAnnouncement(auth,id,input);}
    @PostMapping("/feedback")
    public ResponseEntity<CommunicationService.Feedback> feedback(Authentication auth,@RequestBody CommunicationService.FeedbackInput input){
        var result=service.createFeedback(auth,input);return ResponseEntity.created(URI.create("/api/v1/feedback/"+result.id())).body(result);
    }
    @GetMapping("/feedback")
    public CommunicationService.Page<CommunicationService.Feedback> feedbacks(Authentication auth,@RequestParam(defaultValue="false") boolean managed,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return service.feedbacks(auth,managed,page,size);}
    @PutMapping("/feedback/{id}")
    public CommunicationService.Feedback handle(Authentication auth,@PathVariable long id,@RequestBody CommunicationService.FeedbackUpdate input){return service.updateFeedback(auth,id,input);}
}
