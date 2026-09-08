package dev.dhruv.jobsearch.contact;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import dev.dhruv.jobsearch.opportunity.JobOpportunity;
import jakarta.persistence.*;

@Entity @Table(name="referral_candidate")
public class ReferralCandidate {
    @Id private UUID id;
    @ManyToOne(fetch=FetchType.EAGER,optional=false) @JoinColumn(name="opportunity_id") private JobOpportunity opportunity;
    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="contact_id") private NetworkContact contact;
    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="outreach_id") private OutreachActivity outreach;
    @Column(nullable=false,length=200) private String fullName;
    @Column(length=240) private String companyName;
    @Column(length=240) private String roleTitle;
    @Column(length=1500) private String profileUrl;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=40) private ConnectionDegree connectionDegree;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=48) private ReferralChannel discoveryChannel;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=40) private RelationshipStrength relationshipStrength;
    @Column(length=200) private String mutualConnectionName;
    @Column(length=1500) private String mutualConnectionUrl;
    @Column(nullable=false) private boolean currentCompanyMatch;
    @Column(nullable=false) private boolean formerCompanyMatch;
    @Column(nullable=false) private int roleRelevance;
    @Column(nullable=false) private int responsiveness;
    private LocalDate lastInteractionOn;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=40) private ReferralCandidateStatus status;
    @Column(nullable=false) private int pathScore;
    @Column(nullable=false,length=1000) private String scoreExplanation;
    @Column(columnDefinition="text") private String notes;
    @Column(nullable=false) private Instant createdAt;
    @Column(nullable=false) private Instant updatedAt;
    @Version private long version;
    protected ReferralCandidate() {}
    public ReferralCandidate(JobOpportunity opportunity,String fullName,String companyName,String roleTitle,String profileUrl,
            ConnectionDegree connectionDegree,ReferralChannel channel,RelationshipStrength relationshipStrength,
            String mutualConnectionName,String mutualConnectionUrl,boolean currentCompanyMatch,boolean formerCompanyMatch,
            int roleRelevance,int responsiveness,LocalDate lastInteractionOn,int pathScore,String explanation,String notes){
        this.id=UUID.randomUUID();this.opportunity=opportunity;this.fullName=required(fullName);this.companyName=optional(companyName);
        this.roleTitle=optional(roleTitle);this.profileUrl=optional(profileUrl);this.connectionDegree=connectionDegree==null?ConnectionDegree.OTHER:connectionDegree;
        this.discoveryChannel=channel==null?ReferralChannel.LINKEDIN:channel;this.relationshipStrength=relationshipStrength==null?RelationshipStrength.COLD:relationshipStrength;
        this.mutualConnectionName=optional(mutualConnectionName);this.mutualConnectionUrl=optional(mutualConnectionUrl);
        this.currentCompanyMatch=currentCompanyMatch;this.formerCompanyMatch=formerCompanyMatch;this.roleRelevance=range(roleRelevance,"Role relevance");
        this.responsiveness=range(responsiveness,"Responsiveness");this.lastInteractionOn=lastInteractionOn;this.status=ReferralCandidateStatus.SHORTLISTED;
        this.pathScore=pathScore;this.scoreExplanation=explanation;this.notes=optional(notes);
    }
    public void markOutreach(NetworkContact contact,OutreachActivity outreach){this.contact=contact;this.outreach=outreach;this.status=ReferralCandidateStatus.OUTREACH_CREATED;}
    public void updateProfile(String fullName,String companyName,String roleTitle,String profileUrl,
            ConnectionDegree connectionDegree,ReferralChannel channel,RelationshipStrength relationshipStrength,
            String mutualConnectionName,String mutualConnectionUrl,boolean currentCompanyMatch,boolean formerCompanyMatch,
            int roleRelevance,int responsiveness,LocalDate lastInteractionOn,int pathScore,String explanation,String notes){
        this.fullName=required(fullName);this.companyName=optional(companyName);this.roleTitle=optional(roleTitle);this.profileUrl=optional(profileUrl);
        this.connectionDegree=connectionDegree==null?ConnectionDegree.OTHER:connectionDegree;this.discoveryChannel=channel==null?ReferralChannel.LINKEDIN:channel;
        this.relationshipStrength=relationshipStrength==null?RelationshipStrength.COLD:relationshipStrength;this.mutualConnectionName=optional(mutualConnectionName);
        this.mutualConnectionUrl=optional(mutualConnectionUrl);this.currentCompanyMatch=currentCompanyMatch;this.formerCompanyMatch=formerCompanyMatch;
        this.roleRelevance=range(roleRelevance,"Role relevance");this.responsiveness=range(responsiveness,"Responsiveness");
        this.lastInteractionOn=lastInteractionOn;this.pathScore=pathScore;this.scoreExplanation=explanation;this.notes=optional(notes);
    }
    public void dismiss(){if(status!=ReferralCandidateStatus.OUTREACH_CREATED)status=ReferralCandidateStatus.DISMISSED;}
    @PrePersist void prePersist(){createdAt=Instant.now();updatedAt=createdAt;} @PreUpdate void preUpdate(){updatedAt=Instant.now();}
    private static String required(String value){if(value==null||value.isBlank())throw new IllegalArgumentException("Candidate name is required.");return value.trim();}
    private static String optional(String value){return value==null||value.isBlank()?null:value.trim();}
    private static int range(int value,String label){if(value<1||value>5)throw new IllegalArgumentException(label+" must be between 1 and 5.");return value;}
    public UUID getId(){return id;} public JobOpportunity getOpportunity(){return opportunity;} public NetworkContact getContact(){return contact;}
    public OutreachActivity getOutreach(){return outreach;} public String getFullName(){return fullName;} public String getCompanyName(){return companyName;}
    public String getRoleTitle(){return roleTitle;} public String getProfileUrl(){return profileUrl;} public ConnectionDegree getConnectionDegree(){return connectionDegree;}
    public ReferralChannel getDiscoveryChannel(){return discoveryChannel;} public RelationshipStrength getRelationshipStrength(){return relationshipStrength;}
    public String getMutualConnectionName(){return mutualConnectionName;} public String getMutualConnectionUrl(){return mutualConnectionUrl;}
    public boolean isCurrentCompanyMatch(){return currentCompanyMatch;} public boolean isFormerCompanyMatch(){return formerCompanyMatch;}
    public int getRoleRelevance(){return roleRelevance;} public int getResponsiveness(){return responsiveness;} public LocalDate getLastInteractionOn(){return lastInteractionOn;}
    public ReferralCandidateStatus getStatus(){return status;} public int getPathScore(){return pathScore;} public String getScoreExplanation(){return scoreExplanation;}
    public String getNotes(){return notes;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
}
