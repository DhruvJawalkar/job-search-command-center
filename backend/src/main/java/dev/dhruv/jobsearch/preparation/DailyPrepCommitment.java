package dev.dhruv.jobsearch.preparation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import jakarta.persistence.*;

@Entity @Table(name="daily_prep_commitment")
public class DailyPrepCommitment {
    @Id private UUID id;
    @Column(nullable=false,unique=true) private LocalDate commitmentDate;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="prep_item_id") private PreparationItem prepItem;
    @Column(nullable=false) private int plannedMinutes;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=40) private CommitmentStatus status;
    @Column(columnDefinition="text") private String intention;
    @Column(columnDefinition="text") private String reflection;
    private Instant completedAt;
    @Column(nullable=false) private Instant createdAt;
    @Column(nullable=false) private Instant updatedAt;
    @Version private long version;
    protected DailyPrepCommitment() {}
    public DailyPrepCommitment(LocalDate date,PreparationItem item,int minutes,String intention){this.id=UUID.randomUUID();this.commitmentDate=date;change(item,minutes,intention);}
    public void change(PreparationItem item,int minutes,String intention){if(minutes<=0)throw new IllegalArgumentException("Planned minutes must be positive.");this.prepItem=item;this.plannedMinutes=minutes;this.intention=optional(intention);this.status=CommitmentStatus.PLANNED;this.reflection=null;this.completedAt=null;}
    public void updateStatus(CommitmentStatus status,String reflection){this.status=status;this.reflection=optional(reflection);this.completedAt=status==CommitmentStatus.COMPLETED?Instant.now():null;}
    @PrePersist void prePersist(){createdAt=Instant.now();updatedAt=createdAt;} @PreUpdate void preUpdate(){updatedAt=Instant.now();}
    private static String optional(String v){return v==null||v.isBlank()?null:v.trim();}
    public UUID getId(){return id;} public LocalDate getCommitmentDate(){return commitmentDate;} public PreparationItem getPrepItem(){return prepItem;}
    public int getPlannedMinutes(){return plannedMinutes;} public CommitmentStatus getStatus(){return status;} public String getIntention(){return intention;}
    public String getReflection(){return reflection;} public Instant getCompletedAt(){return completedAt;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
}
