package dev.dhruv.jobsearch.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import dev.dhruv.jobsearch.opportunity.JobOpportunity;
import dev.dhruv.jobsearch.opportunity.OpportunityService;
import dev.dhruv.jobsearch.resume.ResumeVariant;
import dev.dhruv.jobsearch.resume.ResumeVariantRepository;
import dev.dhruv.jobsearch.shared.NotFoundException;

@Service
public class ApplicationService {

    private final JobApplicationRepository applicationRepository;
    private final ApplicationEventRepository eventRepository;
    private final OpportunityService opportunityService;
    private final ResumeVariantRepository resumeRepository;
    private final ApplicationArtifactService artifactService;

    public ApplicationService(JobApplicationRepository applicationRepository,
            ApplicationEventRepository eventRepository,
            OpportunityService opportunityService,
            ResumeVariantRepository resumeRepository,
            ApplicationArtifactService artifactService) {
        this.applicationRepository = applicationRepository;
        this.eventRepository = eventRepository;
        this.opportunityService = opportunityService;
        this.resumeRepository = resumeRepository;
        this.artifactService = artifactService;
    }

    @Transactional
    public JobApplication create(UUID opportunityId, CreateApplication command) {
        applicationRepository.findByOpportunityId(opportunityId).ifPresent(existing -> {
            throw new IllegalStateException("An application already exists for this opportunity.");
        });
        JobOpportunity opportunity = opportunityService.get(opportunityId);
        ResumeVariant resume = resumeRepository.findById(command.resumeVariantId())
                .orElseThrow(() -> new NotFoundException("Resume variant " + command.resumeVariantId() + " was not found."));
        ApplicationStage stage = command.stage() == null ? ApplicationStage.DRAFT : command.stage();
        LocalDate appliedOn = command.appliedOn();
        if (stage != ApplicationStage.DRAFT && appliedOn == null) {
            appliedOn = LocalDate.now();
        }
        JobApplication application = applicationRepository.save(new JobApplication(
                opportunity, resume, stage, appliedOn, trimToNull(command.channel()),
                trimToNull(command.nextAction()), command.nextActionAt()));
        eventRepository.save(new ApplicationEvent(application, null, stage, trimToNull(command.note())));
        if (stage != ApplicationStage.DRAFT) {
            opportunity.markApplied();
        }
        return application;
    }

    @Transactional
    public JobApplication createWithArtifacts(UUID opportunityId, CreateApplication command,
            MultipartFile resumeFile, String jobDescription) {
        artifactService.validate(resumeFile, jobDescription);
        JobApplication application = create(opportunityId, command);
        artifactService.store(application, resumeFile, jobDescription);
        return application;
    }

    @Transactional
    public JobApplication transition(UUID id, TransitionApplication command) {
        JobApplication application = get(id);
        ApplicationStage previous = application.transitionTo(
                command.toStage(), trimToNull(command.nextAction()), command.nextActionAt());
        eventRepository.save(new ApplicationEvent(application, previous, command.toStage(), trimToNull(command.note())));
        if (command.toStage() == ApplicationStage.APPLIED) {
            application.getOpportunity().markApplied();
        }
        return application;
    }

    @Transactional
    public JobApplication updateFollowUp(UUID id, UpdateApplicationFollowUp command) {
        JobApplication application = get(id);
        ApplicationStage target = command.stage() == null ? application.getStage() : command.stage();
        if (target != application.getStage()) {
            ApplicationStage previous = application.transitionTo(target, null, null);
            eventRepository.save(new ApplicationEvent(application, previous, target, trimToNull(command.note())));
            if (target == ApplicationStage.APPLIED) {
                application.getOpportunity().markApplied();
            }
        }
        if (!target.isTerminal()) {
            application.scheduleFollowUp(command.nextAction(), command.nextActionAt());
        }
        return application;
    }

    @Transactional
    public JobApplication dropFollowUp(UUID id, String note) {
        JobApplication application = get(id);
        application.dropFollowUp();
        eventRepository.save(new ApplicationEvent(application, application.getStage(), application.getStage(),
                trimToNull(note) == null ? "Application follow-up removed from the active queue." : note.trim()));
        return application;
    }

    @Transactional(readOnly = true)
    public JobApplication get(UUID id) {
        return applicationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Application " + id + " was not found."));
    }

    @Transactional(readOnly = true)
    public List<JobApplication> list() {
        return applicationRepository.findAllByOrderByUpdatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<ApplicationEvent> events(UUID id) {
        get(id);
        return eventRepository.findByApplicationIdOrderByOccurredAtDesc(id);
    }

    @Transactional(readOnly = true)
    public List<ApplicationArtifact> artifacts(UUID id) {
        get(id);
        return artifactService.list(id);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record CreateApplication(
            UUID resumeVariantId,
            ApplicationStage stage,
            LocalDate appliedOn,
            String channel,
            String nextAction,
            Instant nextActionAt,
            String note) {
    }

    public record TransitionApplication(
            ApplicationStage toStage,
            String nextAction,
            Instant nextActionAt,
            String note) {
    }

    public record UpdateApplicationFollowUp(
            ApplicationStage stage,
            String nextAction,
            Instant nextActionAt,
            String note) {
    }
}
