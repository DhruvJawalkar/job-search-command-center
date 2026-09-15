package dev.dhruv.jobsearch.preparation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import jakarta.persistence.*;

@Entity @Table(name="practice_session")
public class PracticeSession {
    @Id private UUID id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="prep_item_id") private PreparationItem prepItem;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=48) private PracticeSessionType sessionType;
    @Column(nullable=false) private Instant practicedAt;
    @Column(nullable=false) private int durationMinutes;
    @Column(columnDefinition="text") private String resultSummary;
    @Column(columnDefinition="text") private String mistakes;
    @Column(columnDefinition="text") private String nextSteps;
    private Integer confidenceBefore; private Integer confidenceAfter; private LocalDate nextReviewOn;
    @Column(nullable=false) private Instant createdAt;
    protected PracticeSession() {}
    public PracticeSession(PreparationItem item,PracticeSessionType type,Instant practicedAt,int duration,String result,String mistakes,
            String nextSteps,Integer confidenceBefore,Integer confidenceAfter,LocalDate nextReviewOn){
        this.id=UUID.randomUUID();this.prepItem=item;this.sessionType=type==null?PracticeSessionType.DRILL:type;
        this.practicedAt=practicedAt==null?Instant.now():practicedAt;if(duration<=0)throw new IllegalArgumentException("Duration must be positive.");this.durationMinutes=duration;
        this.resultSummary=optional(result);this.mistakes=optional(mistakes);this.nextSteps=optional(nextSteps);
        this.confidenceBefore=confidence(confidenceBefore);this.confidenceAfter=confidence(confidenceAfter);this.nextReviewOn=nextReviewOn;
    }
    @PrePersist void prePersist(){createdAt=Instant.now();}
    private static String optional(String v){return v==null||v.isBlank()?null:v.trim();}
    private static Integer confidence(Integer v){if(v!=null&&(v<1||v>5))throw new IllegalArgumentException("Confidence must be between 1 and 5.");return v;}
    public UUID getId(){return id;} public PreparationItem getPrepItem(){return prepItem;} public PracticeSessionType getSessionType(){return sessionType;}
    public Instant getPracticedAt(){return practicedAt;} public int getDurationMinutes(){return durationMinutes;} public String getResultSummary(){return resultSummary;}
    public String getMistakes(){return mistakes;} public String getNextSteps(){return nextSteps;} public Integer getConfidenceBefore(){return confidenceBefore;}
    public Integer getConfidenceAfter(){return confidenceAfter;} public LocalDate getNextReviewOn(){return nextReviewOn;} public Instant getCreatedAt(){return createdAt;}
}
