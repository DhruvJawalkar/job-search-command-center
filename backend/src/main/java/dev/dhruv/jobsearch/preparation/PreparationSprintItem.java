package dev.dhruv.jobsearch.preparation;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

@Entity @Table(name="preparation_sprint_item", uniqueConstraints=@UniqueConstraint(columnNames={"sprint_id","prep_item_id"}))
public class PreparationSprintItem {
    @Id private UUID id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="sprint_id") private PreparationSprint sprint;
    @ManyToOne(fetch=FetchType.EAGER,optional=false) @JoinColumn(name="prep_item_id") private PreparationItem item;
    @Enumerated(EnumType.STRING) @Column(length=40) private PrepItemStatus finalStatus;
    @Column(nullable=false) private Instant addedAt;

    protected PreparationSprintItem() {}
    public PreparationSprintItem(PreparationSprint sprint,PreparationItem item){
        this.id=UUID.randomUUID();this.sprint=sprint;this.item=item;this.addedAt=Instant.now();
    }
    public void finish(){this.finalStatus=item.getStatus();}
    public UUID getId(){return id;} public PreparationSprint getSprint(){return sprint;} public PreparationItem getItem(){return item;}
    public PrepItemStatus getFinalStatus(){return finalStatus;} public Instant getAddedAt(){return addedAt;}
}
