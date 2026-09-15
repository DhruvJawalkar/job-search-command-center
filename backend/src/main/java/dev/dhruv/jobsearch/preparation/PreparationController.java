package dev.dhruv.jobsearch.preparation;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/preparation")
public class PreparationController {
    private final PreparationService service;
    public PreparationController(PreparationService service){this.service=service;}

    @GetMapping("/overview") PreparationService.Overview overview(){return service.overview();}
    @PostMapping("/tracks") ResponseEntity<PreparationService.TrackView> createTrack(@Valid @RequestBody TrackRequest r){
        var t=service.createTrack(new PreparationService.NewTrack(r.name,r.description,r.category,r.targetDate,r.displayOrder));
        return ResponseEntity.created(URI.create("/api/v1/preparation/tracks/"+t.getId())).body(PreparationService.TrackView.from(t,java.util.List.of()));
    }
    @PutMapping("/tracks/order") java.util.List<PreparationService.TrackView> reorderTracks(@Valid @RequestBody TrackOrderRequest r){
        service.reorderTracks(r.trackIds); return service.overview().tracks();
    }
    @PostMapping("/tracks/{trackId}/resources") ResponseEntity<PreparationService.TrackResourceView> createResource(@PathVariable UUID trackId,@Valid @RequestBody TrackResourceRequest r){
        var resource=service.createResource(trackId,new PreparationService.NewTrackResource(r.title,r.url,r.notes));
        return ResponseEntity.created(URI.create("/api/v1/preparation/resources/"+resource.getId())).body(PreparationService.TrackResourceView.from(resource));
    }
    @DeleteMapping("/resources/{id}") ResponseEntity<Void> deleteResource(@PathVariable UUID id){service.deleteResource(id);return ResponseEntity.noContent().build();}
    @PostMapping("/tracks/{trackId}/milestones") ResponseEntity<PreparationService.MilestoneView> createMilestone(@PathVariable UUID trackId,@Valid @RequestBody MilestoneRequest r){
        var m=service.createMilestone(trackId,new PreparationService.NewMilestone(r.title,r.description,r.targetDate,r.displayOrder));
        return ResponseEntity.created(URI.create("/api/v1/preparation/milestones/"+m.getId())).body(PreparationService.MilestoneView.from(m,java.util.List.of()));
    }
    @PatchMapping("/milestones/{id}") PreparationService.MilestoneView updateMilestone(@PathVariable UUID id,@Valid @RequestBody UpdateMilestoneRequest r){
        return PreparationService.MilestoneView.from(service.updateMilestone(id,new PreparationService.UpdateMilestone(r.title,r.description,r.targetDate)),java.util.List.of());
    }
    @DeleteMapping("/milestones/{id}") ResponseEntity<Void> deleteMilestone(@PathVariable UUID id){service.deleteMilestone(id);return ResponseEntity.noContent().build();}
    @PostMapping("/milestones/{milestoneId}/items") ResponseEntity<PreparationService.ItemView> createItem(@PathVariable UUID milestoneId,@Valid @RequestBody ItemRequest r){
        var i=service.createItem(milestoneId,new PreparationService.NewItem(r.title,r.description,r.priority,r.estimatedMinutes,r.scheduledFor,r.dueDate,r.skillFocus,r.opportunityId,r.displayOrder));
        return ResponseEntity.created(URI.create("/api/v1/preparation/items/"+i.getId())).body(PreparationService.ItemView.from(i));
    }
    @PatchMapping("/items/{id}") PreparationService.ItemView updateItem(@PathVariable UUID id,@Valid @RequestBody UpdateItemRequest r){return PreparationService.ItemView.from(service.updateItem(id,new PreparationService.UpdateItem(r.status,r.priority,r.scheduledFor,r.dueDate,r.nextReviewOn)));}
    @PutMapping("/items/{id}") PreparationService.ItemView editItem(@PathVariable UUID id,@Valid @RequestBody EditItemRequest r){
        return PreparationService.ItemView.from(service.editItem(id,new PreparationService.EditItem(r.title,r.description,r.status,r.priority,r.estimatedMinutes,r.scheduledFor,r.dueDate,r.skillFocus,r.nextReviewOn,r.opportunityId)));
    }
    @GetMapping("/items/{id}/sessions") java.util.List<PreparationService.SessionView> itemSessions(@PathVariable UUID id){return service.sessionsForItem(id);}
    @PostMapping("/sprints") ResponseEntity<PreparationService.SprintView> startSprint(){
        var sprint=service.startSprint();
        return ResponseEntity.created(URI.create("/api/v1/preparation/sprints/"+sprint.id())).body(sprint);
    }
    @PostMapping("/sprints/current/items/{itemId}") PreparationService.SprintView addSprintItem(@PathVariable UUID itemId){return service.addItemToCurrentSprint(itemId);}
    @PostMapping("/sprints/{id}/close") PreparationService.SprintView closeSprint(@PathVariable UUID id){return service.closeSprint(id);}
    @PostMapping("/items/{id}/sessions") ResponseEntity<PreparationService.SessionView> logSession(@PathVariable UUID id,@Valid @RequestBody SessionRequest r){
        var s=service.logSession(id,new PreparationService.NewSession(r.sessionType,r.practicedAt,r.durationMinutes,r.resultSummary,r.mistakes,r.nextSteps,r.confidenceBefore,r.confidenceAfter,r.nextReviewOn));
        return ResponseEntity.created(URI.create("/api/v1/preparation/sessions/"+s.getId())).body(PreparationService.SessionView.from(s));
    }
    @PutMapping("/today") PreparationService.CommitmentView setToday(@Valid @RequestBody SetTodayRequest r){return PreparationService.CommitmentView.from(service.setToday(new PreparationService.SetCommitment(r.prepItemId,r.plannedMinutes,r.intention)));}
    @PatchMapping("/today") PreparationService.CommitmentView updateToday(@Valid @RequestBody UpdateTodayRequest r){return PreparationService.CommitmentView.from(service.updateToday(new PreparationService.UpdateCommitment(r.status,r.reflection)));}

