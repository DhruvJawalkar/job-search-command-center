package dev.dhruv.jobsearch.preparation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import dev.dhruv.jobsearch.opportunity.JobOpportunity;
import jakarta.persistence.*;

@Entity @Table(name="preparation_item")
public class PreparationItem {
    @Id private UUID id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="milestone_id") private PreparationMilestone milestone;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="opportunity_id") private JobOpportunity opportunity;
    @Column(nullable=false,length=240) private String title;
    @Column(columnDefinition="text") private String description;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=40) private PrepItemStatus status;
    @Column(nullable=false) private int priority;
    @Column(nullable=false) private int estimatedMinutes;
    private LocalDate scheduledFor; private LocalDate dueDate;
    @Column(length=500) private String skillFocus;
    private LocalDate nextReviewOn;
    @Column(nullable=false) private int displayOrder;
    private Instant completedAt;
    @Column(nullable=false) private Instant createdAt;
    @Column(nullable=false) private Instant updatedAt;
    @Version private long version;
    protected PreparationItem() {}
    public PreparationItem(PreparationMilestone milestone,JobOpportunity opportunity,String title,String description,int priority,
            int estimatedMinutes,LocalDate scheduledFor,LocalDate dueDate,String skillFocus,int displayOrder){
        this.id=UUID.randomUUID();this.milestone=milestone;this.opportunity=opportunity;this.title=required(title);
        this.description=optional(description);this.status=PrepItemStatus.BACKLOG;this.priority=range(priority,1,5,"Priority");
        this.estimatedMinutes=positive(estimatedMinutes,"Estimated minutes");this.scheduledFor=scheduledFor;this.dueDate=dueDate;
        this.skillFocus=optional(skillFocus);this.displayOrder=Math.max(displayOrder,0);
    }
    public void update(PrepItemStatus status,Integer priority,LocalDate scheduledFor,LocalDate dueDate,LocalDate nextReviewOn){
        if(status!=null){
            if(status==PrepItemStatus.COMPLETED&&this.status!=PrepItemStatus.COMPLETED)this.completedAt=Instant.now();
            if(status!=PrepItemStatus.COMPLETED)this.completedAt=null;
            this.status=status;
        }
        if(priority!=null)this.priority=range(priority,1,5,"Priority");
        if(scheduledFor!=null)this.scheduledFor=scheduledFor;
        if(dueDate!=null)this.dueDate=dueDate;
        if(nextReviewOn!=null)this.nextReviewOn=nextReviewOn;
    }
    public void editDetails(JobOpportunity opportunity,String title,String description,PrepItemStatus status,int priority,
            int estimatedMinutes,LocalDate scheduledFor,LocalDate dueDate,String skillFocus,LocalDate nextReviewOn){
        this.opportunity=opportunity; this.title=required(title); this.description=optional(description);
        this.priority=range(priority,1,5,"Priority"); this.estimatedMinutes=positive(estimatedMinutes,"Estimated minutes");
        this.scheduledFor=scheduledFor; this.dueDate=dueDate; this.skillFocus=optional(skillFocus); this.nextReviewOn=nextReviewOn;
        update(status,null,null,null,null);
    }
    public void recordPractice(LocalDate nextReviewOn){if(status==PrepItemStatus.READY||status==PrepItemStatus.BACKLOG)status=PrepItemStatus.IN_PROGRESS;if(nextReviewOn!=null)this.nextReviewOn=nextReviewOn;}
    public void addToSprint(LocalDate addedOn){
        if(status==PrepItemStatus.BACKLOG||status==PrepItemStatus.COMPLETED||status==PrepItemStatus.SKIPPED){status=PrepItemStatus.READY;completedAt=null;}
        if(scheduledFor==null)scheduledFor=addedOn;
    }
    public void returnOpenWorkToBacklog(){if(status==PrepItemStatus.READY){status=PrepItemStatus.BACKLOG;scheduledFor=null;}}
    @PrePersist void prePersist(){createdAt=Instant.now();updatedAt=createdAt;} @PreUpdate void preUpdate(){updatedAt=Instant.now();}
    private static String required(String v){if(v==null||v.isBlank())throw new IllegalArgumentException("Preparation item title is required.");return v.trim();}
    private static String optional(String v){return v==null||v.isBlank()?null:v.trim();}
    private static int positive(int v,String l){if(v<=0)throw new IllegalArgumentException(l+" must be positive.");return v;}
    private static int range(int v,int min,int max,String l){if(v<min||v>max)throw new IllegalArgumentException(l+" must be between "+min+" and "+max+".");return v;}
    public UUID getId(){return id;} public PreparationMilestone getMilestone(){return milestone;} public JobOpportunity getOpportunity(){return opportunity;}
    public String getTitle(){return title;} public String getDescription(){return description;} public PrepItemStatus getStatus(){return status;}
    public int getPriority(){return priority;} public int getEstimatedMinutes(){return estimatedMinutes;} public LocalDate getScheduledFor(){return scheduledFor;}
    public LocalDate getDueDate(){return dueDate;} public String getSkillFocus(){return skillFocus;} public LocalDate getNextReviewOn(){return nextReviewOn;}
    public int getDisplayOrder(){return displayOrder;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
    public Instant getCompletedAt(){return completedAt;}
}
