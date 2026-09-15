package dev.dhruv.jobsearch.preparation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import jakarta.persistence.*;

@Entity @Table(name="preparation_sprint")
public class PreparationSprint {
    @Id private UUID id;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=32) private SprintStatus status;
    @Column(nullable=false) private LocalDate startDate;
    @Column(nullable=false) private LocalDate endDate;
    private Instant closedAt;
    @Column(nullable=false) private Instant createdAt;
    @Column(nullable=false) private Instant updatedAt;
    @Version private long version;

    protected PreparationSprint() {}
    public PreparationSprint(LocalDate startDate) {
        if(startDate==null) throw new IllegalArgumentException("Sprint start date is required.");
        this.id=UUID.randomUUID(); this.status=SprintStatus.ACTIVE; this.startDate=startDate; this.endDate=startDate.plusDays(13);
    }
    public void close() {
        if(status!=SprintStatus.ACTIVE) throw new IllegalStateException("Only an active sprint can be closed.");
        status=SprintStatus.CLOSED; closedAt=Instant.now();
    }
    @PrePersist void prePersist(){createdAt=Instant.now();updatedAt=createdAt;}
    @PreUpdate void preUpdate(){updatedAt=Instant.now();}
    public UUID getId(){return id;} public SprintStatus getStatus(){return status;} public LocalDate getStartDate(){return startDate;}
    public LocalDate getEndDate(){return endDate;} public Instant getClosedAt(){return closedAt;} public Instant getCreatedAt(){return createdAt;}
    public Instant getUpdatedAt(){return updatedAt;}
}
