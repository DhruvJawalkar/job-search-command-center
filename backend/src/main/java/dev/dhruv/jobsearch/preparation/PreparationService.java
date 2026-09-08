package dev.dhruv.jobsearch.preparation;

import java.time.Instant;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import dev.dhruv.jobsearch.opportunity.JobOpportunity;
import dev.dhruv.jobsearch.opportunity.JobOpportunityRepository;
import dev.dhruv.jobsearch.shared.NotFoundException;

@Service
public class PreparationService {
    private final PreparationTrackRepository tracks;
    private final PreparationMilestoneRepository milestones;
    private final PreparationItemRepository items;
    private final PracticeSessionRepository sessions;
    private final DailyPrepCommitmentRepository commitments;
    private final JobOpportunityRepository opportunities;
    private final PreparationSprintRepository sprints;
    private final PreparationSprintItemRepository sprintItems;
    private final PreparationTrackResourceRepository resources;

    public PreparationService(PreparationTrackRepository tracks, PreparationMilestoneRepository milestones,
            PreparationItemRepository items, PracticeSessionRepository sessions,
            DailyPrepCommitmentRepository commitments, JobOpportunityRepository opportunities,
            PreparationSprintRepository sprints, PreparationSprintItemRepository sprintItems,
            PreparationTrackResourceRepository resources) {
        this.tracks=tracks; this.milestones=milestones; this.items=items; this.sessions=sessions;
        this.commitments=commitments; this.opportunities=opportunities; this.sprints=sprints; this.sprintItems=sprintItems; this.resources=resources;
    }

    @Transactional(readOnly=true)
    public Overview overview() {
        var allMilestones=milestones.findAllByOrderByDisplayOrderAscCreatedAtAsc();
        var allItems=items.findAllByOrderByDisplayOrderAscPriorityAscCreatedAtAsc();
        Map<UUID,List<PreparationItem>> itemsByMilestone=allItems.stream().collect(Collectors.groupingBy(i->i.getMilestone().getId()));
        Map<UUID,List<PreparationMilestone>> milestonesByTrack=allMilestones.stream().collect(Collectors.groupingBy(m->m.getTrack().getId()));
        Map<UUID,List<PreparationTrackResource>> resourcesByTrack=resources.findAllByOrderByCreatedAtAsc().stream().collect(Collectors.groupingBy(r->r.getTrack().getId()));
        var trackViews=tracks.findAllByOrderByDisplayOrderAscCreatedAtAsc().stream().map(track->TrackView.from(track,
            milestonesByTrack.getOrDefault(track.getId(),List.of()).stream().map(m->MilestoneView.from(m,
                itemsByMilestone.getOrDefault(m.getId(),List.of()).stream().map(ItemView::from).toList())).toList(),
            resourcesByTrack.getOrDefault(track.getId(),List.of()).stream().map(TrackResourceView::from).toList())).toList();
        var today=commitments.findByCommitmentDate(LocalDate.now()).map(CommitmentView::from).orElse(null);
        Instant since=Instant.now().minus(7,ChronoUnit.DAYS);
        var stats=new PreparationStats(items.countByStatusIn(List.of(PrepItemStatus.READY,PrepItemStatus.IN_PROGRESS,PrepItemStatus.IN_REVIEW)),
                sessions.countByPracticedAtGreaterThanEqual(since), sessions.totalMinutesSince(since));
        var activeSprint=sprints.findFirstByStatusOrderByStartDateDesc(SprintStatus.ACTIVE).map(this::sprintView).orElse(null);
        var recentSprints=sprints.findTop4ByStatusOrderByStartDateDesc(SprintStatus.CLOSED).stream().map(this::sprintView).toList();
        return new Overview(LocalDate.now(),today,stats,trackViews,sessions.findTop8ByOrderByPracticedAtDesc().stream().map(SessionView::from).toList(),activeSprint,recentSprints);
    }

