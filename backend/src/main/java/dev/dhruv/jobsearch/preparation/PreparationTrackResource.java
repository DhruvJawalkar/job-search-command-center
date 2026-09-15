package dev.dhruv.jobsearch.preparation;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

@Entity @Table(name="preparation_track_resource")
public class PreparationTrackResource {
    @Id private UUID id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="track_id") private PreparationTrack track;
    @Column(nullable=false,length=180) private String title;
    @Column(nullable=false,length=2000) private String url;
    @Column(columnDefinition="text") private String notes;
    @Column(nullable=false) private Instant createdAt;

    protected PreparationTrackResource() {}
    public PreparationTrackResource(PreparationTrack track,String title,String url,String notes){
        this.id=UUID.randomUUID();this.track=track;this.title=required(title,"Resource title");this.url=required(url,"Resource URL");this.notes=optional(notes);
    }
    @PrePersist void prePersist(){createdAt=Instant.now();}
    private static String required(String value,String label){if(value==null||value.isBlank())throw new IllegalArgumentException(label+" is required.");return value.trim();}
    private static String optional(String value){return value==null||value.isBlank()?null:value.trim();}
    public UUID getId(){return id;} public PreparationTrack getTrack(){return track;} public String getTitle(){return title;}
    public String getUrl(){return url;} public String getNotes(){return notes;} public Instant getCreatedAt(){return createdAt;}
}
