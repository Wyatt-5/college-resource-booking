package edu.college.booking.tasks;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ReservationTaskController {
    public record ClaimInput(Integer limit) {}
    private final ReservationTaskService tasks;
    public ReservationTaskController(ReservationTaskService tasks) { this.tasks = tasks; }

    @PostMapping("/internal/tasks/claim")
    public ReservationTaskService.ClaimResult claim(@RequestBody(required = false) ClaimInput input) {
        return tasks.claim(input == null || input.limit() == null ? 5 : input.limit());
    }
    @PostMapping("/internal/tasks/{id}/complete")
    public ReservationTaskService.Result complete(@PathVariable long id, @RequestBody ReservationTaskService.Completion input) {
        return tasks.complete(id, input);
    }
    @PostMapping("/internal/tasks/{id}/fail")
    public ReservationTaskService.Result fail(@PathVariable long id, @RequestBody ReservationTaskService.Failure input) {
        return tasks.fail(id, input);
    }
    @GetMapping("/return-reminders")
    public ReservationTaskService.ReminderPage reminders(Authentication actor,
            @RequestParam(defaultValue = "false") boolean managed, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return tasks.reminders(actor, managed, page, size);
    }
}