    @Transactional public PreparationTrack createTrack(NewTrack c){return tracks.save(new PreparationTrack(c.name,c.description,c.category,c.targetDate,tracks.findMaxDisplayOrder()+1));}
    @Transactional public void reorderTracks(List<UUID> orderedTrackIds){
        var existing=tracks.findAllByOrderByDisplayOrderAscCreatedAtAsc();
        if(orderedTrackIds==null||orderedTrackIds.size()!=existing.size()||new HashSet<>(orderedTrackIds).size()!=orderedTrackIds.size()
                || !new HashSet<>(orderedTrackIds).equals(existing.stream().map(PreparationTrack::getId).collect(Collectors.toSet())))
            throw new IllegalArgumentException("Track order must include every preparation track exactly once.");
        Map<UUID,PreparationTrack> byId=existing.stream().collect(Collectors.toMap(PreparationTrack::getId,track->track));
        for(int index=0;index<orderedTrackIds.size();index++) byId.get(orderedTrackIds.get(index)).reorder(index);
    }
    @Transactional public PreparationTrackResource createResource(UUID trackId,NewTrackResource c){
        var track=tracks.findById(trackId).orElseThrow(()->new NotFoundException("Preparation track "+trackId+" was not found."));
        return resources.save(new PreparationTrackResource(track,c.title,c.url,c.notes));
    }
    @Transactional public void deleteResource(UUID id){
        var resource=resources.findById(id).orElseThrow(()->new NotFoundException("Preparation resource "+id+" was not found.")); resources.delete(resource);
    }
    @Transactional public PreparationMilestone createMilestone(UUID trackId,NewMilestone c){
        var track=tracks.findById(trackId).orElseThrow(()->new NotFoundException("Preparation track "+trackId+" was not found."));
        return milestones.save(new PreparationMilestone(track,c.title,c.description,c.targetDate,c.displayOrder));
    }
    @Transactional public PreparationMilestone updateMilestone(UUID id,UpdateMilestone c){
        var milestone=milestone(id); milestone.update(c.title,c.description,c.targetDate); return milestone;
    }
    @Transactional public void deleteMilestone(UUID id){milestones.delete(milestone(id));}
    @Transactional public PreparationItem createItem(UUID milestoneId,NewItem c){
        var milestone=milestones.findById(milestoneId).orElseThrow(()->new NotFoundException("Preparation milestone "+milestoneId+" was not found."));
        JobOpportunity opportunity=c.opportunityId==null?null:opportunities.findById(c.opportunityId).orElseThrow(()->new NotFoundException("Opportunity "+c.opportunityId+" was not found."));
        return items.save(new PreparationItem(milestone,opportunity,c.title,c.description,c.priority,c.estimatedMinutes,c.scheduledFor,c.dueDate,c.skillFocus,c.displayOrder));
    }
    @Transactional public PreparationItem updateItem(UUID id,UpdateItem c){
        var item=item(id); item.update(c.status,c.priority,c.scheduledFor,c.dueDate,c.nextReviewOn); return item;
    }
    @Transactional public PreparationItem editItem(UUID id,EditItem c){
        var item=item(id);
        JobOpportunity opportunity=c.opportunityId==null?null:opportunities.findById(c.opportunityId)
                .orElseThrow(()->new NotFoundException("Opportunity "+c.opportunityId+" was not found."));
        item.editDetails(opportunity,c.title,c.description,c.status,c.priority,c.estimatedMinutes,c.scheduledFor,c.dueDate,c.skillFocus,c.nextReviewOn);
        return item;
    }
    @Transactional(readOnly=true) public List<SessionView> sessionsForItem(UUID itemId){
        item(itemId); return sessions.findByPrepItemIdOrderByPracticedAtDesc(itemId).stream().map(SessionView::from).toList();
    }
    @Transactional public SprintView startSprint(){
        if(sprints.findFirstByStatusOrderByStartDateDesc(SprintStatus.ACTIVE).isPresent()) throw new IllegalStateException("Close the current sprint before starting another one.");
        LocalDate today=LocalDate.now();
        LocalDate start=today.getDayOfWeek()==DayOfWeek.SUNDAY?today.plusDays(1):today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return sprintView(sprints.save(new PreparationSprint(start)));
    }
    @Transactional public SprintView addItemToCurrentSprint(UUID itemId){
        var sprint=sprints.findFirstByStatusOrderByStartDateDesc(SprintStatus.ACTIVE)
                .orElseThrow(()->new IllegalStateException("Start a sprint before adding tasks to the board."));
        var item=item(itemId);
        if(!sprintItems.existsBySprintIdAndItemId(sprint.getId(),itemId)){
            item.addToSprint(LocalDate.now());
            sprintItems.save(new PreparationSprintItem(sprint,item));
        }
        return sprintView(sprint);
    }
    @Transactional public SprintView closeSprint(UUID sprintId){
        var sprint=sprints.findById(sprintId).orElseThrow(()->new NotFoundException("Preparation sprint "+sprintId+" was not found."));
        if(sprint.getStatus()!=SprintStatus.ACTIVE) throw new IllegalStateException("This sprint is already closed.");
        if(LocalDate.now().isBefore(sprint.getEndDate())) throw new IllegalStateException("The sprint can be closed on or after "+sprint.getEndDate()+".");
        var entries=sprintItems.findBySprintIdOrderByAddedAtAsc(sprint.getId());
        entries.forEach(entry->{entry.finish();entry.getItem().returnOpenWorkToBacklog();});
        sprint.close();
        return sprintView(sprint,entries);
    }
    @Transactional public PracticeSession logSession(UUID itemId,NewSession c){
        var item=item(itemId); var session=sessions.save(new PracticeSession(item,c.sessionType,c.practicedAt,c.durationMinutes,
                c.resultSummary,c.mistakes,c.nextSteps,c.confidenceBefore,c.confidenceAfter,c.nextReviewOn));
        item.recordPractice(c.nextReviewOn);
        commitments.findByCommitmentDate(LocalDate.now()).filter(commitment->commitment.getPrepItem()!=null)
            .filter(commitment->commitment.getPrepItem().getId().equals(itemId))
            .ifPresent(commitment->commitment.updateStatus(CommitmentStatus.COMPLETED,c.resultSummary));
        return session;
    }
    @Transactional public DailyPrepCommitment setToday(SetCommitment c){
        var item=item(c.prepItemId); var today=LocalDate.now();
        var commitment=commitments.findByCommitmentDate(today).orElseGet(()->new DailyPrepCommitment(today,item,c.plannedMinutes,c.intention));
        if(commitment.getCreatedAt()!=null) commitment.change(item,c.plannedMinutes,c.intention);
        return commitments.save(commitment);
    }
    @Transactional public DailyPrepCommitment updateToday(UpdateCommitment c){
        var commitment=commitments.findByCommitmentDate(LocalDate.now()).orElseThrow(()->new NotFoundException("No preparation commitment is set for today."));
        commitment.updateStatus(c.status,c.reflection); return commitment;
    }
    private PreparationItem item(UUID id){return items.findById(id).orElseThrow(()->new NotFoundException("Preparation item "+id+" was not found."));}
    private PreparationMilestone milestone(UUID id){return milestones.findById(id).orElseThrow(()->new NotFoundException("Preparation story "+id+" was not found."));}
    private SprintView sprintView(PreparationSprint sprint){return sprintView(sprint,sprintItems.findBySprintIdOrderByAddedAtAsc(sprint.getId()));}
    private SprintView sprintView(PreparationSprint sprint,List<PreparationSprintItem> entries){
        List<SprintTaskView> tasks=entries.stream().map(entry->new SprintTaskView(entry.getItem().getId(),entry.getItem().getTitle(),
                sprint.getStatus()==SprintStatus.CLOSED&&entry.getFinalStatus()!=null?entry.getFinalStatus():entry.getItem().getStatus())).toList();
        int done=(int)tasks.stream().filter(task->task.status()==PrepItemStatus.COMPLETED).count();
        int inReview=(int)tasks.stream().filter(task->task.status()==PrepItemStatus.IN_REVIEW).count();
        int inProgress=(int)tasks.stream().filter(task->task.status()==PrepItemStatus.IN_PROGRESS).count();
        int open=tasks.size()-done-inReview-inProgress;
        return new SprintView(sprint.getId(),sprint.getStatus(),sprint.getStartDate(),sprint.getEndDate(),sprint.getClosedAt(),
                sprint.getStatus()==SprintStatus.ACTIVE&&!LocalDate.now().isBefore(sprint.getEndDate()),tasks,new SprintSummary(tasks.size(),done,inReview,inProgress,open));
    }