    public record TrackRequest(@NotBlank String name,String description,PreparationCategory category,LocalDate targetDate,@Min(0) int displayOrder){}
    public record TrackOrderRequest(@NotEmpty java.util.List<@NotNull UUID> trackIds){}
    public record TrackResourceRequest(@NotBlank @Size(max=180) String title,@NotBlank @Size(max=2000) String url,String notes){}
    public record MilestoneRequest(@NotBlank String title,String description,LocalDate targetDate,@Min(0) int displayOrder){}
    public record UpdateMilestoneRequest(@NotBlank String title,String description,LocalDate targetDate){}
    public record ItemRequest(@NotBlank String title,String description,@Min(1) @Max(5) int priority,@Positive int estimatedMinutes,LocalDate scheduledFor,LocalDate dueDate,String skillFocus,UUID opportunityId,@Min(0) int displayOrder){}
    public record UpdateItemRequest(PrepItemStatus status,@Min(1) @Max(5) Integer priority,LocalDate scheduledFor,LocalDate dueDate,LocalDate nextReviewOn){}
    public record EditItemRequest(@NotBlank String title,String description,@NotNull PrepItemStatus status,@Min(1) @Max(5) int priority,
            @Positive int estimatedMinutes,LocalDate scheduledFor,LocalDate dueDate,String skillFocus,LocalDate nextReviewOn,UUID opportunityId){}
    public record SessionRequest(PracticeSessionType sessionType,Instant practicedAt,@Positive int durationMinutes,String resultSummary,String mistakes,String nextSteps,@Min(1) @Max(5) Integer confidenceBefore,@Min(1) @Max(5) Integer confidenceAfter,LocalDate nextReviewOn){}
    public record SetTodayRequest(@NotNull UUID prepItemId,@Positive int plannedMinutes,String intention){}
    public record UpdateTodayRequest(@NotNull CommitmentStatus status,String reflection){}
}
