package dev.dhruv.jobsearch.application;
import java.util.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/applications/{applicationId}/interviews")
public class InterviewRoundController {
    private final InterviewRoundService service;
    public InterviewRoundController(InterviewRoundService service){this.service=service;}
    @GetMapping public List<InterviewRoundService.View> list(@PathVariable UUID applicationId){return service.list(applicationId);}
    @PostMapping @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public InterviewRoundService.View create(@PathVariable UUID applicationId,@Valid @RequestBody InterviewRoundService.Command command){return service.create(applicationId,command);}
    @PutMapping("/{id}") public InterviewRoundService.View update(@PathVariable UUID applicationId,@PathVariable UUID id,@Valid @RequestBody InterviewRoundService.Command command){return service.update(applicationId,id,command);}
}