    public record NewTrack(String name,String description,PreparationCategory category,LocalDate targetDate,int displayOrder){}
    public record NewTrackResource(String title,String url,String notes){}
    public record NewMilestone(String title,String description,LocalDate targetDate,int displayOrder){}
    public record UpdateMilestone(String title,String description,LocalDate targetDate){}
    public record NewItem(String title,String description,int priority,int estimatedMinutes,LocalDate scheduledFor,LocalDate dueDate,String skillFocus,UUID opportunityId,int displayOrder){}
    public record UpdateItem(PrepItemStatus status,Integer priority,LocalDate scheduledFor,LocalDate dueDate,LocalDate nextReviewOn){}
    public record EditItem(String title,String description,PrepItemStatus status,int priority,int estimatedMinutes,LocalDate scheduledFor,LocalDate dueDate,String skillFocus,LocalDate nextReviewOn,UUID opportunityId){}
    public record NewSession(PracticeSessionType sessionType,Instant practicedAt,int durationMinutes,String resultSummary,String mistakes,String nextSteps,Integer confidenceBefore,Integer confidenceAfter,LocalDate nextReviewOn){}
    public record SetCommitment(UUID prepItemId,int plannedMinutes,String intention){}
    public record UpdateCommitment(CommitmentStatus status,String reflection){}

