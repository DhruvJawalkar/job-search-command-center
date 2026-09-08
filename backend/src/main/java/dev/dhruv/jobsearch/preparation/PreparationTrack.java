package dev.dhruv.jobsearch.preparation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import jakarta.persistence.*;

@Entity @Table(name = "preparation_track")
public class PreparationTrack {
    @Id private UUID id;
    @Column(nullable=false, length=180) private String name;
    @Column(columnDefinition="text") private String description;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=48) private PreparationCategory category;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=40) private PreparationTrackStatus status;
    private LocalDate targetDate;
    @Column(nullable=false) private int displayOrder;
    @Column(nullable=false) private Instant createdAt;
    @Column(nullable=false) private Instant updatedAt;
    @Version private long version;
    protected PreparationTrack() {}
    public PreparationTrack(String name, String description, PreparationCategory category, LocalDate targetDate, int displayOrder) {
        this.id=UUID.randomUUID(); this.name=required(name,"Track name"); this.description=optional(description);
        this.category=category==null?PreparationCategory.OTHER:category; this.status=PreparationTrackStatus.ACTIVE;
        this.targetDate=targetDate; this.displayOrder=Math.max(displayOrder,0);
    }
    @PrePersist void prePersist(){createdAt=Instant.now();updatedAt=createdAt;}
    @PreUpdate void preUpdate(){updatedAt=Instant.now();}
    public void reorder(int displayOrder){this.displayOrder=Math.max(displayOrder,0);}
    private static String required(String v,String label){if(v==null||v.isBlank())throw new IllegalArgumentException(label+" is required.");return v.trim();}
    private static String optional(String v){return v==null||v.isBlank()?null:v.trim();}
    public UUID getId(){return id;} public String getName(){return name;} public String getDescription(){return description;}
    public PreparationCategory getCategory(){return category;} public PreparationTrackStatus getStatus(){return status;}
    public LocalDate getTargetDate(){return targetDate;} public int getDisplayOrder(){return displayOrder;}
    public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
}
