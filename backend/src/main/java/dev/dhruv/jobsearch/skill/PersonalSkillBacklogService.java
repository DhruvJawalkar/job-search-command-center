package dev.dhruv.jobsearch.skill;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.dhruv.jobsearch.preparation.PreparationItem;
import dev.dhruv.jobsearch.shared.NotFoundException;
import jakarta.persistence.EntityManager;

@Service
public class PersonalSkillBacklogService {
    private static final String AI_BACKLOG_MILESTONE = "ai skill backlog";
    private static final String USER_SELECTED_RATIONALE =
            "User-selected from reviewed job openings and retained as a preparation priority.";

    private final PersonalSkillBacklogRepository backlog;
    private final PersonalSkillPreparationLinkRepository preparationLinks;
    private final SkillLearningResourceRepository resources;
    private final SkillProjectEvidenceRepository projectEvidence;
    private final CanonicalSkillRepository skills;
    private final SkillAliasRepository aliases;
    private final MarketSignalService marketSignals;
    private final EntityManager entityManager;

    public PersonalSkillBacklogService(PersonalSkillBacklogRepository backlog,
            PersonalSkillPreparationLinkRepository preparationLinks,
            SkillLearningResourceRepository resources,
            SkillProjectEvidenceRepository projectEvidence,
            CanonicalSkillRepository skills, SkillAliasRepository aliases,
            MarketSignalService marketSignals, EntityManager entityManager) {
        this.backlog = backlog; this.preparationLinks = preparationLinks; this.resources = resources;
        this.projectEvidence = projectEvidence; this.skills = skills; this.aliases = aliases;
        this.marketSignals = marketSignals; this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public BacklogOverview overview() {
        var demand = marketSignals.overview(new MarketSignalService.MarketSignalQuery(
                null, null, null, null, null, null));
        Map<UUID, MarketSignalService.SkillSignal> demandBySkill = new HashMap<>();
        demand.signals().forEach(signal -> demandBySkill.put(signal.skillId(), signal));
        List<BacklogItemView> items = backlog.findAllByOrderByPriorityAscCreatedAtAsc().stream()
                .map(item -> view(item, demandBySkill.get(item.getSkill().getId())))
                .sorted(Comparator.comparingInt(BacklogItemView::priority)
                        .thenComparing(BacklogItemView::currentOpeningCount, Comparator.reverseOrder())
                        .thenComparing(BacklogItemView::skillName))
                .toList();
        int active = (int) items.stream().filter(item -> item.status() == PersonalSkillStatus.ACTIVE).count();
        int linkedPrepItems = (int) items.stream().flatMap(item -> item.preparationLinks().stream())
                .map(PreparationLinkView::prepItemId).distinct().count();
        return new BacklogOverview(items.size(), active, linkedPrepItems, demand.currentSampleSize(),
                demand.query().from(), demand.query().to(), items);
    }

    @Transactional
    public BacklogItemView update(UUID skillId, UpdateBacklog command) {
        CanonicalSkill skill = skills.findById(skillId)
                .orElseThrow(() -> new NotFoundException("Canonical skill " + skillId + " was not found."));
        PersonalSkillBacklog item = backlog.findBySkillId(skillId).orElse(null);
        if (item == null) {
            item = backlog.save(new PersonalSkillBacklog(skill, command.currentLevel(), command.targetLevel(),
                    command.priority(), command.rationale(), command.status(), command.nextReviewOn()));
        } else {
            item.update(command.currentLevel(), command.targetLevel(), command.priority(), command.rationale(),
                    command.status(), command.nextReviewOn());
        }
        return view(item, signalFor(skillId));
    }

    @Transactional
    public PreparationLinkView linkPreparation(UUID backlogId, UUID prepItemId, String note) {
        PersonalSkillBacklog item = item(backlogId);
        PreparationItem prepItem = entityManager.find(PreparationItem.class, prepItemId);
        if (prepItem == null) throw new NotFoundException("Preparation item " + prepItemId + " was not found.");
        PersonalSkillPreparationLink link = preparationLinks.findByBacklogIdAndPrepItemId(backlogId, prepItemId)
                .orElseGet(() -> preparationLinks.save(new PersonalSkillPreparationLink(item, prepItem, note)));
        return PreparationLinkView.from(link);
    }

    @Transactional
    public ResourceView addResource(UUID backlogId, NewResource command) {
        SkillLearningResource resource = resources.save(new SkillLearningResource(item(backlogId), command.title(),
                command.url(), command.resourceType(), command.status(), command.notes()));
        return ResourceView.from(resource);
    }

    @Transactional
    public ProjectEvidenceView addProjectEvidence(UUID backlogId, NewProjectEvidence command) {
        SkillProjectEvidence evidence = projectEvidence.save(new SkillProjectEvidence(item(backlogId), command.title(),
                command.url(), command.evidenceType(), command.description(), command.outcome(), command.completedOn()));
        return ProjectEvidenceView.from(evidence);
    }

    @Transactional
    public SyncResult synchronizeAiPreparationBacklog() {
        List<PreparationItem> prepItems = entityManager.createQuery(
                "select i from PreparationItem i join fetch i.milestone m join fetch m.track "
                        + "where lower(m.title) = :title order by i.displayOrder, i.createdAt",
                PreparationItem.class).setParameter("title", AI_BACKLOG_MILESTONE).getResultList();
        int created = 0;
        int linksCreated = 0;
        for (PreparationItem prepItem : prepItems) {
            List<CanonicalSkill> matches = skillsForPreparationItem(prepItem.getTitle());
            if (matches.isEmpty()) {
                throw new IllegalStateException("No canonical skill or alias matches preparation item: " + prepItem.getTitle());
            }
            for (CanonicalSkill skill : matches) {
                PersonalSkillBacklog profile = backlog.findBySkillId(skill.getId()).orElse(null);
                if (profile == null) {
                    profile = backlog.save(new PersonalSkillBacklog(skill, SkillProficiencyLevel.NOT_ASSESSED,
                            SkillProficiencyLevel.WORKING_PROFICIENCY, prepItem.getPriority(),
                            USER_SELECTED_RATIONALE, PersonalSkillStatus.BACKLOG, prepItem.getNextReviewOn()));
                    created++;
                }
                if (preparationLinks.findByBacklogIdAndPrepItemId(profile.getId(), prepItem.getId()).isEmpty()) {
                    preparationLinks.save(new PersonalSkillPreparationLink(profile, prepItem,
                            "Retained from the AI skill backlog."));
                    linksCreated++;
                }
            }
        }
        return new SyncResult(prepItems.size(), created, linksCreated, backlog.count());
    }

    @Transactional
    public void mergeSkillBacklog(CanonicalSkill source, CanonicalSkill target) {
        PersonalSkillBacklog sourceBacklog = backlog.findBySkillId(source.getId()).orElse(null);
        if (sourceBacklog == null) return;
        PersonalSkillBacklog targetBacklog = backlog.findBySkillId(target.getId()).orElse(null);
        if (targetBacklog == null) {
            sourceBacklog.reassignSkill(target);
            return;
        }
        targetBacklog.absorb(sourceBacklog);
        for (PersonalSkillPreparationLink link : preparationLinks.findByBacklogId(sourceBacklog.getId())) {
            if (preparationLinks.findByBacklogIdAndPrepItemId(targetBacklog.getId(), link.getPrepItem().getId()).isPresent()) {
                preparationLinks.delete(link);
            } else link.reassignBacklog(targetBacklog);
        }
        resources.findByBacklogIdOrderByCreatedAtAsc(sourceBacklog.getId()).forEach(item -> item.reassignBacklog(targetBacklog));
        projectEvidence.findByBacklogIdOrderByCreatedAtAsc(sourceBacklog.getId()).forEach(item -> item.reassignBacklog(targetBacklog));
        backlog.delete(sourceBacklog);
    }

    private List<CanonicalSkill> skillsForPreparationItem(String title) {
        if (title.equalsIgnoreCase("Azure OpenAI and Azure AI Foundry")) {
            return List.of(skillByName("Azure OpenAI"), skillByName("Azure AI Foundry"));
        }
        String normalized = SkillEvidenceService.normalize(title);
        CanonicalSkill direct = skills.findByNormalizedName(normalized).orElse(null);
        if (direct != null) return List.of(direct);
        return aliases.findByNormalizedAlias(normalized).map(alias -> List.of(alias.getSkill())).orElse(List.of());
    }

    private CanonicalSkill skillByName(String name) {
        return skills.findByNormalizedName(SkillEvidenceService.normalize(name))
                .orElseThrow(() -> new IllegalStateException("Canonical skill is missing: " + name));
    }

    private MarketSignalService.SkillSignal signalFor(UUID skillId) {
        return marketSignals.overview(new MarketSignalService.MarketSignalQuery(null, null, null, null, null, null))
                .signals().stream().filter(signal -> signal.skillId().equals(skillId)).findFirst().orElse(null);
    }

    private PersonalSkillBacklog item(UUID id) {
        return backlog.findById(id).orElseThrow(() -> new NotFoundException("Personal skill backlog item " + id + " was not found."));
    }

    private BacklogItemView view(PersonalSkillBacklog item, MarketSignalService.SkillSignal signal) {
        List<PreparationLinkView> links = preparationLinks.findByBacklogId(item.getId()).stream()
                .map(PreparationLinkView::from).toList();
        List<ResourceView> resourceViews = resources.findByBacklogIdOrderByCreatedAtAsc(item.getId()).stream()
                .map(ResourceView::from).toList();
        List<ProjectEvidenceView> evidenceViews = projectEvidence.findByBacklogIdOrderByCreatedAtAsc(item.getId()).stream()
                .map(ProjectEvidenceView::from).toList();
        return new BacklogItemView(item.getId(), item.getSkill().getId(), item.getSkill().getName(),
                item.getSkill().getCategory(), item.getCurrentLevel(), item.getTargetLevel(), item.getPriority(),
                item.getRationale(), item.getStatus(), item.getNextReviewOn(),
                signal == null ? 0 : signal.currentOpeningCount(), signal == null ? 0 : signal.currentFrequencyPercent(),
                signal == null ? null : signal.trendDeltaPercentagePoints(), signal == null ? 0 : signal.requiredCount(),
                signal == null ? 0 : signal.preferredCount(), signal == null ? 0 : signal.mentionedCount(),
                signal == null ? 0 : signal.evidenceCount(), links, resourceViews, evidenceViews);
    }

    public record UpdateBacklog(SkillProficiencyLevel currentLevel, SkillProficiencyLevel targetLevel,
            int priority, String rationale, PersonalSkillStatus status, LocalDate nextReviewOn) {}
    public record NewResource(String title, String url, LearningResourceType resourceType,
            LearningResourceStatus status, String notes) {}
    public record NewProjectEvidence(String title, String url, ProjectEvidenceType evidenceType,
            String description, String outcome, LocalDate completedOn) {}
    public record SyncResult(int preparationItemsScanned, int backlogItemsCreated, int linksCreated, long backlogSize) {}
    public record BacklogOverview(int backlogSize, int activeCount, int linkedPreparationItems,
            int reviewedOpeningSampleSize, LocalDate demandFrom, LocalDate demandTo, List<BacklogItemView> items) {}
    public record BacklogItemView(UUID id, UUID skillId, String skillName, SkillCategory category,
            SkillProficiencyLevel currentLevel, SkillProficiencyLevel targetLevel, int priority, String rationale,
            PersonalSkillStatus status, LocalDate nextReviewOn, int currentOpeningCount,
            double currentFrequencyPercent, Double trendDeltaPercentagePoints, int requiredCount,
            int preferredCount, int mentionedCount, int evidenceCount, List<PreparationLinkView> preparationLinks,
            List<ResourceView> resources, List<ProjectEvidenceView> projectEvidence) {}
    public record PreparationLinkView(UUID id, UUID prepItemId, String prepItemTitle, String prepItemStatus,
            String trackName, String milestoneTitle, String linkNote) {
        static PreparationLinkView from(PersonalSkillPreparationLink link) {
            PreparationItem item = link.getPrepItem();
            return new PreparationLinkView(link.getId(), item.getId(), item.getTitle(), item.getStatus().name(),
                    item.getMilestone().getTrack().getName(), item.getMilestone().getTitle(), link.getLinkNote());
        }
    }
    public record ResourceView(UUID id, String title, String url, LearningResourceType resourceType,
            LearningResourceStatus status, String notes) {
        static ResourceView from(SkillLearningResource item) { return new ResourceView(item.getId(), item.getTitle(),
                item.getUrl(), item.getResourceType(), item.getStatus(), item.getNotes()); }
    }
    public record ProjectEvidenceView(UUID id, String title, String url, ProjectEvidenceType evidenceType,
            String description, String outcome, LocalDate completedOn) {
        static ProjectEvidenceView from(SkillProjectEvidence item) { return new ProjectEvidenceView(item.getId(),
                item.getTitle(), item.getUrl(), item.getEvidenceType(), item.getDescription(), item.getOutcome(), item.getCompletedOn()); }
    }
}