    public record Overview(LocalDate date,CommitmentView today,PreparationStats stats,List<TrackView> tracks,List<SessionView> recentSessions,SprintView activeSprint,List<SprintView> recentSprints){}
    public record PreparationStats(long readyItems,long sessionsThisWeek,long minutesThisWeek){}
    public record SprintView(UUID id,SprintStatus status,LocalDate startDate,LocalDate endDate,Instant closedAt,boolean canClose,List<SprintTaskView> tasks,SprintSummary summary){}
    public record SprintTaskView(UUID itemId,String title,PrepItemStatus status){}
    public record SprintSummary(int total,int done,int inReview,int inProgress,int open){}
    public record TrackView(UUID id,String name,String description,PreparationCategory category,PreparationTrackStatus status,LocalDate targetDate,int displayOrder,List<MilestoneView> milestones,List<TrackResourceView> resources){
        static TrackView from(PreparationTrack t,List<MilestoneView> m){return from(t,m,List.of());}
        static TrackView from(PreparationTrack t,List<MilestoneView> m,List<TrackResourceView> r){return new TrackView(t.getId(),t.getName(),t.getDescription(),t.getCategory(),t.getStatus(),t.getTargetDate(),t.getDisplayOrder(),m,r);}}
    public record TrackResourceView(UUID id,String title,String url,String notes,Instant createdAt){
        static TrackResourceView from(PreparationTrackResource r){return new TrackResourceView(r.getId(),r.getTitle(),r.getUrl(),r.getNotes(),r.getCreatedAt());}}
    public record MilestoneView(UUID id,String title,String description,MilestoneStatus status,LocalDate targetDate,List<ItemView> items){
        static MilestoneView from(PreparationMilestone m,List<ItemView> i){return new MilestoneView(m.getId(),m.getTitle(),m.getDescription(),m.getStatus(),m.getTargetDate(),i);}}
    public record ItemView(UUID id,String title,String description,PrepItemStatus status,int priority,int estimatedMinutes,LocalDate scheduledFor,LocalDate dueDate,String skillFocus,LocalDate nextReviewOn,UUID opportunityId,String opportunityLabel,Instant completedAt){
        static ItemView from(PreparationItem i){var o=i.getOpportunity();return new ItemView(i.getId(),i.getTitle(),i.getDescription(),i.getStatus(),i.getPriority(),i.getEstimatedMinutes(),i.getScheduledFor(),i.getDueDate(),i.getSkillFocus(),i.getNextReviewOn(),o==null?null:o.getId(),o==null?null:o.getCompanyName()+" · "+o.getRoleTitle(),i.getCompletedAt());}}
    public record SessionView(UUID id,UUID prepItemId,String itemTitle,PracticeSessionType sessionType,Instant practicedAt,int durationMinutes,String resultSummary,String mistakes,String nextSteps,Integer confidenceBefore,Integer confidenceAfter,LocalDate nextReviewOn){
        static SessionView from(PracticeSession s){return new SessionView(s.getId(),s.getPrepItem().getId(),s.getPrepItem().getTitle(),s.getSessionType(),s.getPracticedAt(),s.getDurationMinutes(),s.getResultSummary(),s.getMistakes(),s.getNextSteps(),s.getConfidenceBefore(),s.getConfidenceAfter(),s.getNextReviewOn());}}
    public record CommitmentView(UUID id,LocalDate commitmentDate,UUID prepItemId,String itemTitle,int plannedMinutes,CommitmentStatus status,String intention,String reflection,Instant completedAt){
        static CommitmentView from(DailyPrepCommitment c){var i=c.getPrepItem();return new CommitmentView(c.getId(),c.getCommitmentDate(),i==null?null:i.getId(),i==null?"Preparation item removed":i.getTitle(),c.getPlannedMinutes(),c.getStatus(),c.getIntention(),c.getReflection(),c.getCompletedAt());}}
}
