package dev.dhruv.jobsearch.preparation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import jakarta.persistence.*;

@Entity @Table(name="preparation_milestone")
public class PreparationMilestone {
    @Id private UUID id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="track_id") private PreparationTrack track;
    @Column(nullable=false, length=220) private String title;
    @Column(columnDefinition="text") private String description;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=40) private MilestoneStatus status;
    private LocalDate targetDate;
    @Column(nullable=false) private int displayOrder;
    @Column(nullable=false) private Instant createdAt;
    @Column(nullable=false) private Instant updatedAt;
    @Version private long version;
    protected PreparationMilestone() {}
    public PreparationMilestone(PreparationTrack track,String title,String description,LocalDate targetDate,int displayOrder){
        this.id=UUID.randomUUID();this.track=track;this.title=required(title);this.description=optional(description);
        this.status=MilestoneStatus.PLANNED;this.targetDate=targetDate;this.displayOrder=Math.max(displayOrder,0);
    }
    public void update(String title,String description,LocalDate targetDate){
        this.title=required(title);this.description=optional(description);this.targetDate=targetDate;
    }
    @PrePersist void prePersist(){createdAt=Instant.now();updatedAt=createdAt;} @PreUpdate void preUpdate(){updatedAt=Instant.now();}
    private static String required(String v){if(v==null||v.isBlank())throw new IllegalArgumentException("Milestone title is required.");return v.trim();}
    private static String optional(String v){return v==null||v.isBlank()?null:v.trim();}
    public UUID getId(){return id;} public PreparationTrack getTrack(){return track;} public String getTitle(){return title;}
    public String getDescription(){return description;} public MilestoneStatus getStatus(){return status;}
    public LocalDate getTargetDate(){return targetDate;} public int getDisplayOrder(){return displayOrder;}
    public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
}
