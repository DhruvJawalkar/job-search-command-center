"use client";

import { FormEvent, useCallback, useEffect, useEffectEvent, useId, useMemo, useRef, useState } from "react";

type ApplicationStage =
  | "DRAFT" | "APPLIED" | "RECRUITER_SCREEN" | "INTERVIEWING" | "OFFER"
  | "ACCEPTED" | "WITHDRAWN" | "REJECTED" | "GHOSTED" | "CLOSED";
type OpportunityStatus = "NEW" | "REVIEWING" | "SHORTLISTED" | "SKIPPED" | "APPLIED" | "EXPIRED" | "ARCHIVED";
type DailyActionStatus = "TODO" | "DONE" | "SKIPPED";
type RelationshipStrength = "COLD" | "ACQUAINTANCE" | "FORMER_COLLEAGUE" | "WARM" | "STRONG";
type OutreachStatus = "PLANNED" | "SENT" | "RESPONDED" | "REFERRED" | "DECLINED" | "CLOSED";
type OutreachType = "REFERRAL_REQUEST" | "INTRODUCTION_REQUEST" | "RECRUITER_MESSAGE" | "FOLLOW_UP";
type OutreachTimingFilter = "ALL" | "OVERDUE" | "NEXT_SEVEN_DAYS" | "UNSCHEDULED";
type OutreachSort = "URGENCY" | "FOLLOW_UP" | "RECENT" | "COMPANY";
type WorkspaceView = "overview" | "opportunities" | "outreach" | "skills" | "preparation" | "reviews";

type ActionItem = {
  applicationId: string; companyName: string; roleTitle: string; stage: ApplicationStage;
  nextAction: string; nextActionAt: string;
};
type Opportunity = {
  id: string; companyName: string; roleTitle: string; location: string | null; workMode: string;
  status: OpportunityStatus; fitScore: number | null; fitSummary: string | null;
  discoveredAt: string; applicationId: string | null;
};
type Dashboard = {
  date: string; totalOpportunities: number; shortlistedOpportunities: number; activeApplications: number;
  appliedThisWeek: number; actionsDue: number; actions: ActionItem[]; opportunities: Opportunity[];
  pipeline: Partial<Record<ApplicationStage, number>>;
};
type ResumeVariant = { id: string; name: string; targetRole: string; versionLabel: string };
type ApplicationArtifact = { id: string; artifactType: "RESUME_PDF" | "JOB_DESCRIPTION_TEXT";
  originalFilename: string | null; storedPath: string; contentHash: string; mediaType: string; sizeBytes: number; createdAt: string };
type ApplicationRecord = { id: string; opportunityId: string; companyName: string; roleTitle: string;
  resumeVariantId: string; resumeName: string; resumeVersion: string; stage: ApplicationStage; appliedOn: string | null;
  channel: string | null; nextAction: string | null; nextActionAt: string | null; followUpActive: boolean; createdAt: string; updatedAt: string;
  artifacts: ApplicationArtifact[] };
type InterviewRound = { id: string; applicationId: string; title: string; type: string;
  status: "AWAITING_SCHEDULING" | "SCHEDULED" | "COMPLETED" | "CANCELLED";
  outcome: string; preparationNotes: string | null; debrief: string | null; calendarEventId: string | null;
  startsAt: string | null; durationMinutes: number | null; location: string | null; meetingUrl: string | null;
  reminderMinutesBefore: number | null; version: number; calendarVersion: number | null };
type Opening = {
  opportunityId: string; companyName: string; roleTitle: string; location: string | null; workMode: string;
  status: OpportunityStatus; sourceUrl: string; observedOn: string; rank: number; overallFit: number;
  recruiterScreenStrength: number; technicalScope: number; growthPotential: number; weightedTotal: number;
  recommendation: string; roleSummary: string; fitRationale: string; keyRisks: string | null;
  recommendedResumeVariant: string | null; applicationId: string | null; applicationStage: ApplicationStage | null;
  archiveReason: string | null;
  archivedAt: string | null; archivedFromStatus: OpportunityStatus | null;
};
type OpeningFeed = {
  latestObservationDate: string | null; resultCount: number; openings: Opening[]; availableDates: string[];
  recommendations: string[]; resumeVariants: string[];
};
type OpeningDetail = {
  opening: Opening; roleSummary: string; fitRationale: string; keyRisks: string | null;
  authorizationEligibility: string | null; postingDate: string | null; verifiedDate: string;
  observationHistory: { observedOn: string; rank: number; weightedTotal: number; recommendation: string; recommendedResumeVariant: string | null }[];
};
type DailyAction = {
  id: string; actionDate: string; priorityRank: number; actionText: string; status: DailyActionStatus;
  opportunityId: string | null; companyName: string | null; roleTitle: string | null; updatedAt: string;
};
type DailyActionDay = { date: string | null; actions: DailyAction[] };
type ImportBatch = {
  id: string; sourceFile: string; sourceDate: string; status: "RUNNING" | "COMPLETED" | "FAILED";
  rowsSeen: number; opportunitiesCreated: number; observationsCreated: number; actionsCreated: number;
  completedAt: string | null; errorMessage: string | null;
};
type ImportRun = { filesImported: number; filesUnchanged: number; filesFailed: number; opportunitiesCreated: number;
  files?: { file: string; result: string; error: string | null }[] };
type InboxSourceType = "PASTED_TEXT" | "PASTED_JSON" | "UPLOADED_CSV" | "UPLOADED_JSON";
type InboxItemStatus = "RECEIVED" | "PARSED" | "NEEDS_REVIEW" | "PARTIALLY_REVIEWED" | "IMPORTED" | "REJECTED" | "FAILED";
type InboxCandidateStatus = "NEEDS_REVIEW" | "DUPLICATE_REVIEW" | "IMPORTED" | "LINKED" | "MERGED" | "REJECTED";
type InboxDuplicateMatch = { id: string; opportunityId: string; companyName: string; roleTitle: string;
  location: string | null; workMode: string; sourceName: string | null; sourceExternalId: string | null;
  sourceUrl: string | null; description: string | null; matchType: "EXACT_URL" | "EXACT_EXTERNAL_ID" | "LIKELY_SIMILAR";
  confidence: number; explanation: string; status: "OPEN" | "RESOLVED" | "DISMISSED"; detectedAt: string };
type InboxCandidate = { id: string; rowNumber: number; companyName: string | null; roleTitle: string | null;
  location: string | null; workMode: string; sourceName: string | null; sourceExternalId: string | null; sourceUrl: string | null;
  description: string | null; parseWarnings: string | null; status: InboxCandidateStatus;
  opportunityId: string | null; reviewedAt: string | null; createdAt: string; updatedAt: string; duplicateMatches: InboxDuplicateMatch[] };
type InboxItem = { id: string; sourceType: InboxSourceType; sourceLabel: string | null; sourceFilename: string | null;
  mediaType: string | null; contentHash: string; parserVersion: string; status: InboxItemStatus; candidateCount: number;
  errorMessage: string | null; createdAt: string; updatedAt: string; candidates: InboxCandidate[] };
type InboxCreateResult = { replayed: boolean; item: InboxItem };
type Contact = {
  id: string; fullName: string; companyName: string | null; roleTitle: string | null;
  profileUrl: string | null; email: string | null; relationshipStrength: RelationshipStrength;
};
type Outreach = {
  id: string; opportunityId: string; companyName: string; roleTitle: string; applicationId: string | null;
  contactId: string; contactName: string; contactCompany: string | null; relationshipStrength: RelationshipStrength;
  outreachType: OutreachType; status: OutreachStatus; channel: string | null; messageSummary: string | null;
  requestedAt: string | null; followUpAt: string | null; followUpCount: number; respondedAt: string | null; outcome: string | null;
  notes: string | null; overdue: boolean; updatedAt: string;
};
type ReferralChannel = "LINKEDIN" | "FORMER_COLLEAGUE" | "ALUMNI" | "RECRUITER" | "EMAIL" | "WHATSAPP" | "COMMUNITY" | "OTHER";
type LinkedInPathSearch = "RECRUITER" | "ENGINEERING_MANAGER" | "ENGINEER" | "FORMER_COLLEAGUE" | "ALUMNI";
type MessageTemplateScenario = "" | "CONNECTION_SECOND_DEGREE" | "CONNECTION_ALUMNI" | "CONNECTION_FORMER_COLLEAGUE"
  | "CONNECTION_RECRUITER" | "INMAIL_RECRUITER" | "MESSAGE_FRIEND_REFERRAL";
type ReferralCandidate = { id: string; opportunityId: string; companyName: string; opportunityRole: string; fullName: string;
  candidateCompany: string | null; roleTitle: string | null; profileUrl: string | null; connectionDegree: "FIRST" | "SECOND" | "OTHER";
  discoveryChannel: ReferralChannel; relationshipStrength: RelationshipStrength; mutualConnectionName: string | null;
  mutualConnectionUrl: string | null; currentCompanyMatch: boolean; formerCompanyMatch: boolean; roleRelevance: number;
  responsiveness: number; lastInteractionOn: string | null; status: "DISCOVERED" | "SHORTLISTED" | "OUTREACH_CREATED" | "DISMISSED";
  pathScore: number; pathStrength: "STRONG" | "PROMISING" | "EXPLORATORY"; scoreExplanation: string; notes: string | null;
  contactId: string | null; outreachId: string | null; updatedAt: string };
type ReferralDiscoveryDialog = { kind: "candidate"; channel: ReferralChannel } | { kind: "outreach"; candidate: ReferralCandidate };
type PrepItemStatus = "BACKLOG" | "READY" | "IN_PROGRESS" | "IN_REVIEW" | "COMPLETED" | "SKIPPED";
type PrepItem = { id: string; title: string; description: string | null; status: PrepItemStatus; priority: number;
  estimatedMinutes: number; scheduledFor: string | null; dueDate: string | null; skillFocus: string | null;
  nextReviewOn: string | null; opportunityId: string | null; opportunityLabel: string | null; completedAt: string | null };
type PrepMilestone = { id: string; title: string; description: string | null; status: string; targetDate: string | null; items: PrepItem[] };
type PrepTrackResource = { id: string; title: string; url: string; notes: string | null; createdAt: string };
type PrepTrack = { id: string; name: string; description: string | null; category: string; status: string; targetDate: string | null; displayOrder: number; milestones: PrepMilestone[]; resources: PrepTrackResource[] };
type PrepSession = { id: string; prepItemId: string; itemTitle: string; sessionType: string; practicedAt: string;
  durationMinutes: number; resultSummary: string | null; mistakes: string | null; nextSteps: string | null;
  confidenceBefore: number | null; confidenceAfter: number | null; nextReviewOn: string | null };
type PrepCommitment = { id: string; commitmentDate: string; prepItemId: string | null; itemTitle: string; plannedMinutes: number;
  status: "PLANNED" | "COMPLETED" | "SKIPPED"; intention: string | null; reflection: string | null; completedAt: string | null };
type PrepSprintTask = { itemId: string; title: string; status: PrepItemStatus };
type PrepSprintSummary = { total: number; done: number; inReview: number; inProgress: number; open: number };
type PrepSprint = { id: string; status: "ACTIVE" | "CLOSED"; startDate: string; endDate: string; closedAt: string | null;
  canClose: boolean; tasks: PrepSprintTask[]; summary: PrepSprintSummary };
type PreparationOverview = { date: string; today: PrepCommitment | null; stats: { readyItems: number; sessionsThisWeek: number; minutesThisWeek: number };
  tracks: PrepTrack[]; recentSessions: PrepSession[]; activeSprint: PrepSprint | null; recentSprints: PrepSprint[] };
type WeeklyMetric = { weekStart: string; weekEnd: string; effectiveThrough: string; completeWeek: boolean; capturedAt: string;
  openingsDiscovered: number; applicationsSubmitted: number; highFitApplications: number; applicationProgressions: number;
  outreachSent: number; outreachResponses: number; referralsSecured: number; preparationSessions: number; preparationMinutes: number;
  preparationTasksPlanned: number; preparationTasksCompleted: number; commitmentsPlanned: number; commitmentsCompleted: number;
  dailyActionsPlanned: number; dailyActionsCompleted: number;
  activePipeline: number; overdueApplicationActions: number; overdueOutreachFollowUps: number; stageApplied: number;
  stageRecruiterScreen: number; stageInterviewing: number; stageOffer: number };
type WeeklyMetricDelta = { applicationsSubmitted: number; outreachSent: number; outreachResponses: number;
  preparationSessions: number; preparationMinutes: number; preparationTasksCompleted: number; commitmentsCompleted: number; dailyActionsCompleted: number };
type WeeklyReviewRevision = { id: string; revisionNumber: number; wins: string | null; challenges: string | null;
  reflection: string | null; nextWeekAdjustments: string | null; nextWeekFocus: string | null; createdAt: string };
type WeeklyReview = { id: string; weekStart: string; weekEnd: string; effectiveThrough: string; completeWeek: boolean;
  generatedAt: string; metrics: WeeklyMetric; delta: WeeklyMetricDelta; revisions: WeeklyReviewRevision[] };
type WeeklyReviewOverview = { today: string; livePreview: WeeklyMetric; reviews: WeeklyReview[] };
type MonthlyProgressDay = { date: string; applicationsSubmitted: number; outreachSent: number; outreachResponses: number;
  referralsSecured: number; preparationTasksPlanned: number; preparationTasksCompleted: number;
  preparationSessions: number; preparationMinutes: number };
type MonthlyProgress = { monthStart: string; monthEnd: string; today: string; totals: Omit<MonthlyProgressDay, "date">; days: MonthlyProgressDay[] };
type AssistanceConfiguration = { configured: boolean; provider: string; model: string; message: string };
type AssistedFields = { companyName: string | null; roleTitle: string | null; location: string | null; workMode: string | null;
  sourceName: string | null; sourceExternalId: string | null; sourceUrl: string | null; description: string | null };
type AssistedSkill = { name: string; strength: "REQUIRED" | "PREFERRED" | "MENTIONED"; evidenceSnippet: string };
type InboxSuggestion = { fields: AssistedFields; responsibilities: string[]; requiredQualifications: string[];
  preferredQualifications: string[]; skills: AssistedSkill[]; warnings: string[]; reviewQuestions: string[] };
type AssistanceDecision = { id: string; decisionType: "FIELDS_APPLIED" | "SKILLS_PUBLISHED" | "DISMISSED";
  selectedFields: string | null; note: string | null; createdAt: string };
type InboxAssistanceRun = { id: string; status: "RUNNING" | "COMPLETED" | "FAILED"; provider: string; model: string;
  promptVersion: string; schemaVersion: string; errorMessage: string | null; createdAt: string; completedAt: string | null;
  inputTokens: number | null; outputTokens: number | null; suggestion: InboxSuggestion | null; decisions: AssistanceDecision[] };
type InboxAssistanceOverview = { configuration: AssistanceConfiguration; outboundContent: string;
  current: { candidateId: string; inboxItemId: string; rawPayload: string; companyName: string | null; roleTitle: string | null;
    location: string | null; workMode: string; sourceName: string | null; sourceExternalId: string | null;
    sourceUrl: string | null; description: string | null; opportunityId: string | null }; runs: InboxAssistanceRun[] };
type WeeklyDraft = { wins: string | null; challenges: string | null; reflection: string | null;
  nextWeekAdjustments: string | null; nextWeekFocus: string | null; evidence: string[] };
type WeeklyAssistanceRun = { id: string; status: "RUNNING" | "COMPLETED" | "FAILED"; provider: string; model: string;
  promptVersion: string; errorMessage: string | null; createdAt: string; completedAt: string | null; draft: WeeklyDraft | null };
type WeeklyAssistanceOverview = { configuration: AssistanceConfiguration; review: WeeklyReview; runs: WeeklyAssistanceRun[] };
type PrepDialog = { kind: "track" } | { kind: "milestone"; trackId: string; label: string }
  | { kind: "story-edit"; milestone: PrepMilestone; trackName: string }
  | { kind: "item"; milestoneId: string; label: string } | { kind: "focus" | "session"; item: PrepItem };
type TaskDetailsTarget = { context: PrepTaskContext; initialTab: "DETAILS" | "SESSION" };
type SkillCategory = "AI_AGENT" | "BACKEND" | "CLOUD_INFRASTRUCTURE" | "DATA_PLATFORM" | "DATABASE" | "DEVOPS" | "SECURITY" | "OTHER";
type SkillReviewStatus = "PROPOSED" | "ACCEPTED" | "REJECTED";
type SkillView = { id: string; name: string; normalizedName: string; category: SkillCategory; description: string | null;
  active: boolean; aliases: string[]; createdAt: string; updatedAt: string };
type SkillObservation = { id: string; opportunityId: string; companyName: string; roleTitle: string; snapshotId: string;
  skillId: string; skillName: string; category: SkillCategory; strength: "REQUIRED" | "PREFERRED" | "MENTIONED";
  evidenceSnippet: string; extractionMethod: "MANUAL" | "DETERMINISTIC" | "AI_ASSISTED" | "IMPORTED";
  reviewStatus: SkillReviewStatus; reviewNote: string | null; observedAt: string; reviewedAt: string | null; updatedAt: string };
type SkillOverview = { catalogSize: number; proposedCount: number; acceptedCount: number; rejectedCount: number;
  skills: SkillView[]; observations: SkillObservation[] };
type SkillDialog = { kind: "skill" } | { kind: "evidence" }
  | { kind: "correction"; observation: SkillObservation }
  | { kind: "merge"; source: SkillView };
type SkillSeedResult = { skillsCreated: number; aliasesCreated: number; catalogSize: number };
type LiveSkillExtractionOpportunity = { opportunityId: string; companyName: string; roleTitle: string;
  fetched: boolean; snapshotCreated: boolean; descriptionCharacters: number; detail: string };
type LiveSkillExtractionResult = { opportunitiesEligible: number; pagesFetched: number; snapshotsCreated: number;
  fetchFailures: number; catalogSize: number; matchesFound: number; observationsCreated: number;
  opportunities: LiveSkillExtractionOpportunity[] };
type RoleFamily = "AI_ML_PLATFORM" | "BACKEND_DISTRIBUTED_SYSTEMS" | "CLOUD_INFRASTRUCTURE" | "DATA_PLATFORM" | "SECURITY_IDENTITY" | "GENERAL_SOFTWARE";
type SeniorityBand = "PRINCIPAL_OR_ABOVE" | "STAFF" | "LEAD_OR_ARCHITECT" | "SENIOR" | "OTHER_OR_UNSPECIFIED";
type SkillStrength = "REQUIRED" | "PREFERRED" | "MENTIONED";
type MarketSignalFilters = { from: string; to: string; roleFamily: "" | RoleFamily; seniority: "" | SeniorityBand;
  companySegment: string; strength: "" | SkillStrength };
type MarketSignalQuery = { from: string; to: string; previousFrom: string; previousTo: string;
  roleFamily: RoleFamily | null; seniority: SeniorityBand | null; companySegment: string | null; strength: SkillStrength | null };
type MarketSkillSignal = { skillId: string; skillName: string; category: SkillCategory; currentOpeningCount: number;
  currentFrequencyPercent: number; previousOpeningCount: number; previousFrequencyPercent: number;
  trendDeltaPercentagePoints: number | null; requiredCount: number; preferredCount: number; mentionedCount: number; evidenceCount: number };
type MarketSignalOverview = { query: MarketSignalQuery; currentSampleSize: number; previousSampleSize: number;
  acceptedEvidenceCount: number; signals: MarketSkillSignal[]; options: { roleFamilies: RoleFamily[];
    seniorities: SeniorityBand[]; companySegments: string[]; strengths: SkillStrength[] } };
type MarketEvidence = { observationId: string; skillId: string; skillName: string; category: SkillCategory;
  opportunityId: string; companyName: string; roleTitle: string; roleFamily: RoleFamily; seniority: SeniorityBand;
  companySegment: string; strength: SkillStrength; evidenceSnippet: string; sourceType: string; sourceLabel: string | null;
  sourceUrl: string | null; discoveredOn: string; reviewedAt: string | null };
type MarketEvidenceDrilldown = { skillId: string; skillName: string | null; query: MarketSignalQuery; evidence: MarketEvidence[] };
type SkillProficiencyLevel = "NOT_ASSESSED" | "AWARENESS" | "PRACTICING" | "WORKING_PROFICIENCY" | "PROFICIENT" | "ADVANCED";
type PersonalSkillStatus = "BACKLOG" | "ACTIVE" | "PAUSED" | "ACHIEVED";
type BacklogPreparationLink = { id: string; prepItemId: string; prepItemTitle: string; prepItemStatus: PrepItemStatus;
  trackName: string; milestoneTitle: string; linkNote: string | null };
type SkillLearningResource = { id: string; title: string; url: string | null; resourceType: string; status: string; notes: string | null };
type SkillProjectEvidence = { id: string; title: string; url: string | null; evidenceType: string; description: string | null;
  outcome: string | null; completedOn: string | null };
type PersonalSkillBacklogItem = { id: string; skillId: string; skillName: string; category: SkillCategory;
  currentLevel: SkillProficiencyLevel; targetLevel: SkillProficiencyLevel; priority: number; rationale: string | null;
  status: PersonalSkillStatus; nextReviewOn: string | null; currentOpeningCount: number; currentFrequencyPercent: number;
  trendDeltaPercentagePoints: number | null; requiredCount: number; preferredCount: number; mentionedCount: number;
  evidenceCount: number; preparationLinks: BacklogPreparationLink[]; resources: SkillLearningResource[];
  projectEvidence: SkillProjectEvidence[] };
type PersonalSkillBacklogOverview = { backlogSize: number; activeCount: number; linkedPreparationItems: number;
  reviewedOpeningSampleSize: number; demandFrom: string; demandTo: string; items: PersonalSkillBacklogItem[] };
type BacklogDialog = { kind: "profile" | "resource" | "project" | "preparation"; item: PersonalSkillBacklogItem };
type LinkedInConnection = { id: string; fullName: string; companyName: string | null; roleTitle: string | null;
  profileUrl: string | null; connectedOn: string | null };
type LinkedInImport = { id: string; sourceFile: string; status: "RUNNING" | "COMPLETED" | "FAILED"; rowsSeen: number;
  connectionsCreated: number; connectionsUpdated: number; rowsSkipped: number; errorMessage: string | null;
  startedAt: string; completedAt: string | null; replayed: boolean };
type LinkedInOverview = { totalConnections: number; latestImport: LinkedInImport | null };
type CalendarEventType = "INTERVIEW" | "RECRUITER_CALL" | "NETWORKING" | "MEETING" | "DEADLINE" | "PERSONAL";
type CalendarEvent = { id: string; title: string; eventType: CalendarEventType; description: string | null;
  location: string | null; meetingUrl: string | null; startsAt: string; endsAt: string;
  reminderMinutesBefore: number | null; reminderAt: string | null; reminderDismissedAt: string | null;
  createdAt: string; updatedAt: string; interviewRoundId?: string | null; applicationId?: string | null; interviewStatus?: string | null };
type CalendarView = "MONTH" | "WEEK" | "DAY";
type SavedNote = { filename: string; storedPath: string; savedAt: string };
type ApplicationTarget = { opportunity: Opportunity; preferredResumeName?: string | null };
type Toast = { kind: "success" | "error"; message: string } | null;
type DailyFocusPlan = { label: string; title: string; description: string; secondaryTitle: string; pattern: RegExp };
type TechnologyWatchItem = { theme: string; title: string; insight: string; prompt: string; source: string; href: string };

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://127.0.0.1:8080";
const OPENINGS_PER_PAGE = 10;
const OUTREACH_PER_PAGE = 5;
const APPLICATION_FOLLOWUPS_PER_PAGE = 5;
const WEEKLY_APPLICATION_TARGET = 50;
const PREP_ITEMS_PER_PAGE = 5;
const SKILL_REVIEW_PER_PAGE = 8;
const MARKET_SIGNALS_PER_PAGE = 10;
const CALENDAR_NOTICE_STORAGE_KEY = "job-search.calendar-notice-dismissals.v1";
const weeklyFocusPlans: Record<number, DailyFocusPlan> = {
  0: { label: "Writing & weekly synthesis", title: "Technical article writing and publication",
    description: "Turn the week’s engineering learning into a clear technical article, then close the review and plan the next week.",
    secondaryTitle: "Weekly review and next-week planning", pattern: /(article|writing|publish|review|reflection|plan)/i },
  1: { label: "Coding depth", title: "Advanced coding patterns and implementation",
    description: "Extend the protected coding block with timed, ambiguous problems and rigorous correctness and complexity review.",
    secondaryTitle: "Coding solution review and edge-case hardening", pattern: /(coding|algorithm|data structure|problem|binary search|subsets)/i },
  2: { label: "Concurrency", title: "Multi-threaded and memory-efficient systems",
    description: "Design production-grade concurrent components with explicit correctness, backpressure, memory, and failure trade-offs.",
    secondaryTitle: "Concurrency implementation and verification", pattern: /(multi.?thread|concurren|memory.?efficient|worker pool|scheduler|log merger)/i },
  3: { label: "Object & API design", title: "Object-oriented design and API design",
    description: "Practice requirements discovery, domain modeling, interfaces, patterns, API contracts, and extensible implementation.",
    secondaryTitle: "OOD implementation and design review", pattern: /(object.?oriented|ood|api design|design pattern|domain model)/i },
  4: { label: "Systems design", title: "Systems design interview prep and mock",
    description: "Work a realistic system end to end: clarify requirements, estimate scale, diagram, defend trade-offs, and handle follow-ups.",
    secondaryTitle: "Systems design mock and retrospective", pattern: /(system.?design|architecture|scalability|distributed system|mock)/i },
  5: { label: "Agentic AI delivery", title: "Governed agent project development",
    description: "Ship a concrete governed-agent milestone with code, tests, observable evidence, and a clear portfolio outcome.",
    secondaryTitle: "Agentic AI build, test, and evidence capture", pattern: /(releaseguard|agentic|rag|guardrail|mcp|agent)/i },
  6: { label: "AI frameworks & research", title: "Agentic AI frameworks, MCP, and RAG practice",
    description: "Build hands-on fluency with agent orchestration, tools, MCP, RAG, evaluation, and secure human approval patterns.",
    secondaryTitle: "Technical article research and outline", pattern: /(agent|langgraph|langchain|semantic kernel|mcp|rag|framework|article|writing)/i },
};
const technologyWatchItems: TechnologyWatchItem[] = [
  { theme: "Agent observability", title: "Treat agent traces as production evidence",
    insight: "The OpenAI Agents SDK traces generations, tool calls, handoffs, guardrails, and custom events across an end-to-end workflow.",
    prompt: "Add one trace-backed evaluation path to the agent project and make the failure signal visible in the project demo.",
    source: "OpenAI Agents SDK", href: "https://openai.github.io/openai-agents-python/tracing/" },
  { theme: "Human-in-the-loop", title: "Durable approval is a system-design problem",
    insight: "LangGraph interrupts persist graph state and pause execution until an external decision resumes the workflow.",
    prompt: "Model human approval as a durable checkpoint, including timeout, rejection, replay, and audit behavior.",
    source: "LangGraph documentation", href: "https://langchain-ai.github.io/langgraph/concepts/breakpoints/" },
  { theme: "Secure tool use", title: "MCP needs an explicit trust boundary",
    insight: "The MCP tool specification calls for input validation, access control, confirmation on sensitive operations, timeouts, and audit logging.",
    prompt: "Turn those controls into a concise threat model and acceptance checklist for every agent tool.",
    source: "Model Context Protocol", href: "https://modelcontextprotocol.io/specification/latest/server/tools" },
];
const workspaceViews: { id: WorkspaceView; index: string; label: string; shortLabel: string; description: string }[] = [
  { id: "overview", index: "01", label: "Summary", shortLabel: "Summary", description: "Decisions, priorities, and pipeline health" },
  { id: "opportunities", index: "02", label: "Opportunities", shortLabel: "Openings", description: "High-fit roles and intake review" },
  { id: "outreach", index: "03", label: "Network", shortLabel: "Network", description: "Referral paths and follow-ups" },
  { id: "skills", index: "04", label: "Market & skills", shortLabel: "Skills", description: "Demand evidence and capability plans" },
  { id: "preparation", index: "05", label: "Preparation", shortLabel: "Prep", description: "Tracks, sprint, stories, and tasks" },
  { id: "reviews", index: "06", label: "Weekly review", shortLabel: "Review", description: "Outcomes, health, and reflection" },
];
const dailyMotivations = [
  "Steady, thoughtful action compounds into the opportunity you are working toward.",
  "One clear next step is enough to turn preparation into momentum.",
  "Your experience is real; today’s work helps the right team recognize it.",
  "Consistency creates the surface area where opportunity can find you.",
  "A focused application is not just a submission—it is a confident introduction.",
  "Progress is built quietly: one conversation, one application, one lesson at a time.",
  "You do not need every door to open; you need the right one to recognize your value.",
  "Preparation gives confidence a foundation and persistence gives it direction.",
  "Every thoughtful outreach expands the map of what may become possible.",
  "The work you invest today makes tomorrow’s conversation stronger.",
  "Keep the standard high and the next action small enough to begin now.",
  "Your career story grows stronger each time you connect evidence to impact.",
  "Momentum is a practice: return, refine, and keep moving with purpose.",
  "A strong week begins with one deliberate action completed well.",
];
const preparationMotivations = [
  "Focused intensity turns repetition into readiness—and readiness into confidence.",
  "Deliberate practice is how high standards become dependable performance.",
  "Prepare with enough depth that pressure reveals your training, not your uncertainty.",
  "The winning edge is rarely one heroic effort; it is focused work repeated with honest feedback.",
  "Practice the hard parts on purpose. Confidence follows evidence.",
  "Intensity gives the session energy; reflection makes the improvement last.",
  "Every completed sprint task is proof that your future performance is being built today.",
];
const emptyFeed: OpeningFeed = {
  latestObservationDate: null, resultCount: 0, openings: [], availableDates: [], recommendations: [], resumeVariants: [],
};
const emptyPreparation: PreparationOverview = { date: new Date().toISOString().slice(0, 10), today: null,
  stats: { readyItems: 0, sessionsThisWeek: 0, minutesThisWeek: 0 }, tracks: [], recentSessions: [], activeSprint: null, recentSprints: [] };
const emptySkillOverview: SkillOverview = { catalogSize: 0, proposedCount: 0, acceptedCount: 0, rejectedCount: 0,
  skills: [], observations: [] };
const emptyPersonalBacklog: PersonalSkillBacklogOverview = { backlogSize: 0, activeCount: 0, linkedPreparationItems: 0,
  reviewedOpeningSampleSize: 0, demandFrom: dateOffset(-89), demandTo: dateOffset(0), items: [] };
const emptyLinkedInOverview: LinkedInOverview = { totalConnections: 0, latestImport: null };

function dateOffset(days: number) {
  const date = new Date();
  date.setDate(date.getDate() + days);
  return date.toISOString().slice(0, 10);
}

function dayIndex(value: string) {
  const timestamp = Date.parse(`${value}T00:00:00Z`);
  return Number.isNaN(timestamp) ? 0 : Math.floor(timestamp / 86_400_000);
}

function initialMarketFilters(): MarketSignalFilters {
  return { from: dateOffset(-89), to: dateOffset(0), roleFamily: "", seniority: "", companySegment: "", strength: "" };
}

function marketQuery(filters: MarketSignalFilters) {
  const params = new URLSearchParams({ from: filters.from, to: filters.to });
  if (filters.roleFamily) params.set("roleFamily", filters.roleFamily);
  if (filters.seniority) params.set("seniority", filters.seniority);
  if (filters.companySegment) params.set("companySegment", filters.companySegment);
  if (filters.strength) params.set("strength", filters.strength);
  return params.toString();
}
const demoDashboard: Dashboard = {
  date: "2026-08-20", totalOpportunities: 34, shortlistedOpportunities: 19,
  activeApplications: 0, appliedThisWeek: 0, actionsDue: 0, actions: [], opportunities: [],
  pipeline: { DRAFT: 0, APPLIED: 0, RECRUITER_SCREEN: 0, INTERVIEWING: 0, OFFER: 0 },
};
const demoActions: DailyActionDay = {
  date: "2026-08-20",
  actions: [
    { id: "demo-1", actionDate: "2026-08-20", priorityRank: 1, status: "TODO", opportunityId: null,
      companyName: "Sonatype", roleTitle: "Senior Software Engineer - Data", updatedAt: "2026-08-20T08:00:00+05:30",
      actionText: "Apply to Sonatype first using the Data Platforms & Streaming Systems resume." },
    { id: "demo-2", actionDate: "2026-08-20", priorityRank: 2, status: "TODO", opportunityId: null,
      companyName: "Yext", roleTitle: "Senior Cloud Security Engineer", updatedAt: "2026-08-20T08:00:00+05:30",
      actionText: "Seek a Yext referral and use the Security & Privacy Platforms resume." },
    { id: "demo-3", actionDate: "2026-08-20", priorityRank: 3, status: "TODO", opportunityId: null,
      companyName: "GHX", roleTitle: "Senior Software Engineer", updatedAt: "2026-08-20T08:00:00+05:30",
      actionText: "Submit GHX as the fast secondary application with light Java/Spring/AWS tailoring." },
  ],
};

const stageLabels: Record<ApplicationStage, string> = {
  DRAFT: "Draft", APPLIED: "Applied", RECRUITER_SCREEN: "Recruiter screen", INTERVIEWING: "Interviewing",
  OFFER: "Offer", ACCEPTED: "Accepted", WITHDRAWN: "Withdrawn", REJECTED: "Rejected",
  GHOSTED: "Ghosted", CLOSED: "Closed",
};
const applicationStageProgression: Partial<Record<ApplicationStage, ApplicationStage[]>> = {
  DRAFT: ["APPLIED", "WITHDRAWN"],
  APPLIED: ["RECRUITER_SCREEN", "REJECTED", "GHOSTED", "WITHDRAWN", "CLOSED"],
  RECRUITER_SCREEN: ["INTERVIEWING", "REJECTED", "WITHDRAWN", "CLOSED"],
  INTERVIEWING: ["OFFER", "REJECTED", "WITHDRAWN"],
  OFFER: ["ACCEPTED", "REJECTED", "WITHDRAWN"],
};
const referralChannels: { value: ReferralChannel; index: string; label: string; description: string }[] = [
  { value: "LINKEDIN", index: "01", label: "LinkedIn network", description: "1st- and 2nd-degree paths" },
  { value: "FORMER_COLLEAGUE", index: "02", label: "Former colleagues", description: "People who know your work" },
  { value: "ALUMNI", index: "03", label: "Alumni network", description: "School and employer alumni" },
  { value: "RECRUITER", index: "04", label: "Recruiters", description: "Internal and trusted external" },
  { value: "COMMUNITY", index: "05", label: "Communities", description: "Engineering groups and peers" },
  { value: "EMAIL", index: "06", label: "Direct channels", description: "Email, WhatsApp, or introductions" },
];
const linkedInPathSearches: { value: LinkedInPathSearch; label: string; description: string }[] = [
  { value: "ALUMNI", label: "Alumni network", description: "Target-company employees from schools you attended" },
  { value: "FORMER_COLLEAGUE", label: "Former colleagues", description: "Target-company employees with previous-employer overlap" },
  { value: "RECRUITER", label: "Recruiters", description: "Recruiting and talent partners at the opening company" },
  { value: "ENGINEERING_MANAGER", label: "Engineering managers", description: "Engineering managers and directors near the target domain" },
  { value: "ENGINEER", label: "Engineers", description: "Engineers working in adjacent platform or product areas" },
];
const messageTemplateScenarios: { value: Exclude<MessageTemplateScenario, "">; label: string; group: "Connection request" | "InMail" | "Message" }[] = [
  { value: "CONNECTION_SECOND_DEGREE", label: "2nd-degree contact", group: "Connection request" },
  { value: "CONNECTION_ALUMNI", label: "Alumni network", group: "Connection request" },
  { value: "CONNECTION_FORMER_COLLEAGUE", label: "Former colleague", group: "Connection request" },
  { value: "CONNECTION_RECRUITER", label: "Recruiter", group: "Connection request" },
  { value: "INMAIL_RECRUITER", label: "Recruiter about an opportunity", group: "InMail" },
  { value: "MESSAGE_FRIEND_REFERRAL", label: "Friend about a referral", group: "Message" },
];

function localDateTime(hoursFromNow: number) {
  const date = new Date(Date.now() + hoursFromNow * 60 * 60 * 1000);
  date.setMinutes(date.getMinutes() - date.getTimezoneOffset());
  return date.toISOString().slice(0, 16);
}

function hoursFromNowIso(hoursFromNow: number) {
  return new Date(Date.now() + hoursFromNow * 60 * 60 * 1000).toISOString();
}

function isoToLocalInput(value: string) {
  const date = new Date(value);
  date.setMinutes(date.getMinutes() - date.getTimezoneOffset());
  return date.toISOString().slice(0, 16);
}

async function api<T>(path: string, init?: RequestInit): Promise<T> {
  const multipart = typeof FormData !== "undefined" && init?.body instanceof FormData;
  const response = await fetch(`${API_BASE}${path}`, {
    ...init, headers: multipart ? init?.headers : { "Content-Type": "application/json", ...(init?.headers ?? {}) },
  });
  if (!response.ok) {
    const problem = (await response.json().catch(() => null)) as { detail?: string } | null;
    throw new Error(problem?.detail ?? `Request failed with status ${response.status}.`);
  }
  return response.json() as Promise<T>;
}

export default function Home() {
  const [activeWorkspace, setActiveWorkspace] = useState<WorkspaceView>("overview");
  const [dashboard, setDashboard] = useState<Dashboard>(demoDashboard);
  const [feed, setFeed] = useState<OpeningFeed>(emptyFeed);
  const [archivedFeed, setArchivedFeed] = useState<OpeningFeed>(emptyFeed);
  const [dailyActions, setDailyActions] = useState<DailyActionDay>(demoActions);
  const [imports, setImports] = useState<ImportBatch[]>([]);
  const [inboxItems, setInboxItems] = useState<InboxItem[]>([]);
  const [resumes, setResumes] = useState<ResumeVariant[]>([]);
  const [applications, setApplications] = useState<ApplicationRecord[]>([]);
  const [interviewApplicationId, setInterviewApplicationId] = useState<string | null>(null);
  const [contacts, setContacts] = useState<Contact[]>([]);
  const [outreach, setOutreach] = useState<Outreach[]>([]);
  const [referralCandidates, setReferralCandidates] = useState<ReferralCandidate[]>([]);
  const [preparation, setPreparation] = useState<PreparationOverview>(emptyPreparation);
  const [weeklyReviews, setWeeklyReviews] = useState<WeeklyReviewOverview | null>(null);
  const [monthlyProgress, setMonthlyProgress] = useState<MonthlyProgress | null>(null);
  const [monthlyProgressLoading, setMonthlyProgressLoading] = useState(false);
  const [selectedWeeklyReviewId, setSelectedWeeklyReviewId] = useState<string | null>(null);
  const [weeklyReviewBusy, setWeeklyReviewBusy] = useState(false);
  const [weeklyRevisionTarget, setWeeklyRevisionTarget] = useState<WeeklyReview | null>(null);
  const [weeklyAssistanceTarget, setWeeklyAssistanceTarget] = useState<WeeklyReview | null>(null);
  const [weeklyWorked, setWeeklyWorked] = useState("");
  const [weeklyImprove, setWeeklyImprove] = useState("");
  const [weeklyReflectionSaving, setWeeklyReflectionSaving] = useState(false);
  const [skillOverview, setSkillOverview] = useState<SkillOverview>(emptySkillOverview);
  const [personalBacklog, setPersonalBacklog] = useState<PersonalSkillBacklogOverview>(emptyPersonalBacklog);
  const [linkedInOverview, setLinkedInOverview] = useState<LinkedInOverview>(emptyLinkedInOverview);
  const [linkedInMatches, setLinkedInMatches] = useState<LinkedInConnection[]>([]);
  const [calendarEvents, setCalendarEvents] = useState<CalendarEvent[]>([]);
  const [dueCalendarReminders, setDueCalendarReminders] = useState<CalendarEvent[]>([]);
  const [calendarOpen, setCalendarOpen] = useState(false);
  const [dismissedCalendarNoticeKeys, setDismissedCalendarNoticeKeys] = useState<string[]>(readDismissedCalendarNoticeKeys);
  const [connected, setConnected] = useState(false);
  const [loading, setLoading] = useState(true);
  const [syncing, setSyncing] = useState(false);
  const [opportunityModal, setOpportunityModal] = useState(false);
  const [inboxIntakeOpen, setInboxIntakeOpen] = useState(false);
  const [inboxEditCandidate, setInboxEditCandidate] = useState<InboxCandidate | null>(null);
  const [inboxAssistanceCandidate, setInboxAssistanceCandidate] = useState<InboxCandidate | null>(null);
  const [duplicateReviewCandidate, setDuplicateReviewCandidate] = useState<InboxCandidate | null>(null);
  const [inboxBusyId, setInboxBusyId] = useState<string | null>(null);
  const [duplicateScanBusy, setDuplicateScanBusy] = useState(false);
  const [applicationTarget, setApplicationTarget] = useState<ApplicationTarget | null>(null);
  const [referralTarget, setReferralTarget] = useState<Opening | null>(null);
  const [referralOpportunityId, setReferralOpportunityId] = useState("");
  const [referralDiscoveryDialog, setReferralDiscoveryDialog] = useState<ReferralDiscoveryDialog | null>(null);
  const [messageTemplateScenario, setMessageTemplateScenario] = useState<MessageTemplateScenario>("");
  const [messageTemplateDraft, setMessageTemplateDraft] = useState("");
  const [messageTemplateOpeningId, setMessageTemplateOpeningId] = useState<string | null>(null);
  const [includeLinkedInRoleKeywords, setIncludeLinkedInRoleKeywords] = useState(false);
  const [prepDialog, setPrepDialog] = useState<PrepDialog | null>(null);
  const [taskDetailsTarget, setTaskDetailsTarget] = useState<TaskDetailsTarget | null>(null);
  const [storyDeleteTarget, setStoryDeleteTarget] = useState<{ milestone: PrepMilestone; trackName: string } | null>(null);
  const [prepStatusBusyId, setPrepStatusBusyId] = useState<string | null>(null);
  const [prepTrackOrderBusy, setPrepTrackOrderBusy] = useState(false);
  const [sprintCloseTarget, setSprintCloseTarget] = useState<PrepSprint | null>(null);
  const [sprintLifecycleBusy, setSprintLifecycleBusy] = useState(false);
  const [skillDialog, setSkillDialog] = useState<SkillDialog | null>(null);
  const [backlogDialog, setBacklogDialog] = useState<BacklogDialog | null>(null);
  const [skillAutomationBusy, setSkillAutomationBusy] = useState(false);
  const [liveExtractionReport, setLiveExtractionReport] = useState<LiveSkillExtractionResult | null>(null);
  const [linkedinImporting, setLinkedinImporting] = useState(false);
  const [selectedSkillObservationIds, setSelectedSkillObservationIds] = useState<string[]>([]);
  const [currentSkillReviewPage, setCurrentSkillReviewPage] = useState(1);
  const [marketFilters, setMarketFilters] = useState<MarketSignalFilters>(initialMarketFilters);
  const [marketSignals, setMarketSignals] = useState<MarketSignalOverview | null>(null);
  const [marketSignalLoading, setMarketSignalLoading] = useState(false);
  const [marketEvidenceSavingId, setMarketEvidenceSavingId] = useState<string | null>(null);
  const [currentMarketSignalPage, setCurrentMarketSignalPage] = useState(1);
  const [expandedMarketSkillId, setExpandedMarketSkillId] = useState<string | null>(null);
  const [marketEvidence, setMarketEvidence] = useState<Record<string, MarketEvidence[]>>({});
  const [marketSignalRefreshVersion, setMarketSignalRefreshVersion] = useState(0);
  const [detail, setDetail] = useState<OpeningDetail | null>(null);
  const [archiveTarget, setArchiveTarget] = useState<Opening | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [toast, setToast] = useState<Toast>(null);
  const [query, setQuery] = useState("");
  const [recommendation, setRecommendation] = useState("");
  const [resumeFilter, setResumeFilter] = useState("");
  const [dateFilter, setDateFilter] = useState("all");
  const [minScore, setMinScore] = useState("0");
  const [openingView, setOpeningView] = useState<"unapplied" | "active" | "archived">("unapplied");
  const [currentOpeningPage, setCurrentOpeningPage] = useState(1);
  const [outreachQuery, setOutreachQuery] = useState("");
  const [outreachStatusFilter, setOutreachStatusFilter] = useState<"ACTIVE" | "ALL" | OutreachStatus>("ACTIVE");
  const [outreachCompanyFilter, setOutreachCompanyFilter] = useState("");
  const [outreachTimingFilter, setOutreachTimingFilter] = useState<OutreachTimingFilter>("ALL");
  const [outreachSort, setOutreachSort] = useState<OutreachSort>("URGENCY");
  const [currentOutreachPage, setCurrentOutreachPage] = useState(1);
  const [outreachClock, setOutreachClock] = useState(() => Date.now());
  const [activePrepTrackId, setActivePrepTrackId] = useState<string | null>(null);
  const [applicationFollowUpTarget, setApplicationFollowUpTarget] = useState<ActionItem | null>(null);
  const [outreachFollowUpTarget, setOutreachFollowUpTarget] = useState<Outreach | null>(null);
  const [currentApplicationFollowUpPage, setCurrentApplicationFollowUpPage] = useState(1);

  const loadMarketSignals = useCallback(async () => {
    if (!connected) return;
    setMarketSignalLoading(true);
    try {
      setMarketSignals(await api<MarketSignalOverview>(`/api/v1/skills/market-signals?${marketQuery(marketFilters)}`));
    } catch (error) {
      setToast({ kind: "error", message: error instanceof Error ? error.message : "Could not load trusted market signals." });
    } finally { setMarketSignalLoading(false); }
  }, [connected, marketFilters]);

  const refresh = useCallback(async () => {
    try {
      const [nextDashboard, nextResumes, nextApplications, nextFeed, nextArchivedFeed, nextActions, nextImports, nextContacts, nextOutreach, nextPreparation, nextCandidates, nextSkills, nextBacklog, nextLinkedIn, nextInbox, nextWeeklyReviews, nextMonthlyProgress, nextCalendarEvents, nextCalendarReminders] = await Promise.all([
        api<Dashboard>("/api/v1/dashboard/morning"),
        api<ResumeVariant[]>("/api/v1/resumes"),
        api<ApplicationRecord[]>("/api/v1/applications"),
        api<OpeningFeed>("/api/v1/opportunities/intelligence"),
        api<OpeningFeed>("/api/v1/opportunities/intelligence?archived=true"),
        api<DailyActionDay>("/api/v1/daily-actions"),
        api<ImportBatch[]>("/api/v1/imports"),
        api<Contact[]>("/api/v1/contacts"),
        api<Outreach[]>("/api/v1/outreach"),
        api<PreparationOverview>("/api/v1/preparation/overview"),
        api<ReferralCandidate[]>("/api/v1/referral-candidates"),
        api<SkillOverview>("/api/v1/skills/overview"),
        api<PersonalSkillBacklogOverview>("/api/v1/skills/backlog"),
        api<LinkedInOverview>("/api/v1/linkedin-connections/overview"),
        api<InboxItem[]>("/api/v1/inbox/items"),
        api<WeeklyReviewOverview>("/api/v1/reviews/weekly"),
        api<MonthlyProgress>("/api/v1/reviews/weekly/monthly"),
        api<CalendarEvent[]>("/api/v1/calendar/events"),
        api<CalendarEvent[]>("/api/v1/calendar/reminders/due"),
      ]);
      setDashboard(nextDashboard); setResumes(nextResumes); setApplications(nextApplications); setFeed(nextFeed); setArchivedFeed(nextArchivedFeed);
      setDailyActions(nextActions); setImports(nextImports); setContacts(nextContacts);
      setOutreach(nextOutreach); setConnected(true);
      setPreparation(nextPreparation);
      setReferralCandidates(nextCandidates);
      setSkillOverview(nextSkills);
      setPersonalBacklog(nextBacklog);
      setLinkedInOverview(nextLinkedIn);
      setInboxItems(nextInbox);
      setWeeklyReviews(nextWeeklyReviews);
      setMonthlyProgress(nextMonthlyProgress);
      setCalendarEvents(nextCalendarEvents);
      setDueCalendarReminders(nextCalendarReminders);
    } catch { setConnected(false); } finally { setLoading(false); }
  }, []);

  useEffect(() => {
    const timer = window.setTimeout(() => void refresh(), 0);
    return () => window.clearTimeout(timer);
  }, [refresh]);
  useEffect(() => {
    const updateFromHash = () => setActiveWorkspace(workspaceFromHash(window.location.hash));
    updateFromHash();
    window.addEventListener("hashchange", updateFromHash);
    return () => window.removeEventListener("hashchange", updateFromHash);
  }, []);
  useEffect(() => {
    if (!toast) return;
    const timer = window.setTimeout(() => setToast(null), 4200);
    return () => window.clearTimeout(timer);
  }, [toast]);
  useEffect(() => {
    const timer = window.setInterval(() => setOutreachClock(Date.now()), 60_000);
    return () => window.clearInterval(timer);
  }, []);
  useEffect(() => {
    if (!connected) return;
    const timer = window.setInterval(() => {
      void api<CalendarEvent[]>("/api/v1/calendar/reminders/due").then(setDueCalendarReminders).catch(() => undefined);
    }, 60_000);
    return () => window.clearInterval(timer);
  }, [connected]);
  useEffect(() => {
    if (!connected) return;
    const timer = window.setTimeout(() => void loadMarketSignals(), 0);
    return () => window.clearTimeout(timer);
  }, [connected, loadMarketSignals, marketSignalRefreshVersion]);

  const dailyMotivation = dailyMotivations[dayIndex(dashboard.date) % dailyMotivations.length];
  const preparationMotivation = preparationMotivations[dayIndex(dashboard.date) % preparationMotivations.length];
  const weeklyApplicationProgress = Math.min(100, Math.round((dashboard.appliedThisWeek / WEEKLY_APPLICATION_TARGET) * 100));
  const weeklyApplicationsRemaining = Math.max(0, WEEKLY_APPLICATION_TARGET - dashboard.appliedThisWeek);

  const displayFeed = openingView === "archived" ? archivedFeed : feed;
  const visibleOpenings = useMemo(() => {
    const needle = query.trim().toLowerCase();
    const threshold = Number(minScore) || 0;
    return displayFeed.openings.filter((opening) => {
      const searchable = `${opening.companyName} ${opening.roleTitle} ${opening.location ?? ""} ${opening.roleSummary} ${opening.fitRationale}`.toLowerCase();
      return (!needle || searchable.includes(needle))
        && (openingView !== "unapplied" || !opening.applicationId)
        && (!recommendation || openingRecommendation(opening) === recommendation)
        && (!resumeFilter || opening.recommendedResumeVariant === resumeFilter)
        && (dateFilter === "all"
          || (dateFilter === "latest" && opening.observedOn === displayFeed.latestObservationDate)
          || opening.observedOn === dateFilter)
        && opening.weightedTotal >= threshold;
    });
  }, [dateFilter, displayFeed, minScore, openingView, query, recommendation, resumeFilter]);
  const openingPageCount = Math.max(1, Math.ceil(visibleOpenings.length / OPENINGS_PER_PAGE));
  const effectiveOpeningPage = Math.min(currentOpeningPage, openingPageCount);
  const pagedOpenings = visibleOpenings.slice(
    (effectiveOpeningPage - 1) * OPENINGS_PER_PAGE, effectiveOpeningPage * OPENINGS_PER_PAGE,
  );
  const recommendationOptions = Array.from(new Set(displayFeed.openings.map(openingRecommendation))).sort();
  const openingRangeStart = visibleOpenings.length === 0 ? 0 : (effectiveOpeningPage - 1) * OPENINGS_PER_PAGE + 1;
  const openingRangeEnd = Math.min(effectiveOpeningPage * OPENINGS_PER_PAGE, visibleOpenings.length);
  const applicationFollowUpPageCount = Math.max(1, Math.ceil(dashboard.actions.length / APPLICATION_FOLLOWUPS_PER_PAGE));
  const effectiveApplicationFollowUpPage = Math.min(currentApplicationFollowUpPage, applicationFollowUpPageCount);
  const pagedApplicationFollowUps = dashboard.actions.slice(
    (effectiveApplicationFollowUpPage - 1) * APPLICATION_FOLLOWUPS_PER_PAGE,
    effectiveApplicationFollowUpPage * APPLICATION_FOLLOWUPS_PER_PAGE,
  );
  const applicationFollowUpRangeStart = dashboard.actions.length === 0 ? 0
    : (effectiveApplicationFollowUpPage - 1) * APPLICATION_FOLLOWUPS_PER_PAGE + 1;
  const applicationFollowUpRangeEnd = Math.min(
    effectiveApplicationFollowUpPage * APPLICATION_FOLLOWUPS_PER_PAGE, dashboard.actions.length,
  );

  const todoCount = dailyActions.actions.filter((action) => action.status === "TODO").length;
  const dueOutreach = outreach.filter((item) => item.overdue).length;
  const todayFocusPlan = dailyFocusPlanForDate(dashboard.date);
  const availablePreparationTasks = flattenPreparationTasks(preparation.tracks)
    .filter(({ item }) => !["COMPLETED", "SKIPPED"].includes(item.status));
  const nextCodingTask = availablePreparationTasks.find(({ item, track, milestone }) =>
    /(coding|algorithm|data structure|problem|binary search|subsets)/i.test(`${item.title} ${item.description ?? ""} ${track.name} ${milestone.title}`));
  const nextFocusTask = availablePreparationTasks.find(({ item, track, milestone }) =>
    todayFocusPlan.pattern.test(`${item.title} ${item.description ?? ""} ${track.name} ${milestone.title}`));
  const nextOpportunityAction = [...dailyActions.actions].filter((action) => action.status === "TODO")
    .sort((left, right) => left.priorityRank - right.priorityRank)[0] ?? null;
  const topDailyPriorities = [
    { index: "01", label: "Daily anchor", time: "5:00–7:30 AM", title: "Coding practice",
      detail: nextCodingTask ? `${nextCodingTask.item.title} · ${nextCodingTask.track.name}` : "Timed problem solving, implementation, edge cases, and complexity review." },
    { index: "02", label: "Daily anchor", time: "8:30–10:50 AM", title: "Opportunity execution",
      detail: nextOpportunityAction ? nextOpportunityAction.actionText : "Tailor and submit the strongest application-ready opening." },
    { index: "03", label: todayFocusPlan.label, time: "12:30–2:25 PM", title: todayFocusPlan.title,
      detail: nextFocusTask ? `${nextFocusTask.item.title} · ${nextFocusTask.track.name}` : todayFocusPlan.description },
  ];
  const technologyWatch = technologyWatchItems[dayIndex(dashboard.date) % technologyWatchItems.length];
  const topMarketSignal = [...(marketSignals?.signals ?? [])]
    .sort((left, right) => right.currentOpeningCount - left.currentOpeningCount)[0] ?? null;
  const coachRecommendation = dueOutreach > 0
    ? `Close the loop on ${dueOutreach} overdue outreach follow-up${dueOutreach === 1 ? "" : "s"} before starting new networking. Protect the coding block, then use the opportunity block for the strongest warm path.`
    : dashboard.actions.length > 0
      ? `${dashboard.actions.length} application follow-up${dashboard.actions.length === 1 ? " is" : "s are"} due soon. Handle the highest-conversion conversation first, then return to today’s ${todayFocusPlan.label.toLowerCase()} block.`
      : nextOpportunityAction
        ? `Complete today’s highest-ranked opening action before expanding the shortlist: ${nextOpportunityAction.actionText}`
        : `Protect both daily anchors, then make one measurable ${todayFocusPlan.label.toLowerCase()} deliverable the definition of done for today.`;
  const latestSuccessfulImport = imports.find((item) => item.status === "COMPLETED") ?? null;
  const summaryCalendarEvents = useMemo(() => nearTermCalendarEvents(calendarEvents)
    .filter((event) => !dismissedCalendarNoticeKeys.includes(calendarNoticeKey(event))), [calendarEvents, dismissedCalendarNoticeKeys]);
  const inboxReviewCount = inboxItems.flatMap((item) => item.candidates)
    .filter((candidate) => ["NEEDS_REVIEW", "DUPLICATE_REVIEW"].includes(candidate.status)).length;
  const inboxDuplicateCount = inboxItems.flatMap((item) => item.candidates)
    .filter((candidate) => candidate.status === "DUPLICATE_REVIEW").length;
  const selectedWeeklyReview = weeklyReviews?.reviews.find((review) => review.id === selectedWeeklyReviewId) ?? null;
  const displayedWeeklyMetric = selectedWeeklyReview?.metrics ?? weeklyReviews?.livePreview ?? null;
  const currentWeeklySnapshot = weeklyReviews?.reviews.find((review) => review.weekStart === weeklyReviews.livePreview.weekStart) ?? null;
  const activeOutreach = outreach.filter((item) => !["CLOSED", "DECLINED"].includes(item.status));
  const outreachCompanies = useMemo(() => Array.from(new Set(outreach.map((item) => item.companyName))).sort(), [outreach]);
  const visibleOutreach = useMemo(() => {
    const needle = outreachQuery.trim().toLowerCase();
    const now = outreachClock;
    const sevenDaysFromNow = now + 7 * 24 * 60 * 60 * 1000;
    return outreach.filter((item) => {
      const searchable = `${item.contactName} ${item.contactCompany ?? ""} ${item.companyName} ${item.roleTitle} ${item.messageSummary ?? ""}`.toLowerCase();
      const followUpTime = item.followUpAt ? Date.parse(item.followUpAt) : null;
      return (!needle || searchable.includes(needle))
        && (outreachStatusFilter === "ALL"
          || (outreachStatusFilter === "ACTIVE" && !["CLOSED", "DECLINED"].includes(item.status))
          || item.status === outreachStatusFilter)
        && (!outreachCompanyFilter || item.companyName === outreachCompanyFilter)
        && (outreachTimingFilter === "ALL"
          || (outreachTimingFilter === "OVERDUE" && item.overdue)
          || (outreachTimingFilter === "NEXT_SEVEN_DAYS" && followUpTime !== null && !item.overdue && followUpTime <= sevenDaysFromNow)
          || (outreachTimingFilter === "UNSCHEDULED" && followUpTime === null));
    }).sort((left, right) => compareOutreach(left, right, outreachSort));
  }, [outreach, outreachClock, outreachCompanyFilter, outreachQuery, outreachSort, outreachStatusFilter, outreachTimingFilter]);
  const outreachPageCount = Math.max(1, Math.ceil(visibleOutreach.length / OUTREACH_PER_PAGE));
  const effectiveOutreachPage = Math.min(currentOutreachPage, outreachPageCount);
  const pagedOutreach = visibleOutreach.slice(
    (effectiveOutreachPage - 1) * OUTREACH_PER_PAGE, effectiveOutreachPage * OUTREACH_PER_PAGE,
  );
  const outreachRangeStart = visibleOutreach.length === 0 ? 0 : (effectiveOutreachPage - 1) * OUTREACH_PER_PAGE + 1;
  const outreachRangeEnd = Math.min(effectiveOutreachPage * OUTREACH_PER_PAGE, visibleOutreach.length);
  const referralOpening = feed.openings.find((opening) => opening.opportunityId === referralOpportunityId) ?? feed.openings[0] ?? null;
  const referralOpeningId = referralOpening?.opportunityId ?? null;
  const openingCandidates = referralCandidates.filter((candidate) => candidate.opportunityId === referralOpening?.opportunityId && candidate.status !== "DISMISSED");
  const matchingContacts = contacts.filter((contact) => referralOpening && contact.companyName?.toLowerCase() === referralOpening.companyName.toLowerCase());
  const proposedSkillObservations = skillOverview.observations.filter((item) => item.reviewStatus === "PROPOSED");
  const skillReviewPageCount = Math.max(1, Math.ceil(proposedSkillObservations.length / SKILL_REVIEW_PER_PAGE));
  const effectiveSkillReviewPage = Math.min(currentSkillReviewPage, skillReviewPageCount);
  const pagedSkillObservations = proposedSkillObservations.slice(
    (effectiveSkillReviewPage - 1) * SKILL_REVIEW_PER_PAGE, effectiveSkillReviewPage * SKILL_REVIEW_PER_PAGE,
  );
  const marketSignalPageCount = Math.max(1, Math.ceil((marketSignals?.signals.length ?? 0) / MARKET_SIGNALS_PER_PAGE));
  const effectiveMarketSignalPage = Math.min(currentMarketSignalPage, marketSignalPageCount);
  const pagedMarketSignals = (marketSignals?.signals ?? []).slice(
    (effectiveMarketSignalPage - 1) * MARKET_SIGNALS_PER_PAGE, effectiveMarketSignalPage * MARKET_SIGNALS_PER_PAGE,
  );
  const activePrepTrack = preparation.tracks.find((track) => track.id === activePrepTrackId) ?? null;

  useEffect(() => {
    if (!connected || !referralOpeningId) return;
    let active = true;
    void api<LinkedInConnection[]>(`/api/v1/linkedin-connections/matches?opportunityId=${referralOpeningId}`)
      .then((matches) => { if (active) setLinkedInMatches(matches); })
      .catch(() => { if (active) setLinkedInMatches([]); });
    return () => { active = false; };
  }, [connected, referralOpeningId]);
  // Reset opening-specific drafts before rendering a different opportunity.
  if (messageTemplateOpeningId !== referralOpeningId) {
    setMessageTemplateOpeningId(referralOpeningId);
    setMessageTemplateScenario("");
    setMessageTemplateDraft("");
  }

  async function copyMessageTemplate() {
    if (!messageTemplateDraft.trim()) return;
    try {
      await navigator.clipboard.writeText(messageTemplateDraft);
      setToast({ kind: "success", message: "Message template copied. Personalize the remaining placeholders before sending." });
    } catch {
      setToast({ kind: "error", message: "Could not copy the message. Select the text and copy it manually." });
    }
  }

  function updateMarketFilter<Key extends keyof MarketSignalFilters>(key: Key, value: MarketSignalFilters[Key]) {
    setMarketFilters((current) => ({ ...current, [key]: value }));
    setCurrentMarketSignalPage(1);
    setExpandedMarketSkillId(null);
    setMarketEvidence({});
  }

  async function toggleMarketEvidence(skillId: string) {
    if (expandedMarketSkillId === skillId) {
      setExpandedMarketSkillId(null);
      return;
    }
    setExpandedMarketSkillId(skillId);
    if (marketEvidence[skillId]) return;
    try {
      const result = await api<MarketEvidenceDrilldown>(`/api/v1/skills/market-signals/${skillId}/evidence?${marketQuery(marketFilters)}`);
      setMarketEvidence((current) => ({ ...current, [skillId]: result.evidence }));
    } catch (error) {
      setToast({ kind: "error", message: error instanceof Error ? error.message : "Could not load source evidence." });
    }
  }

  async function updateMarketEvidenceStrength(item: MarketEvidence, strength: SkillStrength) {
    if (item.strength === strength || marketEvidenceSavingId) return;
    setMarketEvidenceSavingId(item.observationId);
    try {
      await api(`/api/v1/skills/observations/${item.observationId}/correction`, { method: "PATCH", body: JSON.stringify({
        skillId: item.skillId,
        strength,
        evidenceSnippet: item.evidenceSnippet,
        note: `Demand strength corrected from ${formatEnum(item.strength)} to ${formatEnum(strength)} in Cohort market signals.`,
      }) });
      const result = await api<MarketEvidenceDrilldown>(`/api/v1/skills/market-signals/${item.skillId}/evidence?${marketQuery(marketFilters)}`);
      setMarketEvidence((current) => ({ ...current, [item.skillId]: result.evidence }));
      setMarketSignalRefreshVersion((current) => current + 1);
      setToast({ kind: "success", message: `${item.skillName} is now ${formatEnum(strength).toLowerCase()} for ${item.companyName}. Cohort totals were recalculated.` });
    } catch (error) {
      setToast({ kind: "error", message: error instanceof Error ? error.message : "Could not update the evidence strength." });
    } finally { setMarketEvidenceSavingId(null); }
  }

  async function syncPersonalSkillBacklog() {
    setSkillAutomationBusy(true);
    try {
      const result = await api<{ preparationItemsScanned: number; backlogItemsCreated: number; linksCreated: number; backlogSize: number }>(
        "/api/v1/skills/backlog/sync-preparation", { method: "POST" },
      );
      setToast({ kind: "success", message: `Personal backlog synchronized: ${result.preparationItemsScanned} preparation goals retained, ${result.linksCreated} new links added.` });
      await refresh();
    } catch (error) {
      setToast({ kind: "error", message: error instanceof Error ? error.message : "Could not synchronize the personal skill backlog." });
    } finally { setSkillAutomationBusy(false); }
  }

  async function addCanonicalSkillToBacklog(skill: SkillView) {
    try {
      await api(`/api/v1/skills/backlog/skills/${skill.id}`, { method: "PUT", body: JSON.stringify({
        currentLevel: "NOT_ASSESSED", targetLevel: "WORKING_PROFICIENCY", priority: 3,
        rationale: "User-selected capability retained for preparation.", status: "ACTIVE", nextReviewOn: null,
      }) });
      setToast({ kind: "success", message: `${skill.name} was added to the personal preparation backlog.` });
      await refresh();
    } catch (error) { setToast({ kind: "error", message: error instanceof Error ? error.message : "Could not add the skill plan." }); }
  }

  async function syncFiles() {
    if (!connected) return;
    setSyncing(true);
    try {
      const result = await api<ImportRun>("/api/v1/imports/daily-high-fit", { method: "POST" });
      const failure = result.files?.find((file) => file.result === "FAILED");
      setToast({ kind: result.filesFailed > 0 ? "error" : "success", message: result.filesFailed > 0
        ? `${result.filesFailed} file(s) failed to import; ${result.filesImported} imported, ${result.filesUnchanged} unchanged.${failure ? ` ${failure.file}: ${(failure.error ?? "Check the workbook format.").slice(0, 180)}` : " Check the workbook format before retrying."}`
        : result.filesImported > 0
        ? `Imported ${result.filesImported} changed file${result.filesImported === 1 ? "" : "s"}.`
        : "All daily files are already up to date." });
      await refresh();
    } catch (error) {
      setToast({ kind: "error", message: error instanceof Error ? error.message : "Could not sync the daily files." });
    } finally { setSyncing(false); }
  }

  async function importInboxCandidate(candidate: InboxCandidate) {
    setInboxBusyId(candidate.id);
    try {
      await api(`/api/v1/inbox/candidates/${candidate.id}/import`, { method: "POST" });
      setToast({ kind: "success", message: `${candidate.companyName ?? "The candidate"} was added to High-fit openings for review.` });
      await refresh();
    } catch (error) {
      setToast({ kind: "error", message: error instanceof Error ? error.message : "Could not import the inbox candidate." });
    } finally { setInboxBusyId(null); }
  }

  async function rejectInboxCandidate(candidate: InboxCandidate) {
    setInboxBusyId(candidate.id);
    try {
      await api(`/api/v1/inbox/candidates/${candidate.id}/reject`, { method: "POST" });
      setToast({ kind: "success", message: "The inbox candidate was rejected; its source provenance was retained." });
      await refresh();
    } catch (error) {
      setToast({ kind: "error", message: error instanceof Error ? error.message : "Could not reject the inbox candidate." });
    } finally { setInboxBusyId(null); }
  }

  async function scanInboxDuplicates() {
    setDuplicateScanBusy(true);
    try {
      const report = await api<{ candidatesScanned: number; matchesCreated: number; candidatesFlagged: number }>("/api/v1/inbox/duplicates/scan", { method: "POST" });
      setToast({ kind: "success", message: report.candidatesFlagged > 0
        ? `${report.candidatesFlagged} candidate${report.candidatesFlagged === 1 ? "" : "s"} need duplicate review.`
        : `Checked ${report.candidatesScanned} candidate${report.candidatesScanned === 1 ? "" : "s"}; no unresolved duplicate evidence found.` });
      await refresh();
    } catch (error) {
      setToast({ kind: "error", message: error instanceof Error ? error.message : "Could not scan the inbox for duplicates." });
    } finally { setDuplicateScanBusy(false); }
  }

  async function generateWeeklyReview() {
    if (!weeklyReviews) return;
    setWeeklyReviewBusy(true);
    try {
      const result = await api<{ replayed: boolean; review: WeeklyReview }>("/api/v1/reviews/weekly", {
        method: "POST", body: JSON.stringify({ weekStart: weeklyReviews.livePreview.weekStart }),
      });
      setSelectedWeeklyReviewId(result.review.id);
      setToast({ kind: "success", message: result.replayed
        ? "This week already has an immutable snapshot; the existing review is selected."
        : `Weekly metrics were frozen through ${formatDate(result.review.effectiveThrough)}.` });
      await refresh();
    } catch (error) {
      setToast({ kind: "error", message: error instanceof Error ? error.message : "Could not create the weekly snapshot." });
    } finally { setWeeklyReviewBusy(false); }
  }

  async function loadMonthlyProgress(monthStart: string) {
    setMonthlyProgressLoading(true);
    try {
      setMonthlyProgress(await api<MonthlyProgress>(`/api/v1/reviews/weekly/monthly?month=${monthStart}`));
    } catch (error) {
      setToast({ kind: "error", message: error instanceof Error ? error.message : "Could not load the monthly progress calendar." });
    } finally { setMonthlyProgressLoading(false); }
  }

  async function saveWeeklyReflection(event: FormEvent) {
    event.preventDefault();
    if (!weeklyReviews || (!weeklyWorked.trim() && !weeklyImprove.trim())) return;
    setWeeklyReflectionSaving(true);
    try {
      let review = selectedWeeklyReview;
      if (!review) {
        const generated = await api<{ replayed: boolean; review: WeeklyReview }>("/api/v1/reviews/weekly", {
          method: "POST", body: JSON.stringify({ weekStart: weeklyReviews.livePreview.weekStart }),
        });
        review = generated.review;
      }
      await api(`/api/v1/reviews/weekly/${review.id}/revisions`, { method: "POST", body: JSON.stringify({
        wins: weeklyWorked, challenges: null, reflection: null, nextWeekAdjustments: weeklyImprove, nextWeekFocus: null,
      }) });
      setSelectedWeeklyReviewId(review.id); setWeeklyWorked(""); setWeeklyImprove("");
      setToast({ kind: "success", message: "Weekly reflection preserved with this week’s evidence." });
      await refresh();
    } catch (error) {
      setToast({ kind: "error", message: error instanceof Error ? error.message : "Could not preserve the weekly reflection." });
    } finally { setWeeklyReflectionSaving(false); }
  }

  async function seedSkillTaxonomy() {
    if (!connected) return;
    setSkillAutomationBusy(true);
    try {
      const result = await api<SkillSeedResult>("/api/v1/skills/seed", { method: "POST" });
      setToast({ kind: "success", message: result.skillsCreated > 0
        ? `Seeded ${result.skillsCreated} canonical skills and ${result.aliasesCreated} reviewed aliases.`
        : `The ${result.catalogSize}-skill taxonomy is already up to date.` });
      await refresh();
    } catch (error) {
      setToast({ kind: "error", message: error instanceof Error ? error.message : "Could not seed the skill taxonomy." });
    } finally { setSkillAutomationBusy(false); }
  }

  async function extractSkillEvidence() {
    if (!connected || skillOverview.catalogSize === 0) return;
    setSkillAutomationBusy(true);
    try {
      const result = await api<LiveSkillExtractionResult>("/api/v1/skills/live-extractions", {
        method: "POST", body: JSON.stringify({ opportunityIds: [] }),
      });
      setLiveExtractionReport(result);
      setToast({ kind: result.pagesFetched > 0 ? "success" : "error", message: result.pagesFetched > 0
        ? `Fetched ${result.pagesFetched} of ${result.opportunitiesEligible} live job pages and created ${result.observationsCreated} reviewable signals.${result.fetchFailures > 0 ? ` ${result.fetchFailures} pages need manual evidence capture.` : ""}`
        : `No live descriptions could be retrieved from ${result.opportunitiesEligible} eligible openings. Use Capture evidence for blocked or expired pages.` });
      setCurrentSkillReviewPage(1);
      await refresh();
    } catch (error) {
      setToast({ kind: "error", message: error instanceof Error ? error.message : "Could not extract skill evidence." });
    } finally { setSkillAutomationBusy(false); }
  }

  async function reviewSelectedSkillObservations(status: Exclude<SkillReviewStatus, "PROPOSED">) {
    if (selectedSkillObservationIds.length === 0) return;
    try {
      await api("/api/v1/skills/observations/review", { method: "PATCH", body: JSON.stringify({
        observationIds: selectedSkillObservationIds, status,
        note: status === "ACCEPTED" ? "Validated in the Milestone 3B review queue." : "Excluded during Milestone 3B review.",
      }) });
      setToast({ kind: "success", message: `${selectedSkillObservationIds.length} skill signals ${status === "ACCEPTED" ? "accepted" : "rejected"}.` });
      setSelectedSkillObservationIds([]);
      await refresh();
      setMarketSignalRefreshVersion((current) => current + 1);
    } catch (error) {
      setToast({ kind: "error", message: error instanceof Error ? error.message : "Could not review the selected evidence." });
    }
  }

  async function importLinkedInConnections() {
    if (!connected) return;
    setLinkedinImporting(true);
    try {
      const result = await api<LinkedInImport>("/api/v1/linkedin-connections/imports", { method: "POST" });
      if (result.status === "FAILED") throw new Error(result.errorMessage ?? "LinkedIn import failed.");
      setToast({ kind: "success", message: result.replayed
        ? `LinkedIn connections are current; ${result.rowsSeen} rows were already imported.`
        : `Imported ${result.connectionsCreated} new and refreshed ${result.connectionsUpdated} LinkedIn connections.` });
      await refresh();
      if (referralOpening) {
        setLinkedInMatches(await api<LinkedInConnection[]>(`/api/v1/linkedin-connections/matches?opportunityId=${referralOpening.opportunityId}`));
      }
    } catch (error) {
      setToast({ kind: "error", message: error instanceof Error ? error.message : "Could not import LinkedIn connections." });
    } finally { setLinkedinImporting(false); }
  }

  async function saveLinkedInMatch(connection: LinkedInConnection) {
    if (!referralOpening) return;
    try {
      await api(`/api/v1/linkedin-connections/${connection.id}/candidates`, { method: "POST", body: JSON.stringify({
        opportunityId: referralOpening.opportunityId,
      }) });
      setToast({ kind: "success", message: `${connection.fullName} was added as a first-degree referral path.` });
      await refresh();
    } catch (error) {
      setToast({ kind: "error", message: error instanceof Error ? error.message : "Could not save the LinkedIn referral path." });
    }
  }

  async function toggleDailyAction(action: DailyAction) {
    if (!connected) return;
    try {
      await api(`/api/v1/daily-actions/${action.id}`, { method: "PATCH", body: JSON.stringify({
        status: action.status === "DONE" ? "TODO" : "DONE",
      }) });
      await refresh();
    } catch (error) {
      setToast({ kind: "error", message: error instanceof Error ? error.message : "Could not update the action." });
    }
  }

  async function showDetail(opening: Opening) {
    if (!connected) return;
    setDetailLoading(true);
    try { setDetail(await api<OpeningDetail>(`/api/v1/opportunities/${opening.opportunityId}/intelligence`)); }
    catch (error) { setToast({ kind: "error", message: error instanceof Error ? error.message : "Could not load opening details." }); }
    finally { setDetailLoading(false); }
  }

  async function restoreOpening(opening: Opening) {
    if (!connected) return;
    try {
      await api(`/api/v1/opportunities/${opening.opportunityId}/restore`, { method: "POST" });
      setToast({ kind: "success", message: `${opening.companyName} was restored to your active openings.` });
      await refresh();
    } catch (error) {
      setToast({ kind: "error", message: error instanceof Error ? error.message : "Could not restore the opening." });
    }
  }

  function startFromOpening(opening: Opening) {
    setDetail(null);
    setApplicationTarget({ opportunity: openingToOpportunity(opening), preferredResumeName: opening.recommendedResumeVariant });
  }

  function startReferralDiscovery(opening: Opening) {
    setReferralOpportunityId(opening.opportunityId);
    window.location.hash = "outreach";
    setActiveWorkspace("outreach");
    window.setTimeout(() => window.scrollTo({ top: 0, behavior: "smooth" }), 0);
  }

  async function advanceOutreach(item: Outreach) {
    const next = nextOutreachStatus(item.status);
    if (!connected || !next) return;
    try {
      await api(`/api/v1/outreach/${item.id}`, { method: "PATCH", body: JSON.stringify({
        status: next,
        followUpAt: next === "SENT" ? hoursFromNowIso(72) : null,
      }) });
      setToast({ kind: "success", message: `Referral activity moved to ${formatEnum(next).toLowerCase()}.` });
      await refresh();
    } catch (error) {
      setToast({ kind: "error", message: error instanceof Error ? error.message : "Could not update outreach." });
    }
  }

  async function updatePrepItemStatus(item: PrepItem, status: PrepItemStatus) {
    setPrepStatusBusyId(item.id);
    try {
      await api(`/api/v1/preparation/items/${item.id}`, { method: "PATCH", body: JSON.stringify({ status }) });
      setToast({ kind: "success", message: `“${item.title}” moved to ${sprintStageLabel(status).toLowerCase()}.` }); await refresh();
    } catch (error) { setToast({ kind: "error", message: error instanceof Error ? error.message : "Could not update the preparation task." }); }
    finally { setPrepStatusBusyId(null); }
  }

  async function addPrepItemToCurrentSprint(item: PrepItem) {
    setPrepStatusBusyId(item.id);
    try {
      await api(`/api/v1/preparation/sprints/current/items/${item.id}`, { method: "POST" });
      setToast({ kind: "success", message: `“${item.title}” was added to the current sprint.` }); await refresh();
    } catch (error) { setToast({ kind: "error", message: error instanceof Error ? error.message : "Could not add the task to the sprint." }); }
    finally { setPrepStatusBusyId(null); }
  }

  async function startPreparationSprint() {
    setSprintLifecycleBusy(true);
    try {
      await api(`/api/v1/preparation/sprints`, { method: "POST" });
      setToast({ kind: "success", message: "A new two-week sprint is active. Add the next backlog tasks when you are ready." });
      await refresh();
    } catch (error) { setToast({ kind: "error", message: error instanceof Error ? error.message : "Could not start the sprint." }); }
    finally { setSprintLifecycleBusy(false); }
  }

  async function closePreparationSprint(sprint: PrepSprint) {
    setSprintLifecycleBusy(true);
    try {
      await api(`/api/v1/preparation/sprints/${sprint.id}/close`, { method: "POST" });
      setSprintCloseTarget(null);
      setToast({ kind: "success", message: "Sprint closed. Open work returned to backlog; in-progress and review work kept its state." });
      await refresh();
    } catch (error) { setToast({ kind: "error", message: error instanceof Error ? error.message : "Could not close the sprint." }); }
    finally { setSprintLifecycleBusy(false); }
  }

  async function updatePrepItemPriority(item: PrepItem, priority: number) {
    setPrepStatusBusyId(item.id);
    try {
      await api(`/api/v1/preparation/items/${item.id}`, { method: "PATCH", body: JSON.stringify({ priority }) });
      setToast({ kind: "success", message: `“${item.title}” is now priority ${priority}.` }); await refresh();
    } catch (error) { setToast({ kind: "error", message: error instanceof Error ? error.message : "Could not update the task priority." }); }
    finally { setPrepStatusBusyId(null); }
  }

  async function reorderPreparationTracks(trackIds: string[]) {
    setPrepTrackOrderBusy(true);
    try {
      await api("/api/v1/preparation/tracks/order", { method: "PUT", body: JSON.stringify({ trackIds }) });
      setToast({ kind: "success", message: "Preparation track order saved." });
      await refresh();
    } catch (error) { setToast({ kind: "error", message: error instanceof Error ? error.message : "Could not save the track order." }); }
    finally { setPrepTrackOrderBusy(false); }
  }

  async function reviewSkillObservation(item: SkillObservation, status: Exclude<SkillReviewStatus, "PROPOSED">) {
    try {
      await api(`/api/v1/skills/observations/${item.id}/review`, { method: "PATCH", body: JSON.stringify({
        status, note: status === "ACCEPTED" ? "Verified against the captured job-description evidence." : "Excluded during human review.",
      }) });
      setToast({ kind: "success", message: `${item.skillName} evidence ${status === "ACCEPTED" ? "accepted" : "rejected"}.` });
      await refresh();
      setMarketSignalRefreshVersion((current) => current + 1);
    } catch (error) {
      setToast({ kind: "error", message: error instanceof Error ? error.message : "Could not review the skill evidence." });
    }
  }

  function dismissSummaryCalendarEvents() {
    const nextKeys = Array.from(new Set([...dismissedCalendarNoticeKeys, ...summaryCalendarEvents.map(calendarNoticeKey)]));
    setDismissedCalendarNoticeKeys(nextKeys);
    try { window.localStorage.setItem(CALENDAR_NOTICE_STORAGE_KEY, JSON.stringify(nextKeys)); }
    catch { /* The notice still remains dismissed for this session if browser storage is unavailable. */ }
  }

  return (
    <main className="app-shell">
      <header className="top-navigation">
        <a className="brand-mark" aria-label="Job Search Command Center home" href="#overview">
          <span className="brand-glyph">J</span>
          <span className="brand-copy"><strong>Job Search</strong><small>Command Center</small></span>
        </a>
        <nav className="top-workspace-nav" aria-label="Primary navigation">
          {workspaceViews.map((view) => <a className={`top-nav-link ${activeWorkspace === view.id ? "active" : ""}`} href={`#${view.id}`}
            aria-current={activeWorkspace === view.id ? "page" : undefined} title={view.description} key={view.id}>
            <span className="top-nav-index">{view.index}</span><strong className="top-nav-full">{view.label}</strong><strong className="top-nav-short">{view.shortLabel}</strong>
          </a>)}
        </nav>
        <div className="topbar-actions">
          <div className="connection-status" title={connected ? "PostgreSQL is the local source of truth" : "Start the API to save changes"}>
            <span className={`connection-dot ${connected ? "online" : ""}`} /><strong>{connected ? "Connected" : "Preview"}</strong>
          </div>
          <button className="calendar-nav-button" type="button" onClick={() => setCalendarOpen(true)} aria-label="Open personal calendar">
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M7 3v3M17 3v3M4.5 8.5h15M6 5h12a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2Z" /></svg>
            <span>Calendar</span>{dueCalendarReminders.length > 0 && <b>{dueCalendarReminders.length}</b>}
          </button>
          {activeWorkspace === "overview" && summaryCalendarEvents.length > 0 && <CalendarNoticePopover events={summaryCalendarEvents}
            onOpen={() => setCalendarOpen(true)} onDismiss={dismissSummaryCalendarEvents} />}
            {activeWorkspace === "opportunities" && <button className="secondary-button sync-button" disabled={!connected || syncing} onClick={() => void syncFiles()}>
              {syncing ? "Syncing…" : "↻ Sync daily files"}
            </button>}
            {activeWorkspace === "opportunities" && <button className="primary-button" onClick={() => setOpportunityModal(true)}>+ Add opportunity</button>}
            {activeWorkspace === "preparation" && !activePrepTrack && <button className="primary-button" disabled={!connected} onClick={() => setPrepDialog({ kind: "track" })}>+ Add track</button>}
        </div>
      </header>

      <section className="workspace">
        <div className="content-wrap">
          {activeWorkspace === "overview" && <div className="workspace-page overview-page" id="overview">
          <section className="hero">
            <div className="hero-greeting"><p className="eyebrow">Morning command center</p><h1>Good morning, there.</h1>
              <div className="daily-motivation"><span>Daily perspective</span><blockquote>“{dailyMotivation}”</blockquote></div></div>
            <aside className="daily-briefing" aria-label="Personalized daily recommendations">
              <header><div><span>Coach’s briefing</span><strong>Recommended next move</strong></div><em>Live workspace</em></header>
              <p>{coachRecommendation}</p>
              <div className="briefing-signals">
                <article><span>Market lens</span><strong>{topMarketSignal
                  ? `${topMarketSignal.skillName} · ${topMarketSignal.currentOpeningCount} of ${marketSignals?.currentSampleSize ?? 0} reviewed openings`
                  : "Refresh full job descriptions to update demand"}</strong></article>
                <article><span>{technologyWatch.theme}</span><strong>{technologyWatch.title}</strong></article>
              </div>
              <div className="technology-watch"><p>{technologyWatch.insight}</p><small>{technologyWatch.prompt}</small>
                <a href={technologyWatch.href} target="_blank" rel="noreferrer">Review {technologyWatch.source} ↗</a></div>
            </aside>
          </section>

          {!connected && !loading && <div className="preview-banner" role="status"><strong>Showing representative data.</strong> Start the local database and API to use live persistence.</div>}

          <section className="overview-operating-row" aria-label="Daily priorities, weekly applications, and pipeline health">
            <section className="daily-workspace">
              <article className="panel daily-plan-panel">
                <div className="daily-plan-context"><p>Coding and opportunity execution stay protected every day; the third priority rotates to build depth without fragmenting attention.</p>
                  <div><span>Today’s specialization</span><strong>{todayFocusPlan.label}</strong></div></div>
                <PanelHeader eyebrow="Daily operating priorities" title="Top three priorities"
                  action={<span className="source-date">{formatScheduleDate(dashboard.date)}</span>} />
                <div className="daily-priority-list">{topDailyPriorities.map((priority) => <article key={priority.index}>
                  <span>{priority.index}</span><div><small>{priority.label} · {priority.time}</small><strong>{priority.title}</strong><p>{priority.detail}</p></div>
                </article>)}</div>
                <details className="imported-action-queue">
                  <summary><span>Opening-specific action queue</span><strong>{todoCount} remaining</strong></summary>
                  {dailyActions.actions.length === 0 ? <p className="inline-empty">No opening-specific actions were imported for today.</p>
                    : <div className="daily-action-list">{dailyActions.actions.map((action) =>
                      <article className={`daily-action ${action.status === "DONE" ? "complete" : ""}`} key={action.id}>
                        <button className="action-check" disabled={!connected} onClick={() => void toggleDailyAction(action)}
                          aria-label={`${action.status === "DONE" ? "Reopen" : "Complete"} priority ${action.priorityRank}`}>
                          {action.status === "DONE" ? "✓" : action.priorityRank}
                        </button>
                        <div><span>Imported priority {action.priorityRank}</span><p>{action.actionText}</p>
                          {action.companyName && <small>{action.companyName}{action.roleTitle ? ` · ${action.roleTitle}` : ""}</small>}</div>
                      </article>)}</div>}
                </details>
                <details className="application-followups">
                  <summary><span>Application follow-ups</span><strong>{dashboard.actions.length}</strong></summary>
                  {dashboard.actions.length === 0 ? <p className="inline-empty">No application follow-ups are due in the next 48 hours.</p> : <>
                    <div className="application-followup-list">{pagedApplicationFollowUps.map((item) => <article className="compact-followup" key={item.applicationId}>
                      <div className="followup-identity"><strong>{item.companyName}</strong><span>{item.roleTitle}</span></div>
                      <div className="followup-action"><span>{stageLabels[item.stage]} · {formatDue(item.nextActionAt)}</span><p>{item.nextAction}</p></div>
                      <button className="secondary-button" disabled={!connected}
                        onClick={() => setApplicationFollowUpTarget(item)}>Update</button>
                    </article>)}</div>
                    <div className="pagination compact-pagination"><span>Showing {applicationFollowUpRangeStart}–{applicationFollowUpRangeEnd} of {dashboard.actions.length}</span>
                      <div><button disabled={effectiveApplicationFollowUpPage === 1} onClick={() => setCurrentApplicationFollowUpPage((page) => Math.max(1, page - 1))}>Previous</button>
                        <span>Page {effectiveApplicationFollowUpPage} of {applicationFollowUpPageCount}</span>
                        <button disabled={effectiveApplicationFollowUpPage === applicationFollowUpPageCount} onClick={() => setCurrentApplicationFollowUpPage((page) => Math.min(applicationFollowUpPageCount, page + 1))}>Next</button></div></div>
                  </>}
                </details>
              </article>
            </section>

            <section className="weekly-application-goal" aria-label={`${dashboard.appliedThisWeek} of ${WEEKLY_APPLICATION_TARGET} weekly applications completed`}>
              <header><div><p className="eyebrow">Weekly application goal</p><strong>{dashboard.appliedThisWeek} of {WEEKLY_APPLICATION_TARGET} applications</strong></div>
                <span>{weeklyApplicationProgress}% complete</span></header>
              <div className="weekly-goal-track" role="progressbar" aria-valuemin={0} aria-valuemax={WEEKLY_APPLICATION_TARGET}
                aria-valuenow={Math.min(dashboard.appliedThisWeek, WEEKLY_APPLICATION_TARGET)}><i style={{ width: `${weeklyApplicationProgress}%` }} /></div>
              <footer><span>{weeklyApplicationsRemaining > 0 ? `${weeklyApplicationsRemaining} applications remaining this week` : "Weekly target reached—excellent momentum."}</span>
                <small>Target · {WEEKLY_APPLICATION_TARGET}</small></footer>
            </section>

            <article className="overview-pipeline-panel" id="pipeline">
              <header><div><p className="eyebrow">Application pipeline</p><strong>{dashboard.activeApplications} active application{dashboard.activeApplications === 1 ? "" : "s"}</strong></div>
                <span>Current stages</span></header>
              <div className="pipeline-list compact">{(["DRAFT", "APPLIED", "RECRUITER_SCREEN", "INTERVIEWING", "OFFER"] as ApplicationStage[]).map((stage) => {
                const value = dashboard.pipeline[stage] ?? 0;
                const max = Math.max(...Object.values(dashboard.pipeline).map(Number), 1);
                return <div className="pipeline-row" key={stage}><span className="pipeline-label">{stageLabels[stage]}</span>
                  <span className="pipeline-track"><i style={{ width: `${Math.max(value ? 16 : 2, (value / max) * 100)}%` }} /></span><strong>{value}</strong></div>;
              })}</div>
              {latestSuccessfulImport && <div className="pipeline-import-receipt"><span><i />Latest high-fit import</span>
                <strong>{formatDate(latestSuccessfulImport.sourceDate)}</strong>
                <small>{latestSuccessfulImport.opportunitiesCreated} new opening{latestSuccessfulImport.opportunitiesCreated === 1 ? "" : "s"} imported</small></div>}
              {applications.length > 0 && <details className="application-artifact-archive overview-artifact-archive">
                <summary><span>Application evidence archive</span><small>Browse stored files</small></summary>
                <div className="application-artifact-list">{applications.map((application) => {
                  const resumeArtifact = application.artifacts.find((artifact) => artifact.artifactType === "RESUME_PDF");
                  const descriptionArtifact = application.artifacts.find((artifact) => artifact.artifactType === "JOB_DESCRIPTION_TEXT");
                  return <article key={application.id}><header><div><strong>{application.companyName}</strong><span>{application.roleTitle}</span></div><em>{stageLabels[application.stage]}</em></header>
                    <button type="button" className="secondary-button" onClick={() => setInterviewApplicationId(application.id)}>Application details & interviews</button>
                    {resumeArtifact ? <div className="artifact-proof"><span>PDF</span><div><strong>{resumeArtifact.storedPath}</strong><small>SHA-256 {resumeArtifact.contentHash.slice(0, 12)}… · {formatFileSize(resumeArtifact.sizeBytes)}</small></div></div>
                      : <p className="artifact-missing">No immutable resume file — this application predates Milestone 5A.</p>}
                    <small className={descriptionArtifact ? "description-preserved" : "description-missing"}>{descriptionArtifact ? `✓ Job description preserved · ${descriptionArtifact.storedPath}` : "Job description not stored"}</small>
                  </article>;
                })}</div>
              </details>}
            </article>
          </section>

          <TodaySchedule date={dashboard.date} dailyActions={dailyActions.actions} preparation={preparation}
            applicationFollowUps={dashboard.actions} outreach={outreach} focusPlan={todayFocusPlan} />
          </div>}

          {activeWorkspace === "opportunities" && <div className="workspace-page opportunity-page" id="opportunities">
          <WorkspaceIntro eyebrow="Opportunity portfolio" title="High-fit openings and intake review"
            description="Prioritize researched roles first, then process new sources into the same trusted opportunity record." />
          <section className="panel generic-inbox-panel" id="inbox">
            <PanelHeader eyebrow="Milestone 4D · assisted, review-first ingestion" title="Inbox review" count={inboxReviewCount}
              action={<div className="inbox-header-actions"><button className="secondary-button" disabled={!connected || duplicateScanBusy}
                onClick={() => void scanInboxDuplicates()}>{duplicateScanBusy ? "Checking…" : "Check duplicates"}</button>
                <button className="primary-button" disabled={!connected} onClick={() => setInboxIntakeOpen(true)}>+ Add source</button></div>} />
            <div className="inbox-summary">
              <div><strong>{inboxReviewCount}</strong><span>candidates awaiting review</span></div>
              <div><strong>{inboxDuplicateCount}</strong><span>possible duplicates to resolve</span></div>
              <p>Exact identifiers and explainable company, title, and location similarities are flagged. Nothing is linked or merged without your decision.</p>
            </div>
            {inboxItems.length === 0 ? <EmptyState text="No generic inbox sources yet. Add a pasted job or an export to begin the review queue." />
              : <div className="inbox-source-list">{inboxItems.slice(0, 10).map((item) => <article className="inbox-source" key={item.id}>
                <header><div><span className={`inbox-status ${item.status.toLowerCase()}`}>{formatEnum(item.status)}</span>
                  <strong>{item.sourceFilename ?? item.sourceLabel ?? formatEnum(item.sourceType)}</strong>
                  <small>{formatEnum(item.sourceType)} · {formatDateTime(item.createdAt)} · hash {item.contentHash.slice(0, 8)}</small></div>
                  <span>{item.candidateCount} candidate{item.candidateCount === 1 ? "" : "s"}</span></header>
                {item.errorMessage && <p className="inbox-error">{item.errorMessage}</p>}
                <div className="inbox-candidate-list">{item.candidates.map((candidate) => <div className={`inbox-candidate ${candidate.status.toLowerCase()}`} key={candidate.id}>
                  <span className="inbox-row-number">{String(candidate.rowNumber).padStart(2, "0")}</span>
                  <div className="inbox-candidate-copy"><strong>{candidate.roleTitle ?? "Role title needs review"}</strong>
                    <span>{candidate.companyName ?? "Company needs review"}{candidate.location ? ` · ${candidate.location}` : ""}</span>
                    {candidate.parseWarnings && <small>{candidate.parseWarnings}</small>}</div>
                  <span className={`status-pill ${candidate.status.toLowerCase()}`}>{formatEnum(candidate.status)}</span>
                  <div className="inbox-candidate-actions">{candidate.status !== "REJECTED" && <button className="assist-button"
                    disabled={inboxBusyId === candidate.id} onClick={() => setInboxAssistanceCandidate(candidate)}>✦ Assist</button>}{candidate.status === "DUPLICATE_REVIEW" ? <>
                    <button className="text-button" disabled={inboxBusyId === candidate.id} onClick={() => setInboxEditCandidate(candidate)}>Edit fields</button>
                    <button className="text-button reject" disabled={inboxBusyId === candidate.id} onClick={() => void rejectInboxCandidate(candidate)}>Reject</button>
                    <button className="secondary-button" disabled={inboxBusyId === candidate.id} onClick={() => setDuplicateReviewCandidate(candidate)}>
                      Review {candidate.duplicateMatches.filter((match) => match.status === "OPEN").length} match{candidate.duplicateMatches.filter((match) => match.status === "OPEN").length === 1 ? "" : "es"}</button>
                  </> : candidate.status === "NEEDS_REVIEW" ? <>
                    <button className="text-button" disabled={inboxBusyId === candidate.id} onClick={() => setInboxEditCandidate(candidate)}>Edit fields</button>
                    <button className="text-button reject" disabled={inboxBusyId === candidate.id} onClick={() => void rejectInboxCandidate(candidate)}>Reject</button>
                    <button className="secondary-button" disabled={inboxBusyId === candidate.id || !candidate.companyName || !candidate.roleTitle}
                      onClick={() => void importInboxCandidate(candidate)}>{inboxBusyId === candidate.id ? "Working…" : "Import opening"}</button>
                  </> : candidate.opportunityId ? <a className="text-link" href="#openings">View opening →</a> : <span className="reviewed-note">Source retained</span>}</div>
                </div>)}</div>
              </article>)}</div>}
            <p className="intelligence-note">Identical source content is replay-safe. Duplicate evidence, raw input, parser version, source metadata, and every review outcome remain preserved locally.</p>
          </section>

          <section className="panel intelligence-panel" id="openings">
            <PanelHeader eyebrow="Evidence-backed opportunity inbox" title="High-fit openings"
              action={<div className="feed-meta"><strong>{visibleOpenings.length ? `${openingRangeStart}–${openingRangeEnd}` : "0"}</strong>
                <span>of {visibleOpenings.length} {openingView === "archived" ? "archived" : openingView === "unapplied" ? "unapplied" : "shown"}</span></div>} />
            <div className="opportunity-metric-strip" aria-label="Opportunity portfolio summary">
              <MetricCard label="Imported openings" value={dashboard.totalOpportunities} note="unique roles" tone="ink" />
              <MetricCard label="Shortlist" value={dashboard.shortlistedOpportunities} note="apply or refer" tone="coral" />
              <MetricCard label="Active pipeline" value={dashboard.activeApplications} note="applications in motion" tone="green" />
              <MetricCard label="Applied this week" value={dashboard.appliedThisWeek} note="quality submissions" tone="blue" />
            </div>
            <div className="filter-bar">
              <label><span>View</span><select value={openingView} onChange={(event) => {
                setOpeningView(event.target.value as "unapplied" | "active" | "archived"); setDateFilter("all"); setRecommendation(""); setResumeFilter(""); setCurrentOpeningPage(1);
              }}><option value="unapplied">UnApplied Openings</option><option value="active">Active openings</option><option value="archived">Archived openings</option></select></label>
              <label className="search-field"><span>Search</span><input value={query} onChange={(event) => { setQuery(event.target.value); setCurrentOpeningPage(1); }} placeholder="Company, role, technology…" /></label>
              <label><span>Day</span><select value={dateFilter} onChange={(event) => { setDateFilter(event.target.value); setCurrentOpeningPage(1); }}>
                <option value="latest">Latest day</option><option value="all">All days</option>
                {displayFeed.availableDates.map((date) => <option value={date} key={date}>{formatDate(date)}</option>)}</select></label>
              <label><span>Recommendation</span><select value={recommendation} onChange={(event) => { setRecommendation(event.target.value); setCurrentOpeningPage(1); }}>
                <option value="">All recommendations</option>{recommendationOptions.map((value) => <option value={value} key={value}>{value}</option>)}</select></label>
              <label><span>Resume</span><select value={resumeFilter} onChange={(event) => { setResumeFilter(event.target.value); setCurrentOpeningPage(1); }}>
                <option value="">All resume variants</option>{displayFeed.resumeVariants.map((value) => <option value={value} key={value}>{value}</option>)}</select></label>
              <label><span>Min score</span><select value={minScore} onChange={(event) => { setMinScore(event.target.value); setCurrentOpeningPage(1); }}>
                <option value="0">Any score</option><option value="8">8.0+</option><option value="8.5">8.5+</option><option value="9">9.0+</option></select></label>
            </div>

            <div className="opening-table" role="table" aria-label="Imported high-fit openings">
              <div className="opening-table-head" role="row"><span>Rank / opening</span><span>Fit</span><span>Recommendation</span><span>Resume lane</span><span>Next step</span></div>
              {visibleOpenings.length === 0 ? <EmptyState text={openingView === "archived" ? "No archived openings match these filters." : openingView === "unapplied" ? "No unapplied openings match these filters." : "No openings match these filters."} /> : pagedOpenings.map((opening) =>
                <article className={`opening-row ${openingView === "archived" ? "archived" : ""}`} key={`${opening.opportunityId}-${opening.observedOn}`}>
                  <button className="opening-main" onClick={() => void showDetail(opening)} disabled={!connected}>
                    <span className="rank-number">{String(opening.rank).padStart(2, "0")}</span>
                    <span><strong>{opening.roleTitle}</strong><small>{opening.companyName} · {opening.location ?? "Location not listed"}</small>
                      {opening.archiveReason && <em className="archive-note">Not pursuing: {opening.archiveReason}</em>}</span>
                  </button>
                  <button className="score-button" onClick={() => void showDetail(opening)} disabled={!connected}>
                    <strong>{opening.weightedTotal.toFixed(2)}</strong><span>/ 10</span>
                  </button>
                  <span className={`recommendation-pill ${recommendationClass(openingRecommendation(opening))}`}>{openingRecommendation(opening)}</span>
                  <span className="resume-lane">{opening.recommendedResumeVariant ?? "Not assigned"}</span>
                  <div className="row-actions">{openingView === "archived" ?
                    <button className="secondary-button restore-button" disabled={!connected} onClick={() => void restoreOpening(opening)}>Restore opening</button>
                    : <>{opening.applicationId
                      ? <span className={opening.applicationStage === "DRAFT" ? "draft-check" : "applied-check"}>
                        {opening.applicationStage === "DRAFT" ? "◌ Application in progress" : `✓ Applied${opening.applicationStage && opening.applicationStage !== "APPLIED" ? ` · ${formatEnum(opening.applicationStage)}` : ""}`}</span>
                      : <button className="secondary-button" disabled={!connected || resumes.length === 0} onClick={() => startFromOpening(opening)}>Start application</button>}
                      <button className="referral-link" disabled={!connected} onClick={() => setReferralTarget(opening)}>
                        {outreach.some((item) => item.opportunityId === opening.opportunityId && !["CLOSED", "DECLINED"].includes(item.status)) ? "Referral tracked" : "Track referral"}
                      </button>
                      <button className="referral-link path-link" disabled={!connected} onClick={() => startReferralDiscovery(opening)}>Find paths</button>
                      {!opening.applicationId && <button className="archive-link" disabled={!connected} onClick={() => setArchiveTarget(opening)}>Archive</button>}</>}
                    {opening.applicationId && <button className="referral-link" disabled={!connected} onClick={() => setInterviewApplicationId(opening.applicationId)}>Interviews</button>}
                    <a className="external-link" href={opening.sourceUrl} target="_blank" rel="noreferrer" aria-label={`Open ${opening.companyName} job posting`}>↗</a></div>
                </article>)}</div>
            {visibleOpenings.length > 0 && <nav className="opening-pagination" aria-label="High-fit openings pages">
              <button className="secondary-button" disabled={effectiveOpeningPage === 1} onClick={() => setCurrentOpeningPage(effectiveOpeningPage - 1)}>← Previous</button>
              <div className="page-numbers">{Array.from({ length: openingPageCount }, (_, index) => index + 1).map((page) =>
                <button className={page === effectiveOpeningPage ? "active" : ""} aria-current={page === effectiveOpeningPage ? "page" : undefined}
                  onClick={() => setCurrentOpeningPage(page)} key={page}>{page}</button>)}</div>
              <span>Page {effectiveOpeningPage} of {openingPageCount} · {OPENINGS_PER_PAGE} openings per page</span>
              <button className="secondary-button" disabled={effectiveOpeningPage === openingPageCount} onClick={() => setCurrentOpeningPage(effectiveOpeningPage + 1)}>Next →</button>
            </nav>}
            <p className="intelligence-note">Scores are preserved from each daily workbook. Use them as prioritization evidence—not as an objective guarantee of recruiter response.</p>
          </section>
          </div>}

          {activeWorkspace === "outreach" && <div className="workspace-page outreach-page" id="outreach">
          <WorkspaceIntro eyebrow="Relationship-led execution" title="Referrals and outreach"
            description="Work the most urgent follow-ups first, then discover and qualify new referral paths for a selected opening." />
          <section className="panel referral-panel" id="referrals">
            <PanelHeader eyebrow="Network execution" title="Referrals & outreach"
              action={<div className="feed-meta"><strong>{activeOutreach.length}</strong><span>active · {dueOutreach} due</span></div>} />
            <div className="referral-summary"><p>Explore every credible path first, then turn the strongest candidate into thoughtful outreach.</p>
              <span>{contacts.length} saved contact{contacts.length === 1 ? "" : "s"} · {referralCandidates.length} candidate path{referralCandidates.length === 1 ? "" : "s"}</span></div>
            <div className="outreach-priority-strip" aria-label="Outreach operating summary">
              <article className={dueOutreach > 0 ? "attention" : ""}><span>Needs action</span><strong>{dueOutreach}</strong><small>overdue follow-ups</small></article>
              <article><span>Ready to send</span><strong>{activeOutreach.filter((item) => item.status === "PLANNED").length}</strong><small>planned messages</small></article>
              <article><span>Awaiting reply</span><strong>{activeOutreach.filter((item) => item.status === "SENT").length}</strong><small>sent requests</small></article>
              <article><span>Warm outcomes</span><strong>{outreach.filter((item) => ["RESPONDED", "REFERRED"].includes(item.status)).length}</strong><small>responses or referrals</small></article>
            </div>

            <section className="referral-discovery">
              <header className="discovery-context"><div><p className="section-label">Referral path finder</p><h3>{referralOpening ? `${referralOpening.companyName} · ${referralOpening.roleTitle}` : "Choose a high-fit opening"}</h3>
                <p>Search your network, record the people worth considering, and compare why each route may work.</p></div>
                <label><span>Opening</span><select value={referralOpening?.opportunityId ?? ""} onChange={(event) => setReferralOpportunityId(event.target.value)}>
                  {feed.openings.map((opening) => <option value={opening.opportunityId} key={opening.opportunityId}>{opening.companyName} · {opening.roleTitle}</option>)}</select></label></header>

              {referralOpening && <section className="linkedin-network-matches" aria-label="Imported LinkedIn network matches">
                <div className="section-heading"><div><p className="section-label">Official LinkedIn export</p><h3>First-degree company matches</h3></div>
                  <div className="linkedin-match-actions"><span>{linkedInMatches.length} at {referralOpening.companyName}</span>
                    <button className="secondary-button" disabled={linkedinImporting} onClick={() => void importLinkedInConnections()}>{linkedinImporting ? "Importing…" : linkedInOverview.totalConnections > 0 ? "Refresh connections" : "Import connections"}</button></div></div>
                {linkedInOverview.totalConnections === 0 ? <p className="inline-empty">Import your official LinkedIn connections export to surface first-degree matches at this company.</p>
                  : linkedInMatches.length === 0 ? <p className="inline-empty">No current-company matches were found in the imported export. Continue with the focused LinkedIn searches below.</p>
                    : <div className="linkedin-match-grid">{linkedInMatches.slice(0, 8).map((connection) => {
                      const saved = referralCandidates.some((candidate) => candidate.opportunityId === referralOpening.opportunityId
                        && candidate.profileUrl?.replace(/\/+$/, "").toLowerCase() === connection.profileUrl?.replace(/\/+$/, "").toLowerCase());
                      return <article key={connection.id}><div><strong>{connection.fullName}</strong><small>{connection.roleTitle ?? "Role not provided"}</small>
                        {connection.connectedOn && <span>Connected {formatDate(connection.connectedOn)}</span>}</div>
                        <div>{connection.profileUrl && <a className="external-link" href={connection.profileUrl} target="_blank" rel="noreferrer">↗</a>}
                          {saved ? <span className="applied-check">✓ Candidate saved</span>
                            : <button className="secondary-button" onClick={() => void saveLinkedInMatch(connection)}>Save path</button>}</div></article>;
                    })}</div>}
              </section>}

              {referralOpening && <section className="linkedin-path-searches" aria-label="LinkedIn referral path searches">
                <header className="linkedin-search-hub-header">
                  <div className="linkedin-search-hub-copy"><span className="linkedin-mark compact">in</span><div><strong>Focused LinkedIn searches</strong>
                    <p>Choose a relationship or role path at {referralOpening.companyName}, then refine LinkedIn&apos;s company, school, or connection filters if needed.</p></div></div>
                  <div className="linkedin-search-hub-actions">
                    <div className="linkedin-role-toggle" title={`Add “${linkedinRoleKeywords(referralOpening)}” to every search`}>
                      <input id="linkedin-role-keyword-toggle" type="checkbox" aria-describedby="linkedin-role-keyword-description" checked={includeLinkedInRoleKeywords} onChange={(event) => setIncludeLinkedInRoleKeywords(event.target.checked)} />
                      <div><label htmlFor="linkedin-role-keyword-toggle">Include role keywords</label><small id="linkedin-role-keyword-description">{linkedinRoleKeywords(referralOpening)}</small></div>
                    </div>
                    <a className="secondary-button link-button" href={linkedinPeopleSearch(referralOpening, "SECOND", includeLinkedInRoleKeywords)} target="_blank" rel="noreferrer">Search 2nd degree ↗</a>
                    <button className="primary-button" onClick={() => setReferralDiscoveryDialog({ kind: "candidate", channel: "LINKEDIN" })}>+ Save candidate</button>
                  </div>
                </header>
                <div className="linkedin-path-search-grid">{linkedInPathSearches.map((path) =>
                  <a className="linkedin-path-search-card" href={linkedinReferralPathSearch(referralOpening, path.value, includeLinkedInRoleKeywords)} target="_blank" rel="noreferrer"
                    aria-label={`Search LinkedIn for ${path.label.toLowerCase()} at ${referralOpening.companyName}`} key={path.value}>
                    <span>{path.label}</span><strong>{referralOpening.companyName}</strong><small>{path.description}</small><b>Search LinkedIn ↗</b>
                  </a>)}</div>
              </section>}

              <section className="other-referral-paths" aria-label="Other referral paths"><p className="section-label">Other referral paths</p>
                <div className="channel-grid">
                  {referralChannels.filter((channel) => ["COMMUNITY", "EMAIL"].includes(channel.value)).map((channel) => {
                    const count=openingCandidates.filter((candidate) => candidate.discoveryChannel === channel.value).length;
                    return <button key={channel.value} onClick={() => referralOpening && setReferralDiscoveryDialog({ kind: "candidate", channel: channel.value })} disabled={!referralOpening}>
                      <span>{channel.index}</span><div><strong>{channel.label}</strong><small>{channel.description}</small></div><b>{count || "+"}</b></button>; })}
                </div>
              </section>

              {matchingContacts.length > 0 && <div className="network-signal"><strong>{matchingContacts.length} saved contact{matchingContacts.length === 1 ? "" : "s"} already match {referralOpening?.companyName}.</strong>
                <span>{matchingContacts.slice(0, 3).map((contact) => contact.fullName).join(" · ")}</span>
                <button className="text-button" onClick={() => referralOpening && setReferralTarget(referralOpening)}>Plan outreach</button></div>}

              <div className="candidate-list"><div className="candidate-list-title"><div><p className="section-label">Candidate paths</p><h4>{openingCandidates.length ? `${openingCandidates.length} path${openingCandidates.length === 1 ? "" : "s"} under consideration` : "No candidates saved yet"}</h4></div>
                {referralOpening && <button className="text-button" onClick={() => setReferralDiscoveryDialog({ kind: "candidate", channel: "LINKEDIN" })}>+ Add manually</button>}</div>
                {openingCandidates.length === 0 ? <div className="candidate-empty"><span>↗</span><p>Use a search shortcut or choose a referral channel above. Save each credible person before deciding whom to contact.</p></div>
                  : openingCandidates.map((candidate) => <article className="candidate-card" key={candidate.id}>
                    <div className={`path-score ${candidate.pathStrength.toLowerCase()}`}><strong>{candidate.pathScore}</strong><span>{formatEnum(candidate.pathStrength)}</span></div>
                    <div className="candidate-identity"><div><span className="relationship-chip">{formatEnum(candidate.connectionDegree)} degree</span><span className="channel-chip">{formatEnum(candidate.discoveryChannel)}</span></div>
                      <strong>{candidate.fullName}</strong><small>{candidate.roleTitle ?? "Role not recorded"} · {candidate.candidateCompany ?? "Company not recorded"}</small>
                      <p>{candidate.scoreExplanation}</p>{candidate.mutualConnectionName && <em>Mutual path: {candidate.mutualConnectionName}</em>}</div>
                    <div className="candidate-actions">{candidate.profileUrl && <a className="external-link" href={candidate.profileUrl} target="_blank" rel="noreferrer" aria-label={`Open ${candidate.fullName} profile`}>↗</a>}
                      {candidate.status === "OUTREACH_CREATED" ? <span className="applied-check">✓ Outreach planned</span>
                        : <button className="primary-button" onClick={() => setReferralDiscoveryDialog({ kind: "outreach", candidate })}>Start outreach</button>}</div>
                  </article>)}</div>

              <section className="message-template-composer" aria-label="Outreach message template composer">
                <div className="message-template-heading"><div><p className="section-label">Message templates</p><h3>Start from the right outreach baseline</h3>
                  <p>Company and role come from the selected opening. Replace the bracketed details and personalize the evidence before sending.</p></div>
                  <label><span>Message template</span><select value={messageTemplateScenario} onChange={(event) => {
                    const scenario = event.target.value as MessageTemplateScenario;
                    setMessageTemplateScenario(scenario);
                    setMessageTemplateDraft(scenario && referralOpening ? outreachMessageTemplate(scenario, referralOpening) : "");
                  }}><option value="">Select a scenario…</option>
                    <optgroup label="Connection requests">{messageTemplateScenarios.filter((item) => item.group === "Connection request").map((item) =>
                      <option value={item.value} key={item.value}>Connection request · {item.label}</option>)}</optgroup>
                    <optgroup label="InMail messages">{messageTemplateScenarios.filter((item) => item.group === "InMail").map((item) =>
                      <option value={item.value} key={item.value}>InMail · {item.label}</option>)}</optgroup>
                    <optgroup label="Regular messages">{messageTemplateScenarios.filter((item) => item.group === "Message").map((item) =>
                      <option value={item.value} key={item.value}>Message · {item.label}</option>)}</optgroup>
                  </select></label></div>
                {!messageTemplateScenario ? <div className="message-template-empty"><span>✦</span><p>Choose a scenario to generate an editable message for {referralOpening?.companyName ?? "the selected company"}.</p></div>
                  : <div className="message-template-editor"><label><span>Editable message</span><textarea value={messageTemplateDraft}
                    onChange={(event) => setMessageTemplateDraft(event.target.value)} rows={messageTemplateScenario.startsWith("CONNECTION_") ? 5 : 10} /></label>
                    <div className="message-template-footer"><span className={messageTemplateScenario.startsWith("CONNECTION_") && messageTemplateDraft.length > 300 ? "over-limit" : ""}>
                      {messageTemplateDraft.length} characters{messageTemplateScenario.startsWith("CONNECTION_") ? " · LinkedIn connection notes allow 300" : ""}</span>
                      <button className="primary-button" type="button" disabled={!messageTemplateDraft.trim()} onClick={() => void copyMessageTemplate()}>Copy message</button></div></div>}
              </section>
            </section>

            <div className="outreach-heading"><p className="section-label">Active outreach and follow-ups</p>
              <span>{visibleOutreach.length} matching · sorted by {outreachSort === "URGENCY" ? "urgency" : formatEnum(outreachSort)}</span></div>
            <div className="outreach-controls" aria-label="Outreach filters">
              <label className="outreach-search"><span>Search</span><input value={outreachQuery} onChange={(event) => { setOutreachQuery(event.target.value); setCurrentOutreachPage(1); }} placeholder="Contact, company, or role…" /></label>
              <label><span>Status</span><select value={outreachStatusFilter} onChange={(event) => { setOutreachStatusFilter(event.target.value as "ACTIVE" | "ALL" | OutreachStatus); setCurrentOutreachPage(1); }}>
                <option value="ACTIVE">Active only</option><option value="ALL">All statuses</option>
                {(["PLANNED", "SENT", "RESPONDED", "REFERRED", "DECLINED", "CLOSED"] as OutreachStatus[]).map((status) => <option value={status} key={status}>{formatEnum(status)}</option>)}
              </select></label>
              <label><span>Company</span><select value={outreachCompanyFilter} onChange={(event) => { setOutreachCompanyFilter(event.target.value); setCurrentOutreachPage(1); }}>
                <option value="">All companies</option>{outreachCompanies.map((company) => <option value={company} key={company}>{company}</option>)}
              </select></label>
              <label><span>Timing</span><select value={outreachTimingFilter} onChange={(event) => { setOutreachTimingFilter(event.target.value as OutreachTimingFilter); setCurrentOutreachPage(1); }}>
                <option value="ALL">Any timing</option><option value="OVERDUE">Overdue</option><option value="NEXT_SEVEN_DAYS">Due in 7 days</option><option value="UNSCHEDULED">No follow-up</option>
              </select></label>
              <label><span>Sort</span><select value={outreachSort} onChange={(event) => { setOutreachSort(event.target.value as OutreachSort); setCurrentOutreachPage(1); }}>
                <option value="URGENCY">Urgency</option><option value="FOLLOW_UP">Follow-up date</option><option value="RECENT">Recently updated</option><option value="COMPANY">Company A–Z</option>
              </select></label>
            </div>
            <div className="outreach-list">
              {outreach.length === 0 ? <EmptyState text="No referral activity yet. Use Track referral on a high-fit opening to begin." />
                : visibleOutreach.length === 0 ? <EmptyState text="No outreach matches these filters. Try broadening the status, company, or timing selection." />
                : pagedOutreach.map((item) => <article className={`outreach-card ${item.overdue ? "overdue" : ""}`} key={item.id}>
                  <div className="outreach-person"><span className="relationship-chip">{formatEnum(item.relationshipStrength)}</span>
                    <strong>{item.contactName}</strong><small>{item.contactCompany ?? "Company not recorded"} · for {item.companyName}</small></div>
                  <div className="outreach-role"><span>{formatEnum(item.outreachType)}</span><strong>{item.roleTitle}</strong>
                    <small>{item.messageSummary ?? "No message summary recorded"}</small></div>
                  <div className="outreach-followup"><span className={`status-pill ${item.status.toLowerCase()}`}>{formatEnum(item.status)}</span>
                    <small>{item.followUpAt ? `${item.overdue ? "Overdue · " : "Follow up · "}${formatDateTime(item.followUpAt)}` : "No follow-up scheduled"}</small>
                    {item.status === "SENT" && <b className="follow-up-count">{item.followUpCount ?? 0} follow-up{(item.followUpCount ?? 0) === 1 ? "" : "s"}</b>}</div>
                  {item.status === "SENT" ? <button className="secondary-button" onClick={() => setOutreachFollowUpTarget(item)}>Update follow-up</button>
                    : nextOutreachStatus(item.status) && <button className="secondary-button" onClick={() => void advanceOutreach(item)}>{outreachActionLabel(item.status)}</button>}
                </article>)}
            </div>
            {visibleOutreach.length > 0 && <nav className="opening-pagination outreach-pagination" aria-label="Outreach pages">
              <button className="secondary-button" disabled={effectiveOutreachPage === 1} onClick={() => setCurrentOutreachPage(effectiveOutreachPage - 1)}>← Previous</button>
              <div className="page-numbers">{Array.from({ length: outreachPageCount }, (_, index) => index + 1).map((page) =>
                <button className={page === effectiveOutreachPage ? "active" : ""} aria-current={page === effectiveOutreachPage ? "page" : undefined}
                  onClick={() => setCurrentOutreachPage(page)} key={page}>{page}</button>)}</div>
              <span>Showing {outreachRangeStart}–{outreachRangeEnd} of {visibleOutreach.length} · {OUTREACH_PER_PAGE} per page</span>
              <button className="secondary-button" disabled={effectiveOutreachPage === outreachPageCount} onClick={() => setCurrentOutreachPage(effectiveOutreachPage + 1)}>Next →</button>
            </nav>}
          </section>
          </div>}

          {activeWorkspace === "skills" && <div className="workspace-page skills-page" id="skills">
          <WorkspaceIntro eyebrow="Evidence to capability" title="Market demand and personal skill strategy"
            description="Review trusted job evidence, maintain the canonical catalog, and keep the personal backlog ordered by real demand." />
          <section className="panel skill-evidence-panel">
            <PanelHeader eyebrow="Milestone 3C · cohort-filtered signals" title="Market skill evidence"
              count={skillOverview.proposedCount}
              action={<div className="skill-panel-actions"><button className="secondary-button" disabled={!connected || skillAutomationBusy} onClick={() => void seedSkillTaxonomy()}>{skillAutomationBusy ? "Working…" : "Seed reviewed taxonomy"}</button>
                <button className="secondary-button" disabled={!connected || skillAutomationBusy || skillOverview.catalogSize === 0} onClick={() => void extractSkillEvidence()}>{skillAutomationBusy ? "Working…" : "Fetch & extract live descriptions"}</button>
                <button className="secondary-button" disabled={!connected} onClick={() => setSkillDialog({ kind: "skill" })}>+ Add skill</button>
                <button className="primary-button" disabled={!connected || skillOverview.skills.length === 0 || feed.openings.length === 0} onClick={() => setSkillDialog({ kind: "evidence" })}>+ Capture evidence</button></div>} />
            <p className="skill-trust-note">Live job pages are fetched from each direct link and preserved as immutable full-description snapshots before matching. Workbook summaries never affect market demand. Proposed signals remain untrusted until you accept or correct them.</p>
            {liveExtractionReport && <details className="live-extraction-report" open={liveExtractionReport.fetchFailures > 0}>
              <summary><span>Latest live-description run</span><strong>{liveExtractionReport.pagesFetched} fetched · {liveExtractionReport.observationsCreated} proposed · {liveExtractionReport.fetchFailures} need attention</strong></summary>
              <div><p>{liveExtractionReport.snapshotsCreated} new immutable full-description snapshot{liveExtractionReport.snapshotsCreated === 1 ? "" : "s"} preserved. Successful pages now use the live source for deterministic matching.</p>
                {liveExtractionReport.opportunities.filter((item) => !item.fetched).length > 0 && <section><h4>Manual evidence needed</h4>
                  {liveExtractionReport.opportunities.filter((item) => !item.fetched).map((item) => <article key={item.opportunityId}><strong>{item.companyName} · {item.roleTitle}</strong><span>{item.detail}</span></article>)}</section>}
              </div>
            </details>}
            <div className="skill-stats" aria-label="Skill evidence summary">
              <article><strong>{skillOverview.catalogSize}</strong><span>canonical skills</span></article>
              <article><strong>{skillOverview.proposedCount}</strong><span>awaiting review</span></article>
            </div>
            <section className="market-signal-workspace" aria-labelledby="market-signal-heading">
              <header className="market-signal-heading"><div><p className="section-label">Accepted evidence only</p><h3 id="market-signal-heading">Cohort market signals</h3>
                <p>Frequency is based on distinct reviewed openings, never raw keyword mentions. Trend compares the selected window with the immediately preceding window of equal length.</p></div>
                {marketSignalLoading && <span className="market-loading" role="status">Refreshing trusted signals…</span>}</header>
              <div className="market-filter-grid" aria-label="Market signal cohort filters">
                <label><span>From</span><input type="date" value={marketFilters.from} max={marketFilters.to}
                  onChange={(event) => updateMarketFilter("from", event.target.value)} /></label>
                <label><span>To</span><input type="date" value={marketFilters.to} min={marketFilters.from}
                  onChange={(event) => updateMarketFilter("to", event.target.value)} /></label>
                <label><span>Role family</span><select value={marketFilters.roleFamily}
                  onChange={(event) => updateMarketFilter("roleFamily", event.target.value as MarketSignalFilters["roleFamily"])}>
                  <option value="">All role families</option>{(marketSignals?.options.roleFamilies ?? []).map((item) => <option value={item} key={item}>{formatEnum(item)}</option>)}</select></label>
                <label><span>Seniority</span><select value={marketFilters.seniority}
                  onChange={(event) => updateMarketFilter("seniority", event.target.value as MarketSignalFilters["seniority"])}>
                  <option value="">All seniority levels</option>{(marketSignals?.options.seniorities ?? []).map((item) => <option value={item} key={item}>{formatEnum(item)}</option>)}</select></label>
                <label><span>Company segment</span><select value={marketFilters.companySegment}
                  onChange={(event) => updateMarketFilter("companySegment", event.target.value)}>
                  <option value="">All company segments</option>{(marketSignals?.options.companySegments ?? []).map((item) => <option value={item} key={item}>{item}</option>)}</select></label>
                <label><span>Demand strength</span><select value={marketFilters.strength}
                  onChange={(event) => updateMarketFilter("strength", event.target.value as MarketSignalFilters["strength"])}>
                  <option value="">All accepted demand</option>{(marketSignals?.options.strengths ?? []).map((item) => <option value={item} key={item}>{formatEnum(item)}</option>)}</select></label>
              </div>
              <div className="market-cohort-summary" aria-label="Market signal sample sizes">
                <article><strong>{marketSignals?.currentSampleSize ?? 0}</strong><span>reviewed openings</span><small>{marketSignals ? `${formatDate(marketSignals.query.from)}–${formatDate(marketSignals.query.to)}` : "Selected window"}</small></article>
                <article><strong>{marketSignals?.previousSampleSize ?? 0}</strong><span>prior-window openings</span><small>{marketSignals ? `${formatDate(marketSignals.query.previousFrom)}–${formatDate(marketSignals.query.previousTo)}` : "Comparable window"}</small></article>
              </div>
              {!marketSignalLoading && (marketSignals?.signals.length ?? 0) === 0 ? <EmptyState text={skillOverview.acceptedCount === 0
                ? "Fetch live descriptions, then accept representative evidence to establish the trusted full-description market sample."
                : "No accepted full-description evidence matches this cohort. Broaden the date range or filters."} />
                : <div className="market-signal-list">
                  <div className="market-signal-columns" aria-hidden="true"><span>Skill</span><span>Current demand</span><span>Strength mix</span><span>Trend</span><span>Evidence</span></div>
                  {pagedMarketSignals.map((signal) => {
                    const expanded = expandedMarketSkillId === signal.skillId;
                    const evidence = marketEvidence[signal.skillId];
                    const trend = signal.trendDeltaPercentagePoints;
                    return <article className={`market-signal-row ${expanded ? "expanded" : ""}`} key={signal.skillId}>
                      <button type="button" className="market-signal-toggle" aria-expanded={expanded}
                        onClick={() => void toggleMarketEvidence(signal.skillId)}>
                        <span className="market-skill-name"><strong>{signal.skillName}</strong><small>{formatEnum(signal.category)}</small></span>
                        <span className="market-frequency"><strong>{signal.currentOpeningCount} <small>of {marketSignals?.currentSampleSize ?? 0}</small></strong>
                          <span>{signal.currentFrequencyPercent.toFixed(1)}% of reviewed openings</span><i><b style={{ width: `${Math.min(100, signal.currentFrequencyPercent)}%` }} /></i></span>
                        <span className="market-strengths"><em className="required">{signal.requiredCount} required</em><em className="preferred">{signal.preferredCount} preferred</em><em>{signal.mentionedCount} mentioned</em></span>
                        <span className={`market-trend ${trend === null ? "neutral" : trend > 0 ? "up" : trend < 0 ? "down" : "neutral"}`}>
                          <strong>{trend === null ? "—" : `${trend > 0 ? "+" : ""}${trend.toFixed(1)} pp`}</strong><small>{trend === null ? "No prior sample" : `${signal.previousOpeningCount} of ${marketSignals?.previousSampleSize ?? 0} before`}</small></span>
                        <span className="market-evidence-count"><strong>{signal.evidenceCount}</strong><small>{expanded ? "Hide excerpts" : "View excerpts"}</small></span>
                      </button>
                      {expanded && <div className="market-evidence-drilldown">
                        {evidence === undefined ? <p className="inline-empty">Loading accepted source excerpts…</p>
                          : evidence.length === 0 ? <p className="inline-empty">No accepted excerpts remain inside this cohort.</p>
                            : evidence.map((item) => <article key={item.observationId}><header><div><label className={`market-evidence-strength-editor ${item.strength.toLowerCase()}`}>
                              <span>Strength</span><select aria-label={`Demand strength for ${item.skillName} at ${item.companyName}`} value={item.strength}
                                disabled={marketEvidenceSavingId === item.observationId}
                                onChange={(event) => void updateMarketEvidenceStrength(item, event.target.value as SkillStrength)}>
                                <option value="MENTIONED">Mentioned</option><option value="PREFERRED">Preferred</option><option value="REQUIRED">Required</option>
                              </select></label>
                              <strong>{item.companyName} · {item.roleTitle}</strong></div><small>{formatDate(item.discoveredOn)} · {formatEnum(item.roleFamily)} · {item.companySegment}</small></header>
                              <blockquote>{item.evidenceSnippet}</blockquote><footer><span>{item.sourceLabel ?? formatEnum(item.sourceType)}</span>
                                {item.sourceUrl && <a href={item.sourceUrl} target="_blank" rel="noreferrer">Open source ↗</a>}</footer></article>)}
                      </div>}
                    </article>;
                  })}
                </div>}
              {(marketSignals?.signals.length ?? 0) > MARKET_SIGNALS_PER_PAGE && <nav className="opening-pagination market-signal-pagination" aria-label="Market signal pages">
                <button className="secondary-button" disabled={effectiveMarketSignalPage === 1} onClick={() => setCurrentMarketSignalPage(effectiveMarketSignalPage - 1)}>← Previous</button>
                <span>Page {effectiveMarketSignalPage} of {marketSignalPageCount} · {MARKET_SIGNALS_PER_PAGE} skills per page</span>
                <button className="secondary-button" disabled={effectiveMarketSignalPage === marketSignalPageCount} onClick={() => setCurrentMarketSignalPage(effectiveMarketSignalPage + 1)}>Next →</button>
              </nav>}
            </section>
            <div className={`skill-evidence-grid ${skillOverview.proposedCount === 0 ? "catalog-only" : ""}`}>
              <section className="skill-catalog"><div className="section-heading"><div><p className="section-label">Canonical taxonomy</p><h3>Skill catalog</h3></div><span>{skillOverview.skills.length} active</span></div>
                {skillOverview.skills.length === 0 ? <EmptyState text="Create the first canonical skill, then capture evidence from an active opening." />
                  : <><SkillCatalogNetwork skills={skillOverview.skills} backlog={personalBacklog} signals={marketSignals?.signals ?? []}
                    onAddToPlan={(skill) => void addCanonicalSkillToBacklog(skill)}
                    onOpenPlan={(item) => setBacklogDialog({ kind: "profile", item })} />
                    <details className="skill-catalog-list-disclosure"><summary><div><strong>Catalog list</strong><small>All canonical skills, descriptions, aliases, and merge controls</small></div>
                      <span><b>{skillOverview.skills.length} records</b><em>Expand list</em></span></summary>
                      <div className="skill-catalog-list">{[...skillOverview.skills].sort((left, right) => left.category.localeCompare(right.category) || left.name.localeCompare(right.name)).map((skill) => <article key={skill.id}>
                        <span className="category-chip">{formatEnum(skill.category)}</span><div className="skill-catalog-title"><strong>{skill.name}</strong>
                          <span>{!personalBacklog.items.some((item) => item.skillId === skill.id) && <button className="text-button" onClick={() => void addCanonicalSkillToBacklog(skill)}>Add to plan</button>}
                            {skillOverview.skills.length > 1 && <button className="text-button" onClick={() => setSkillDialog({ kind: "merge", source: skill })}>Merge</button>}</span></div>
                        <p>{skill.description ?? "No description yet."}</p>{skill.aliases.length > 0 && <small>Also matches: {skill.aliases.join(", ")}</small>}
                      </article>)}</div></details></>}
              </section>
              {skillOverview.proposedCount > 0 && <section className="skill-review"><div className="section-heading"><div><p className="section-label">Human validation</p><h3>Review queue</h3></div><span>{skillOverview.proposedCount} proposed</span></div>
                {selectedSkillObservationIds.length > 0 && <div className="skill-bulk-actions"><span>{selectedSkillObservationIds.length} selected</span>
                  <button className="text-button reject" onClick={() => void reviewSelectedSkillObservations("REJECTED")}>Reject selected</button>
                  <button className="secondary-button" onClick={() => void reviewSelectedSkillObservations("ACCEPTED")}>Accept selected</button></div>}
                <><div className="skill-review-list">{pagedSkillObservations.map((item) => <article className={selectedSkillObservationIds.includes(item.id) ? "selected" : ""} key={item.id}>
                    <div><input type="checkbox" aria-label={`Select ${item.skillName} evidence`} checked={selectedSkillObservationIds.includes(item.id)}
                      onChange={(event) => setSelectedSkillObservationIds((current) => event.target.checked ? [...current, item.id] : current.filter((id) => id !== item.id))} />
                      <span className={`evidence-strength ${item.strength.toLowerCase()}`}>{formatEnum(item.strength)}</span><strong>{item.skillName}</strong><small>{item.companyName} · {item.roleTitle} · {formatEnum(item.extractionMethod)}</small></div>
                    <blockquote>{item.evidenceSnippet}</blockquote><div className="skill-review-actions"><button className="text-button" onClick={() => setSkillDialog({ kind: "correction", observation: item })}>Correct</button>
                      <button className="text-button reject" onClick={() => void reviewSkillObservation(item, "REJECTED")}>Reject</button>
                      <button className="primary-button" onClick={() => void reviewSkillObservation(item, "ACCEPTED")}>Accept evidence</button></div>
                  </article>)}</div>
                    {skillReviewPageCount > 1 && <nav className="opening-pagination skill-review-pagination" aria-label="Skill evidence review pages">
                      <button className="secondary-button" disabled={effectiveSkillReviewPage === 1} onClick={() => setCurrentSkillReviewPage(effectiveSkillReviewPage - 1)}>← Previous</button>
                      <span>Page {effectiveSkillReviewPage} of {skillReviewPageCount} · {SKILL_REVIEW_PER_PAGE} signals per page</span>
                      <button className="secondary-button" disabled={effectiveSkillReviewPage === skillReviewPageCount} onClick={() => setCurrentSkillReviewPage(effectiveSkillReviewPage + 1)}>Next →</button>
                    </nav>}</>
              </section>}
            </div>
            <PersonalSkillBacklogPanel overview={personalBacklog} busy={skillAutomationBusy}
              onSync={() => void syncPersonalSkillBacklog()} onOpen={(dialog) => setBacklogDialog(dialog)} />
          </section>
          </div>}

          {activeWorkspace === "preparation" && <div className="workspace-page preparation-page" id="preparation">
          <WorkspaceIntro eyebrow="Delivery workspace" title="Preparation portfolio"
            description="Plan a two-week sprint across active tracks, then drill into one track’s stories, tasks, evidence, and schedule." />
          <section className="panel preparation-panel">
            <PanelHeader eyebrow={activePrepTrack ? `${formatEnum(activePrepTrack.category)} track dashboard` : "Bi-weekly delivery system"}
              title={activePrepTrack?.name ?? "Preparation workspace"}
              action={activePrepTrack
                ? <div className="prep-header-actions"><button className="secondary-button" onClick={() => setActivePrepTrackId(null)}>← All tracks</button>
                  <button className="primary-button" disabled={!connected} onClick={() => setPrepDialog({ kind: "milestone", trackId: activePrepTrack.id, label: activePrepTrack.name })}>+ Story</button></div>
                : <button className="primary-button" disabled={!connected} onClick={() => setPrepDialog({ kind: "track" })}>+ Add track</button>} />
            <div className="prep-momentum">
              <article className="preparation-mindset"><span>Preparation mindset</span><blockquote>“{preparationMotivation}”</blockquote>
                <small>Focused intensity · deliberate practice · evidence of progress</small></article>
              <div className="prep-momentum-stats"><article><strong>{preparation.stats.minutesThisWeek}</strong><span>minutes practiced<br/>this week</span><small>Logged from focused task sessions</small></article>
                <article><strong>{preparation.stats.sessionsThisWeek}</strong><span>sessions with<br/>evidence</span><small>Practice outcomes preserved</small></article></div>
            </div>

            {activePrepTrack
              ? <PreparationTrackDashboard track={activePrepTrack} backlog={personalBacklog} activeSprint={preparation.activeSprint}
                onAddTask={(milestone) => setPrepDialog({ kind: "item", milestoneId: milestone.id, label: milestone.title })}
                onEditStory={(milestone) => setPrepDialog({ kind: "story-edit", milestone, trackName: activePrepTrack.name })}
                onDeleteStory={(milestone) => setStoryDeleteTarget({ milestone, trackName: activePrepTrack.name })}
                busyTaskId={prepStatusBusyId} onAddToSprint={(item) => void addPrepItemToCurrentSprint(item)}
                onOpenTask={(context, initialTab = "DETAILS") => setTaskDetailsTarget({ context, initialTab })}
                onResourceChanged={async (message) => { setToast({ kind: "success", message }); await refresh(); }}
                onResourceError={(message) => setToast({ kind: "error", message })} />
              : <PreparationPortfolio preparation={preparation} onOpenTrack={setActivePrepTrackId}
                onCreateTrack={() => setPrepDialog({ kind: "track" })} busyTaskId={prepStatusBusyId}
                onUpdateStatus={(item, status) => void updatePrepItemStatus(item, status)}
                onUpdatePriority={(item, priority) => void updatePrepItemPriority(item, priority)}
                onOpenTask={(context, initialTab = "DETAILS") => setTaskDetailsTarget({ context, initialTab })}
                sprintLifecycleBusy={sprintLifecycleBusy} onStartSprint={() => void startPreparationSprint()}
                onRequestCloseSprint={setSprintCloseTarget} trackOrderBusy={prepTrackOrderBusy}
                onReorderTracks={(trackIds) => void reorderPreparationTracks(trackIds)} />}

            {preparation.recentSessions.length > 0 && <div className="practice-history"><p className="section-label">Recent practice evidence</p>
              {preparation.recentSessions.slice(0, 4).map((session) => <article key={session.id}><span>{formatDateTime(session.practicedAt)}</span>
                <div><strong>{session.itemTitle}</strong><small>{formatEnum(session.sessionType)} · {session.durationMinutes} min{session.confidenceAfter ? ` · confidence ${session.confidenceAfter}/5` : ""}</small></div>
                <p>{session.resultSummary ?? session.nextSteps ?? "Session logged"}</p></article>)}</div>}
          </section>
          </div>}

          {activeWorkspace === "reviews" && <div className="workspace-page reviews-page" id="reviews">
          <WorkspaceIntro eyebrow="Operating cadence" title="Weekly review and accountability"
            description="Separate live signals from frozen history, understand funnel health, and record the adjustments for the next week." />
          <section className="panel weekly-review-panel">
            <PanelHeader eyebrow="Milestone 4C · immutable weekly intelligence" title="Weekly review"
              action={<div className="weekly-header-actions">{selectedWeeklyReview && <button className="assist-button"
                onClick={() => setWeeklyAssistanceTarget(selectedWeeklyReview)}>✦ Draft reflection</button>}
                <button className="primary-button" disabled={!connected || weeklyReviewBusy || !weeklyReviews}
                  onClick={() => void generateWeeklyReview()}>{weeklyReviewBusy ? "Freezing…" : currentWeeklySnapshot ? "View current snapshot" : "Freeze current snapshot"}</button></div>} />
            {monthlyProgress && <MonthlyProgressCalendar progress={monthlyProgress} loading={monthlyProgressLoading}
              onMonthChange={(month) => void loadMonthlyProgress(month)} />}
            {!displayedWeeklyMetric ? <EmptyState text="Connect the local API to calculate this week’s review." /> : <>
              <div className="weekly-review-context"><div><span>{selectedWeeklyReview ? "Immutable snapshot" : "Live preview"}</span>
                <strong>{formatDate(displayedWeeklyMetric.weekStart)}–{formatDate(displayedWeeklyMetric.weekEnd)}</strong>
                <small>Measured through {formatDate(displayedWeeklyMetric.effectiveThrough)} · {displayedWeeklyMetric.completeWeek ? "complete week" : "week to date"}</small></div>
                <p>{selectedWeeklyReview
                  ? `Frozen ${formatDateTime(selectedWeeklyReview.generatedAt)}. Later activity will not rewrite these measurements.`
                  : "Live values may change as you record work. Freeze a snapshot when you are ready to preserve the week’s evidence."}</p></div>
              <div className="weekly-kpi-grid">
                <WeeklyKpi label="Applications" value={displayedWeeklyMetric.applicationsSubmitted} detail={`${displayedWeeklyMetric.highFitApplications} high-fit`} delta={selectedWeeklyReview?.delta.applicationsSubmitted} />
                <WeeklyKpi label="Referral outreach" value={displayedWeeklyMetric.outreachSent} detail={`${displayedWeeklyMetric.outreachResponses} responses · ${displayedWeeklyMetric.referralsSecured} referrals`} delta={selectedWeeklyReview?.delta.outreachSent} />
                <WeeklyKpi label="Sprint delivery" value={displayedWeeklyMetric.preparationTasksCompleted} detail={`of ${displayedWeeklyMetric.preparationTasksPlanned} planned tasks`} delta={selectedWeeklyReview?.delta.preparationTasksCompleted} />
                <WeeklyKpi label="Focused practice" value={displayedWeeklyMetric.preparationMinutes} suffix="min" detail={`${displayedWeeklyMetric.preparationSessions} sessions`} delta={selectedWeeklyReview?.delta.preparationMinutes} />
              </div>
              <div className="weekly-review-columns">
                <div className="weekly-signal-list"><div className="section-heading"><div><p className="section-label">Recorded outcomes</p><h3>Weekly evidence</h3></div></div>
                  <WeeklySignal label="Preparation tasks completed" value={displayedWeeklyMetric.preparationTasksCompleted} total={displayedWeeklyMetric.preparationTasksPlanned} />
                  <WeeklySignal label="Referral outcomes from outreach" value={displayedWeeklyMetric.referralsSecured} total={displayedWeeklyMetric.outreachSent} />
                  <WeeklySignal label="Outreach responses" value={displayedWeeklyMetric.outreachResponses} total={displayedWeeklyMetric.outreachSent} />
                  <WeeklySignal label="Applications submitted" value={displayedWeeklyMetric.applicationsSubmitted} />
                  <WeeklySignal label="Application stage progressions" value={displayedWeeklyMetric.applicationProgressions} />
                </div>
                <div className="weekly-health"><div className="section-heading"><div><p className="section-label">Operating health</p><h3>Pipeline and follow-ups</h3></div></div>
                  <div className="weekly-pipeline-stages"><span><b>{displayedWeeklyMetric.stageApplied}</b>Applied</span>
                    <span><b>{displayedWeeklyMetric.stageRecruiterScreen}</b>Recruiter</span>
                    <span><b>{displayedWeeklyMetric.stageInterviewing}</b>Interview</span>
                    <span><b>{displayedWeeklyMetric.stageOffer}</b>Offer</span></div>
                  <div className="weekly-risk-row"><span>Active pipeline</span><strong>{displayedWeeklyMetric.activePipeline}</strong></div>
                  <div className={`weekly-risk-row ${displayedWeeklyMetric.overdueApplicationActions > 0 ? "attention" : ""}`}><span>Overdue application actions</span><strong>{displayedWeeklyMetric.overdueApplicationActions}</strong></div>
                  <div className={`weekly-risk-row ${displayedWeeklyMetric.overdueOutreachFollowUps > 0 ? "attention" : ""}`}><span>Overdue outreach follow-ups</span><strong>{displayedWeeklyMetric.overdueOutreachFollowUps}</strong></div>
                </div>
              </div>
              <form className="weekly-reflection-editor" onSubmit={saveWeeklyReflection}>
                <div className="section-heading"><div><p className="section-label">Close the loop</p><h3>Weekly reflection</h3></div>
                  <small>{selectedWeeklyReview ? "Adds a new revision to the selected week" : "Saving also freezes the current week’s evidence"}</small></div>
                <div className="weekly-reflection-inputs">
                  <label><span>What worked this week?</span><textarea rows={4} value={weeklyWorked} onChange={(event) => setWeeklyWorked(event.target.value)}
                    placeholder="Capture the choices, habits, or actions worth repeating." /></label>
                  <label><span>What could improve next week?</span><textarea rows={4} value={weeklyImprove} onChange={(event) => setWeeklyImprove(event.target.value)}
                    placeholder="Name one or two concrete adjustments for the next cycle." /></label>
                </div>
                <div className="weekly-reflection-actions"><p>Reflections are append-only, so earlier thinking and the evidence behind it stay intact.</p>
                  <button className="primary-button" disabled={weeklyReflectionSaving || (!weeklyWorked.trim() && !weeklyImprove.trim())}>{weeklyReflectionSaving ? "Saving…" : "Preserve reflection"}</button></div>
              </form>
              <div className="weekly-review-history"><div className="section-heading"><div><p className="section-label">Preserved history</p><h3>Review snapshots</h3></div>
                <button className={`text-button ${selectedWeeklyReviewId === null ? "active" : ""}`} onClick={() => setSelectedWeeklyReviewId(null)}>Live preview</button></div>
                {weeklyReviews?.reviews.length === 0 ? <p className="inline-empty">No frozen weekly review yet. The current preview remains live until you create one.</p>
                  : <div className="weekly-history-list">{weeklyReviews?.reviews.map((review) => <button type="button" className={selectedWeeklyReviewId === review.id ? "selected" : ""}
                    onClick={() => setSelectedWeeklyReviewId(review.id)} key={review.id}><span>{formatDate(review.weekStart)}–{formatDate(review.weekEnd)}</span>
                    <strong>{review.metrics.applicationsSubmitted} applications · {review.metrics.preparationMinutes} prep min</strong>
                    <small>{review.completeWeek ? "Complete week" : `Through ${formatDate(review.effectiveThrough)}`} · {review.revisions.length} reflection revision{review.revisions.length === 1 ? "" : "s"}</small></button>)}</div>}
              </div>
              {selectedWeeklyReview && <div className="weekly-reflection"><div className="section-heading"><div><p className="section-label">Append-only reflection</p><h3>{selectedWeeklyReview.revisions.length ? `Revision ${selectedWeeklyReview.revisions[0].revisionNumber}` : "Add your review narrative"}</h3></div>
                <button className="secondary-button" onClick={() => setWeeklyRevisionTarget(selectedWeeklyReview)}>+ Add revision</button></div>
                {selectedWeeklyReview.revisions.length === 0 ? <p className="inline-empty">Metrics are preserved. Add wins, challenges, your reflection, and next-week adjustments when ready.</p>
                  : <WeeklyReflection revision={selectedWeeklyReview.revisions[0]} />}
              </div>}
            </>}
          </section>
          </div>}

          {activeWorkspace === "overview" && <section className="roadmap-strip" id="roadmap">
            <div><p className="eyebrow">Milestone 5D · focused workspaces</p><h2>One command center, six deliberate views, and less cognitive overhead.</h2></div>
            <div className="roadmap-items"><span><b>LIVE</b> Executive summary</span><span><b>LIVE</b> Opportunity intake</span>
              <span><b>LIVE</b> Network execution</span><span><b>LIVE</b> Market strategy</span><span><b>LIVE</b> Sprint preparation</span><span><b>LIVE</b> Weekly review</span></div>
          </section>}
        </div>
      </section>

      {opportunityModal && <OpportunityModal connected={connected} onClose={() => setOpportunityModal(false)}
        onSaved={async () => { setOpportunityModal(false); setToast({ kind: "success", message: "Opportunity added to your inbox." }); await refresh(); }}
        onError={(message) => setToast({ kind: "error", message })} />}
      {inboxIntakeOpen && <InboxIntakeModal onClose={() => setInboxIntakeOpen(false)}
        onSaved={async (result) => { setInboxIntakeOpen(false); setToast({ kind: "success", message: result.replayed
          ? "That exact source was already captured; the existing review item is shown."
          : `${result.item.candidateCount} candidate${result.item.candidateCount === 1 ? "" : "s"} added to Inbox review.` }); await refresh(); }}
        onError={(message) => setToast({ kind: "error", message })} />}
      {inboxEditCandidate && <InboxCandidateModal candidate={inboxEditCandidate} onClose={() => setInboxEditCandidate(null)}
        onSaved={async () => { setInboxEditCandidate(null); setToast({ kind: "success", message: "Candidate fields updated and ready for review." }); await refresh(); }}
        onError={(message) => setToast({ kind: "error", message })} />}
      {inboxAssistanceCandidate && <InboxAssistanceModal candidate={inboxAssistanceCandidate}
        onClose={() => setInboxAssistanceCandidate(null)}
        onSaved={async (message) => { setToast({ kind: "success", message }); await refresh(); }}
        onError={(message) => setToast({ kind: "error", message })} />}
      {duplicateReviewCandidate && <DuplicateReviewModal candidate={duplicateReviewCandidate} onClose={() => setDuplicateReviewCandidate(null)}
        onSaved={async (message) => { setDuplicateReviewCandidate(null); setToast({ kind: "success", message }); await refresh(); }}
        onError={(message) => setToast({ kind: "error", message })} />}
      {weeklyRevisionTarget && <WeeklyReviewRevisionModal review={weeklyRevisionTarget} onClose={() => setWeeklyRevisionTarget(null)}
        onSaved={async () => { setWeeklyRevisionTarget(null); setToast({ kind: "success", message: "A new reflection revision was preserved without changing the metric snapshot." }); await refresh(); }}
        onError={(message) => setToast({ kind: "error", message })} />}
      {weeklyAssistanceTarget && <WeeklyAssistanceModal review={weeklyAssistanceTarget}
        onClose={() => setWeeklyAssistanceTarget(null)}
        onSaved={async () => { setWeeklyAssistanceTarget(null); setToast({ kind: "success", message: "The reviewed AI draft was preserved as a new append-only reflection revision." }); await refresh(); }}
        onError={(message) => setToast({ kind: "error", message })} />}
      {applicationTarget && <ApplicationModal opportunity={applicationTarget.opportunity} resumes={resumes}
        preferredResumeName={applicationTarget.preferredResumeName}
        onClose={() => setApplicationTarget(null)}
        onSaved={async () => { setApplicationTarget(null); setToast({ kind: "success", message: "Application created and its exact resume was preserved in the local evidence archive." }); await refresh(); }}
        onError={(message) => setToast({ kind: "error", message })} />}
      {applicationFollowUpTarget && <ApplicationFollowUpModal item={applicationFollowUpTarget}
        onClose={() => setApplicationFollowUpTarget(null)}
        onSaved={async (message) => { setApplicationFollowUpTarget(null); setToast({ kind: "success", message }); await refresh(); }}
        onError={(message) => setToast({ kind: "error", message })} />}
      {outreachFollowUpTarget && <OutreachFollowUpModal item={outreachFollowUpTarget}
        onClose={() => setOutreachFollowUpTarget(null)}
        onSaved={async (message) => { setOutreachFollowUpTarget(null); setToast({ kind: "success", message }); await refresh(); }}
        onError={(message) => setToast({ kind: "error", message })} />}
      {archiveTarget && <ArchiveOpeningModal opening={archiveTarget} onClose={() => setArchiveTarget(null)}
        onSaved={async () => { setArchiveTarget(null); setToast({ kind: "success", message: `${archiveTarget.companyName} was archived and removed from your active view.` }); await refresh(); }}
        onError={(message) => setToast({ kind: "error", message })} />}
      {referralTarget && <ReferralModal opening={referralTarget} contacts={contacts} onClose={() => setReferralTarget(null)}
        onSaved={async () => { setReferralTarget(null); setToast({ kind: "success", message: "Referral activity added with its follow-up plan." }); await refresh(); }}
        onError={(message) => setToast({ kind: "error", message })} />}
      {referralDiscoveryDialog && referralOpening && <ReferralDiscoveryModal dialog={referralDiscoveryDialog} opening={referralOpening}
        onClose={() => setReferralDiscoveryDialog(null)}
        onSaved={async (message) => { setReferralDiscoveryDialog(null); setToast({ kind: "success", message }); await refresh(); }}
        onError={(message) => setToast({ kind: "error", message })} />}
      {prepDialog && <PreparationDialog dialog={prepDialog} openings={feed.openings} onClose={() => setPrepDialog(null)}
        onSaved={async (message) => { setPrepDialog(null); setToast({ kind: "success", message }); await refresh(); }}
        onError={(message) => setToast({ kind: "error", message })} />}
      {taskDetailsTarget && <TaskDetailsDialog key={taskDetailsTarget.context.item.id} target={taskDetailsTarget} openings={feed.openings}
        onClose={() => setTaskDetailsTarget(null)}
        onSaved={async (message) => { setTaskDetailsTarget(null); setToast({ kind: "success", message }); await refresh(); }}
        onError={(message) => setToast({ kind: "error", message })} />}
      {storyDeleteTarget && <StoryDeleteModal target={storyDeleteTarget} onClose={() => setStoryDeleteTarget(null)}
        onSaved={async (message) => { setStoryDeleteTarget(null); setToast({ kind: "success", message }); await refresh(); }}
        onError={(message) => setToast({ kind: "error", message })} />}
      {sprintCloseTarget && <SprintCloseModal sprint={sprintCloseTarget} saving={sprintLifecycleBusy}
        onClose={() => setSprintCloseTarget(null)} onConfirm={() => void closePreparationSprint(sprintCloseTarget)} />}
      {skillDialog && <SkillEvidenceDialog dialog={skillDialog} openings={feed.openings} skills={skillOverview.skills}
        onClose={() => setSkillDialog(null)}
        onSaved={async (message) => { setSkillDialog(null); setToast({ kind: "success", message }); await refresh(); setMarketSignalRefreshVersion((current) => current + 1); }}
        onError={(message) => setToast({ kind: "error", message })} />}
      {backlogDialog && <PersonalBacklogDialog dialog={backlogDialog} preparation={preparation}
        onClose={() => setBacklogDialog(null)}
        onSaved={async (message) => { setBacklogDialog(null); setToast({ kind: "success", message }); await refresh(); }}
        onError={(message) => setToast({ kind: "error", message })} />}
      {calendarOpen && <CalendarWorkspace events={calendarEvents} dueReminders={dueCalendarReminders} connected={connected}
        onClose={() => setCalendarOpen(false)}
        onSaved={async (message) => { setToast({ kind: "success", message }); await refresh(); }}
        onError={(message) => setToast({ kind: "error", message })} />}
      {interviewApplicationId && applications.find((a) => a.id === interviewApplicationId) && <InterviewApplicationDialog
        key={interviewApplicationId} application={applications.find((a) => a.id === interviewApplicationId)!} calendarEvents={calendarEvents}
        onClose={() => setInterviewApplicationId(null)} onSaved={async () => { await refresh(); }} />}
      {detail && <OpeningDetailModal detail={detail} onClose={() => setDetail(null)} onStart={() => startFromOpening(detail.opening)} />}
      {detailLoading && <div className="detail-loading" role="status">Loading opening evidence…</div>}
      <Scratchpad connected={connected}
        onSaved={(message) => setToast({ kind: "success", message })}
        onError={(message) => setToast({ kind: "error", message })} />
      {toast && <div className={`toast ${toast.kind}`} role="status">{toast.message}</div>}
    </main>
  );
}

function MetricCard({ label, value, note, tone }: { label: string; value: number; note: string; tone: string }) {
  return <article className={`metric-card tone-${tone}`}><p>{label}</p><div><strong>{String(value).padStart(2, "0")}</strong><span>{note}</span></div></article>;
}
function PanelHeader({ eyebrow, title, count, action }: { eyebrow: string; title: string; count?: number; action?: React.ReactNode }) {
  return <header className="panel-header"><div><p className="eyebrow">{eyebrow}</p><h2>{title}{count !== undefined && count > 0 ? <span className="count-badge">{count}</span> : null}</h2></div>{action}</header>;
}
function WorkspaceIntro({ eyebrow, title, description }: { eyebrow: string; title: string; description: string }) {
  return <header className="workspace-intro"><div><p className="eyebrow">{eyebrow}</p><h1>{title}</h1></div><p>{description}</p></header>;
}
function EmptyState({ text }: { text: string }) { return <div className="empty-state"><span>✓</span><p>{text}</p></div>; }

function Scratchpad({ connected, onSaved, onError }: { connected: boolean; onSaved: (message: string) => void; onError: (message: string) => void }) {
  const storageKey = "job-search-command-center.scratch-note.v1";
  const [open, setOpen] = useState(false);
  const [note, setNote] = useState(() => {
    if (typeof window === "undefined") return "";
    try { return window.localStorage.getItem(storageKey) ?? ""; } catch { return ""; }
  });
  const [saving, setSaving] = useState(false);
  const [lastSnapshot, setLastSnapshot] = useState<SavedNote | null>(null);
  const [confirmClear, setConfirmClear] = useState(false);
  useEffect(() => {
    const timer = window.setTimeout(() => {
      try { window.localStorage.setItem(storageKey, note); } catch { /* Browser storage can be unavailable in private contexts. */ }
    }, 250);
    return () => window.clearTimeout(timer);
  }, [note]);
  async function saveSnapshot() {
    if (!note.trim()) return;
    setSaving(true);
    try {
      const saved = await api<SavedNote>("/api/v1/notes", { method: "POST", body: JSON.stringify({ content: note }) });
      setLastSnapshot(saved); onSaved(`Note saved to ${saved.storedPath}.`);
    } catch (error) { onError(error instanceof Error ? error.message : "Could not save the note to disk."); }
    finally { setSaving(false); }
  }
  function clearNote() {
    setNote(""); setLastSnapshot(null); setConfirmClear(false);
    try { window.localStorage.removeItem(storageKey); } catch { /* The empty state still clears this session. */ }
  }
  return <aside className={`scratchpad ${open ? "open" : ""}`} aria-label="Quick notes scratchpad">
    {open && <section className="scratchpad-panel"><header><div><p className="eyebrow">Quick capture</p><h2>Daily scratchpad</h2></div>
      <button type="button" className="close-button" onClick={() => setOpen(false)} aria-label="Collapse notes">×</button></header>
      <textarea aria-label="Scratchpad note" value={note} maxLength={100000} onChange={(event) => { setNote(event.target.value); setLastSnapshot(null); setConfirmClear(false); }}
        placeholder="Jot down an idea, reminder, interview question, or a short list…" />
      <div className="scratchpad-status"><span><i />Autosaved in this browser</span><small>{note.length.toLocaleString("en-IN")} characters</small></div>
      {lastSnapshot && <p className="scratchpad-snapshot">Saved snapshot · <strong>{lastSnapshot.filename}</strong></p>}
      <footer><div>{confirmClear ? <><span>Clear this scratchpad?</span><button type="button" className="text-button" onClick={() => setConfirmClear(false)}>Keep</button>
        <button type="button" className="danger-button" onClick={clearNote}>Clear</button></> : <button type="button" className="text-button destructive" disabled={!note} onClick={() => setConfirmClear(true)}>Clear</button>}</div>
        <button type="button" className="primary-button" disabled={!connected || saving || !note.trim()} onClick={() => void saveSnapshot()}>{saving ? "Saving…" : "Save to notes"}</button></footer>
    </section>}
    <button type="button" className="scratchpad-toggle" onClick={() => setOpen((current) => !current)} aria-label={open ? "Collapse quick notes" : "Open quick notes"} aria-expanded={open}>
      <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6 3.5h9.5L19 7v13.5H6zM15.5 3.5V7H19M9 11h7M9 14h7M9 17h5" /></svg>
      {!open && note.trim() && <span aria-hidden="true" />}
    </button>
  </aside>;
}

const calendarEventLabels: Record<CalendarEventType, string> = {
  INTERVIEW: "Interview", RECRUITER_CALL: "Recruiter call", NETWORKING: "Networking",
  MEETING: "Meeting", DEADLINE: "Deadline", PERSONAL: "Personal",
};

function startOfCalendarDay(value: Date) {
  const date = new Date(value); date.setHours(0, 0, 0, 0); return date;
}
function startOfCalendarWeek(value: Date) {
  const date = startOfCalendarDay(value); date.setDate(date.getDate() - ((date.getDay() + 6) % 7)); return date;
}
function addCalendarDays(value: Date, amount: number) {
  const date = new Date(value); date.setDate(date.getDate() + amount); return date;
}
function calendarDateKey(value: Date | string) {
  const date = value instanceof Date ? value : new Date(value);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}
function calendarTime(value: string) {
  return new Date(value).toLocaleTimeString("en-IN", { hour: "numeric", minute: "2-digit", hour12: true });
}
function calendarDuration(event: CalendarEvent) {
  const minutes = Math.round((Date.parse(event.endsAt) - Date.parse(event.startsAt)) / 60_000);
  return minutes >= 60 && minutes % 60 === 0 ? `${minutes / 60} hr${minutes === 60 ? "" : "s"}` : `${minutes} min`;
}
function localDateInput(value: Date | string) { return calendarDateKey(value); }
function localTimeInput(value: Date | string) {
  const date = value instanceof Date ? value : new Date(value);
  return `${String(date.getHours()).padStart(2, "0")}:${String(date.getMinutes()).padStart(2, "0")}`;
}
function calendarNoticeKey(event: CalendarEvent) { return `${event.id}:${event.startsAt}`; }
function readDismissedCalendarNoticeKeys() {
  if (typeof window === "undefined") return [];
  try {
    const value: unknown = JSON.parse(window.localStorage.getItem(CALENDAR_NOTICE_STORAGE_KEY) ?? "[]");
    return Array.isArray(value) ? value.filter((item): item is string => typeof item === "string") : [];
  } catch { return []; }
}
function nearTermCalendarEvents(events: CalendarEvent[]) {
  const now = new Date();
  const today = calendarDateKey(now);
  const tomorrow = calendarDateKey(addCalendarDays(now, 1));
  return [...events].filter((event) => Date.parse(event.endsAt) >= now.getTime()
    && !["COMPLETED", "CANCELLED"].includes(event.interviewStatus ?? "")
    && [today, tomorrow].includes(calendarDateKey(event.startsAt)))
    .sort((left, right) => left.startsAt.localeCompare(right.startsAt));
}

function CalendarNoticePopover({ events, onOpen, onDismiss }: { events: CalendarEvent[]; onOpen: () => void; onDismiss: () => void }) {
  const today = calendarDateKey(new Date());
  const visibleEvents = events.slice(0, 4);
  return <aside className="calendar-notice-popover" aria-label="Calendar events today and tomorrow">
    <header><div><span>Calendar & interviews</span><strong>{events.length} upcoming</strong></div>
      <button type="button" onClick={onDismiss} aria-label="Dismiss upcoming calendar events">×</button></header>
    <div>{visibleEvents.map((event) => <button type="button" className={`calendar-notice-event event-${event.eventType.toLowerCase()}`} onClick={onOpen} key={event.id}>
      <i /><div><span>{calendarDateKey(event.startsAt) === today ? "Today" : "Tomorrow"} · {calendarEventLabels[event.eventType]}</span>
        <strong>{event.title}</strong><small>{event.location || calendarDuration(event)}</small></div>
      <time>{calendarTime(event.startsAt)}–{calendarTime(event.endsAt)}</time>
    </button>)}</div>
    {events.length > visibleEvents.length && <button type="button" className="calendar-notice-more" onClick={onOpen}>+{events.length - visibleEvents.length} more in calendar</button>}
  </aside>;
}

function CalendarWorkspace({ events, dueReminders, connected, onClose, onSaved, onError }: {
  events: CalendarEvent[]; dueReminders: CalendarEvent[]; connected: boolean; onClose: () => void;
  onSaved: (message: string) => Promise<void>; onError: (message: string) => void;
}) {
  const [view, setView] = useState<CalendarView>("MONTH");
  const dialogRef = useAccessibleDialog(onClose);
  const [anchor, setAnchor] = useState(() => startOfCalendarDay(new Date()));
  const [editor, setEditor] = useState<{ event?: CalendarEvent; startsAt: Date } | null>(null);
  const [reminderBusyId, setReminderBusyId] = useState<string | null>(null);
  const monthStart = new Date(anchor.getFullYear(), anchor.getMonth(), 1);
  const monthGridStart = startOfCalendarWeek(monthStart);
  const monthDays = Array.from({ length: 42 }, (_, index) => addCalendarDays(monthGridStart, index));
  const weekStart = startOfCalendarWeek(anchor);
  const weekDays = Array.from({ length: 7 }, (_, index) => addCalendarDays(weekStart, index));
  const dayEvents = (date: Date) => events.filter((event) => calendarDateKey(event.startsAt) === calendarDateKey(date))
    .sort((left, right) => left.startsAt.localeCompare(right.startsAt));
  const periodLabel = view === "MONTH" ? anchor.toLocaleDateString("en-IN", { month: "long", year: "numeric" })
    : view === "WEEK" ? `${weekStart.toLocaleDateString("en-IN", { day: "numeric", month: "short" })} – ${addCalendarDays(weekStart, 6).toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" })}`
      : anchor.toLocaleDateString("en-IN", { weekday: "long", day: "numeric", month: "long", year: "numeric" });
  function shiftPeriod(direction: number) {
    const next = new Date(anchor);
    if (view === "MONTH") next.setMonth(next.getMonth() + direction);
    else next.setDate(next.getDate() + direction * (view === "WEEK" ? 7 : 1));
    setAnchor(next);
  }
  function startOn(date: Date, hour = 9) { const next = new Date(date); next.setHours(hour, 0, 0, 0); return next; }
  async function dismissReminder(event: CalendarEvent) {
    setReminderBusyId(event.id);
    try {
      await api<CalendarEvent>(`/api/v1/calendar/events/${event.id}/reminder/dismiss`, { method: "POST" });
      await onSaved(`Reminder dismissed for “${event.title}”.`);
    } catch (error) { onError(error instanceof Error ? error.message : "Could not dismiss the reminder."); }
    finally { setReminderBusyId(null); }
  }
  const eventButton = (event: CalendarEvent, compact = false) => <button type="button" key={event.id}
    className={`calendar-event-chip event-${event.eventType.toLowerCase()} ${compact ? "compact" : ""}`}
    onClick={(click) => { click.stopPropagation(); setEditor({ event, startsAt: new Date(event.startsAt) }); }}>
    <span>{calendarTime(event.startsAt)}</span><strong>{event.interviewStatus === "CANCELLED" ? "Cancelled · " : event.interviewStatus === "COMPLETED" ? "Completed · " : ""}{event.title}</strong>{!compact && <small>{calendarDuration(event)}</small>}
  </button>;
  return <div className="calendar-backdrop"><section ref={dialogRef} tabIndex={-1} className="calendar-workspace" role="dialog" aria-modal="true" aria-labelledby="personal-calendar-title">
    <header className="calendar-workspace-head"><div><p className="eyebrow">Personal calendar</p><h2 id="personal-calendar-title">Meetings, interviews & events</h2>
      <p>Keep important commitments visible alongside your job-search operating plan.</p></div>
      <div><button type="button" className="primary-button" disabled={!connected} onClick={() => setEditor({ startsAt: startOn(anchor) })}>+ New event</button>
        <button type="button" className="close-button" onClick={onClose} aria-label="Close calendar">×</button></div></header>
    {dueReminders.length > 0 && <aside className="calendar-reminders" aria-label="Due reminders"><div><span>{dueReminders.length}</span><strong>Reminder{dueReminders.length === 1 ? "" : "s"} due</strong></div>
      <div>{dueReminders.map((event) => <article key={event.id}><button type="button" onClick={() => setEditor({ event, startsAt: new Date(event.startsAt) })}><strong>{event.title}</strong><span>{calendarTime(event.startsAt)} · {new Date(event.startsAt).toLocaleDateString("en-IN", { day: "numeric", month: "short" })}</span></button>
        <button type="button" className="text-button" disabled={reminderBusyId === event.id} onClick={() => void dismissReminder(event)}>Dismiss</button></article>)}</div></aside>}
    <div className="calendar-toolbar"><div className="calendar-period-nav"><button type="button" onClick={() => shiftPeriod(-1)} aria-label="Previous period">‹</button>
      <button type="button" className="calendar-today-button" onClick={() => setAnchor(startOfCalendarDay(new Date()))}>Today</button>
      <button type="button" onClick={() => shiftPeriod(1)} aria-label="Next period">›</button><strong>{periodLabel}</strong></div>
      <div className="calendar-view-switch" aria-label="Calendar view">{(["MONTH", "WEEK", "DAY"] as CalendarView[]).map((option) => <button type="button" key={option}
        className={view === option ? "active" : ""} onClick={() => setView(option)}>{option.toLowerCase()}</button>)}</div></div>
    <div className="calendar-content">
      {view === "MONTH" && <div className="calendar-month"><div className="calendar-weekdays">{["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"].map((day) => <span key={day}>{day}</span>)}</div>
        <div className="calendar-month-grid">{monthDays.map((date) => { const dateEvents = dayEvents(date); const today = calendarDateKey(date) === calendarDateKey(new Date());
          return <div className={`${date.getMonth() !== anchor.getMonth() ? "outside" : ""} ${today ? "today" : ""}`} key={calendarDateKey(date)} onDoubleClick={() => setEditor({ startsAt: startOn(date) })}>
            <button type="button" className="calendar-day-number" onClick={() => { setAnchor(date); setView("DAY"); }}>{date.getDate()}</button>
            <div>{dateEvents.slice(0, 3).map((event) => eventButton(event, true))}{dateEvents.length > 3 && <button type="button" className="calendar-more" onClick={() => { setAnchor(date); setView("DAY"); }}>+{dateEvents.length - 3} more</button>}</div>
          </div>; })}</div></div>}
      {view === "WEEK" && <div className="calendar-week">{weekDays.map((date) => { const dateEvents = dayEvents(date); const today = calendarDateKey(date) === calendarDateKey(new Date());
        return <section className={today ? "today" : ""} key={calendarDateKey(date)}><header><span>{date.toLocaleDateString("en-IN", { weekday: "short" })}</span><button type="button" onClick={() => { setAnchor(date); setView("DAY"); }}>{date.getDate()}</button></header>
          <div>{dateEvents.length ? dateEvents.map((event) => eventButton(event)) : <button type="button" className="calendar-empty-day" onClick={() => setEditor({ startsAt: startOn(date) })}>+ Add event</button>}</div></section>; })}</div>}
      {view === "DAY" && <div className="calendar-day"><header><div><span>{anchor.toLocaleDateString("en-IN", { weekday: "long" })}</span><strong>{anchor.getDate()}</strong></div>
        <button type="button" className="secondary-button" disabled={!connected} onClick={() => setEditor({ startsAt: startOn(anchor) })}>+ Add to this day</button></header>
        {dayEvents(anchor).length ? <div className="calendar-day-agenda">{dayEvents(anchor).map((event) => <button type="button" className={`calendar-agenda-event event-${event.eventType.toLowerCase()}`} key={event.id} onClick={() => setEditor({ event, startsAt: new Date(event.startsAt) })}>
          <time><strong>{calendarTime(event.startsAt)}</strong><span>{calendarTime(event.endsAt)}</span></time><i /><div><small>{calendarEventLabels[event.eventType]} · {calendarDuration(event)}{event.interviewStatus ? ` · ${formatEnum(event.interviewStatus)}` : ""}</small><strong>{event.title}</strong>
            <p>{event.location || event.description || "No additional details"}</p></div></button>)}</div>
          : <div className="calendar-day-empty"><strong>No events planned.</strong><p>This day is clear. Add an interview, meeting, call, or deadline when one is confirmed.</p></div>}
      </div>}
    </div>
    <footer className="calendar-legend">{(Object.keys(calendarEventLabels) as CalendarEventType[]).map((type) => <span className={`event-${type.toLowerCase()}`} key={type}><i />{calendarEventLabels[type]}</span>)}</footer>
    {editor && <CalendarEventDialog target={editor} connected={connected} onClose={() => setEditor(null)}
      onSaved={async (message) => { setEditor(null); await onSaved(message); }} onError={onError} />}
  </section></div>;
}

function InterviewApplicationDialog({ application, calendarEvents, onClose, onSaved }: {
  application: ApplicationRecord; calendarEvents: CalendarEvent[]; onClose: () => void; onSaved: () => Promise<void>;
}) {
  const [rounds, setRounds] = useState<InterviewRound[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [editing, setEditing] = useState<InterviewRound | "NEW" | null>(null);
  const [reload, setReload] = useState(0);
  useEffect(() => {
    let active = true;
    api<InterviewRound[]>(`/api/v1/applications/${application.id}/interviews`)
      .then((result) => { if (active) { setRounds(result); setError(""); } })
      .catch((reason) => { if (active) setError(reason instanceof Error ? reason.message : "Could not load interview rounds."); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [application.id, reload]);
  return <Modal title="Application details & interviews" subtitle={`${application.companyName} · ${application.roleTitle}`} onClose={onClose} wide>
    <div className="interview-dialog-body">
    <div className="interview-application-summary"><span className="status-pill">{stageLabels[application.stage]}</span>
      <span>Applied {application.appliedOn ? formatDate(application.appliedOn) : "—"}</span><span>Resume: {application.resumeName}</span></div>
    <p className="form-hint">Track each conversation, preparation and feedback. Round outcomes never change your application stage automatically.</p>
    {error && <p role="alert" className="interview-error">{error} <button type="button" className="text-button" onClick={() => { setLoading(true); setReload((value) => value + 1); }}>Retry / reload</button></p>}
    {editing ? <InterviewRoundForm key={editing === "NEW" ? "new" : editing.id} round={editing === "NEW" ? null : editing}
      applicationId={application.id} calendarEvents={calendarEvents} onCancel={() => setEditing(null)}
      onSaved={async () => { setEditing(null); setLoading(true); setReload((value) => value + 1); await onSaved(); }} />
      : <><div className="interview-round-heading"><h3>Interview rounds</h3><div className="interview-round-actions"><button type="button" className="text-button" disabled={loading} onClick={() => { setLoading(true); setReload((value) => value + 1); }}>Reload</button><button type="button" className="primary-button" disabled={loading || !!error} onClick={() => setEditing("NEW")}>+ Add interview round</button></div></div>
        {loading ? <p role="status">Loading interview rounds…</p> : rounds.length === 0 ? <p className="inline-empty">No rounds yet. Add the recruiter screen or your next interview when you’re ready.</p>
          : <div className="interview-round-list">{rounds.map((round, index) => <article key={round.id}>
            <header><div><small>Round {index + 1} · {formatEnum(round.type)}</small><h3>{round.title}</h3></div><button type="button" className="secondary-button" onClick={() => setEditing(round)}>Edit round</button></header>
            <div className="interview-round-meta"><span className="status-pill">{formatEnum(round.status)}</span><span>{formatEnum(round.outcome)}</span>
              {round.startsAt && <time>{new Date(round.startsAt).toLocaleString("en-IN", { dateStyle: "medium", timeStyle: "short" })} · {round.durationMinutes} min</time>}</div>
            {round.location && <p>{round.location}</p>}
            {round.meetingUrl && /^https?:\/\//i.test(round.meetingUrl) && <a href={round.meetingUrl} target="_blank" rel="noreferrer">Open meeting link ↗</a>}
            {(round.preparationNotes || round.debrief) && <details><summary>Preparation & debrief</summary>
              {round.preparationNotes && <section><h4>Preparation</h4><p className="interview-notes">{round.preparationNotes}</p></section>}
              {round.debrief && <section><h4>Debrief / feedback</h4><p className="interview-notes">{round.debrief}</p></section>}</details>}
          </article>)}</div>}</>}
    </div>
  </Modal>;
}

function InterviewRoundForm({ round, applicationId, calendarEvents, onCancel, onSaved }: {
  round: InterviewRound | null; applicationId: string; calendarEvents: CalendarEvent[]; onCancel: () => void; onSaved: () => Promise<void>;
}) {
  const [status, setStatus] = useState<InterviewRound["status"]>(round?.status ?? "AWAITING_SCHEDULING");
  const [existingEventId, setExistingEventId] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [clearScheduleConfirmed, setClearScheduleConfirmed] = useState(false);
  const clearsSchedule = status === "AWAITING_SCHEDULING" && !!round?.calendarEventId;
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setSaving(true); setError("");
    const data = new FormData(event.currentTarget);
    const scheduled = status !== "AWAITING_SCHEDULING" && !existingEventId;
    const date = String(data.get("startsAt") ?? "");
    const payload = { title: data.get("title"), type: data.get("type"), status, outcome: data.get("outcome"),
      preparationNotes: data.get("preparationNotes") || null, debrief: data.get("debrief") || null,
      startsAt: scheduled && date ? new Date(date).toISOString() : null,
      durationMinutes: scheduled && date ? Number(data.get("durationMinutes")) : null,
      location: data.get("location") || null, meetingUrl: data.get("meetingUrl") || null,
      reminderMinutesBefore: data.get("reminderMinutesBefore") ? Number(data.get("reminderMinutesBefore")) : null,
      existingEventId: status !== "AWAITING_SCHEDULING" ? existingEventId || null : null,
      version: round?.version ?? null, calendarVersion: round?.calendarVersion ?? null };
    try {
      await api(`/api/v1/applications/${applicationId}/interviews${round ? `/${round.id}` : ""}`, { method: round ? "PUT" : "POST", body: JSON.stringify(payload) });
      await onSaved();
    } catch (reason) { setError(reason instanceof Error ? reason.message : "Could not save the round. Reload if its calendar event changed."); }
    finally { setSaving(false); }
  }
  return <form className="modal-form interview-form" onSubmit={submit}>
    <h3>{round ? "Edit interview round" : "Add interview round"}</h3>
    {error && <p role="alert" className="interview-error">{error}</p>}
    <Field label="Round title"><input name="title" required maxLength={240} defaultValue={round?.title ?? ""} placeholder="e.g. Coding interview — round 1" /></Field>
    <div className="form-grid"><Field label="Round type"><select name="type" defaultValue={round?.type ?? "RECRUITER"}>
      {["RECRUITER", "CODING", "SYSTEM_DESIGN", "BEHAVIORAL", "HIRING_MANAGER", "CUSTOM"].map((type) => <option value={type} key={type}>{formatEnum(type)}</option>)}</select></Field>
      <Field label="Status"><select value={status} onChange={(event) => setStatus(event.target.value as InterviewRound["status"])}>
        {["AWAITING_SCHEDULING", "SCHEDULED", "COMPLETED", "CANCELLED"].map((value) => <option key={value} value={value}>{formatEnum(value)}</option>)}</select></Field></div>
    {clearsSchedule && <label className="delete-confirmation"><input type="checkbox" required checked={clearScheduleConfirmed} onChange={(event) => setClearScheduleConfirmed(event.target.checked)} />Remove this round’s calendar event and clear its schedule. Keep its notes and outcome.</label>}
    {status !== "AWAITING_SCHEDULING" && <fieldset className="interview-schedule"><legend>Calendar schedule</legend>
      {!round && <Field label="Calendar event"><select value={existingEventId} onChange={(event) => setExistingEventId(event.target.value)}><option value="">Create a new event</option>
        {calendarEvents.filter((event) => !event.interviewRoundId).map((event) => <option key={event.id} value={event.id}>{event.title} · {new Date(event.startsAt).toLocaleString("en-IN")}</option>)}</select></Field>}
      {existingEventId ? <p className="form-hint">Uses the existing event’s time, duration, location and reminder without creating a duplicate.</p> : <>
        <div className="form-grid"><Field label="Date & start time (your local time)"><input name="startsAt" type="datetime-local" required={status === "SCHEDULED" || !!round?.calendarEventId} defaultValue={round?.startsAt ? `${localDateInput(round.startsAt)}T${localTimeInput(round.startsAt)}` : ""} /></Field>
          <Field label="Duration (minutes)"><input name="durationMinutes" type="number" min={1} max={1440} defaultValue={round?.durationMinutes ?? 60} /></Field></div>
        <div className="form-grid"><Field label="Location"><input name="location" maxLength={500} defaultValue={round?.location ?? ""} /></Field>
          <Field label="Reminder (minutes before, optional)"><input name="reminderMinutesBefore" type="number" min={1} max={10080} defaultValue={round?.reminderMinutesBefore ?? ""} /></Field></div>
        <Field label="Meeting link (optional)"><input name="meetingUrl" type="url" maxLength={2000} defaultValue={round?.meetingUrl ?? ""} /></Field>
      </>}
      {(status === "COMPLETED" || status === "CANCELLED") && <p className="form-hint">The calendar entry stays as history; reminders are dismissed.</p>}
    </fieldset>}
    <Field label="Preparation notes"><textarea name="preparationNotes" rows={3} maxLength={10000} defaultValue={round?.preparationNotes ?? ""} placeholder="Topics to review and questions to ask" /></Field>
    <Field label="Outcome"><select name="outcome" defaultValue={round?.outcome ?? "NOT_RECORDED"}>
      {["NOT_RECORDED", "AWAITING_FEEDBACK", "ADVANCED", "REJECTED"].map((value) => <option key={value} value={value}>{formatEnum(value)}</option>)}</select></Field>
    <Field label="Debrief / feedback"><textarea name="debrief" rows={4} maxLength={10000} defaultValue={round?.debrief ?? ""} placeholder="What happened, feedback received, and what to improve" /></Field>
    <ModalActions onClose={onCancel} saving={saving} disabled={clearsSchedule && !clearScheduleConfirmed} label="Save round" />
  </form>;
}

function CalendarEventDialog({ target, connected, onClose, onSaved, onError }: {
  target: { event?: CalendarEvent; startsAt: Date }; connected: boolean; onClose: () => void;
  onSaved: (message: string) => Promise<void>; onError: (message: string) => void;
}) {
  const [saving, setSaving] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const dialogRef = useAccessibleDialog(onClose);
  const event = target.event;
  const defaultDuration = event ? Math.max(1, Math.round((Date.parse(event.endsAt) - Date.parse(event.startsAt)) / 60_000)) : 60;
  async function submit(formEvent: FormEvent<HTMLFormElement>) {
    formEvent.preventDefault(); setSaving(true);
    const data = new FormData(formEvent.currentTarget);
    const startsAt = new Date(`${data.get("date")}T${data.get("time")}:00`);
    const durationMinutes = Number(data.get("durationMinutes"));
    const endsAt = new Date(startsAt.getTime() + durationMinutes * 60_000);
    const reminder = String(data.get("reminderMinutesBefore") ?? "");
    const payload = { title: data.get("title"), eventType: data.get("eventType"), description: data.get("description") || null,
      location: data.get("location") || null, meetingUrl: data.get("meetingUrl") || null, startsAt: startsAt.toISOString(), endsAt: endsAt.toISOString(),
      reminderMinutesBefore: reminder ? Number(reminder) : null };
    try {
      await api<CalendarEvent>(event ? `/api/v1/calendar/events/${event.id}` : "/api/v1/calendar/events", {
        method: event ? "PUT" : "POST", body: JSON.stringify(payload),
      });
      await onSaved(event ? `“${String(payload.title)}” was updated.` : `“${String(payload.title)}” was added to your calendar.`);
    } catch (error) { onError(error instanceof Error ? error.message : "Could not save the calendar event."); }
    finally { setSaving(false); }
  }
  async function remove() {
    if (!event) return; setSaving(true);
    try {
      const response = await fetch(`${API_BASE}/api/v1/calendar/events/${event.id}`, { method: "DELETE" });
      if (!response.ok) throw new Error("Could not delete the calendar event.");
      await onSaved(`“${event.title}” was removed from your calendar.`);
    } catch (error) { onError(error instanceof Error ? error.message : "Could not delete the calendar event."); }
    finally { setSaving(false); }
  }
  return <div className="calendar-editor-backdrop"><section ref={dialogRef} tabIndex={-1} className="calendar-editor" role="dialog" aria-modal="true" aria-labelledby="calendar-event-title">
    <header><div><p className="eyebrow">{event ? "Update commitment" : "Plan ahead"}</p><h2 id="calendar-event-title">{event ? "Edit event" : "Create calendar event"}</h2>
      <p>Record the time and context you will want at a glance when the day arrives.</p></div><button type="button" className="close-button" onClick={onClose} aria-label="Close event editor">×</button></header>
    <form className="modal-form" onSubmit={submit}><Field label="Event title"><input name="title" required maxLength={240} defaultValue={event?.title ?? ""} placeholder="Technical interview with Apple" /></Field>
      {event?.interviewRoundId && <p className="form-hint">Linked interview · {formatEnum(event.interviewStatus ?? "SCHEDULED")}. Schedule edits appear in the application’s Interviews view. Deleting this event clears its schedule but preserves the round, notes and outcome.</p>}
      <div className="form-grid"><Field label="Type"><select name="eventType" defaultValue={event?.eventType ?? "INTERVIEW"}>{(Object.keys(calendarEventLabels) as CalendarEventType[]).map((type) => <option value={type} key={type}>{calendarEventLabels[type]}</option>)}</select></Field>
        <Field label="Date"><input name="date" type="date" required defaultValue={localDateInput(event?.startsAt ?? target.startsAt)} /></Field></div>
      <div className="form-grid"><Field label="Start time"><input name="time" type="time" required defaultValue={localTimeInput(event?.startsAt ?? target.startsAt)} /></Field>
        <Field label="Duration (minutes)"><input name="durationMinutes" type="number" min={1} max={1440} required defaultValue={defaultDuration} /></Field></div>
      <div className="form-grid"><Field label="Location"><input name="location" maxLength={500} defaultValue={event?.location ?? ""} placeholder="Google Meet, office, or phone" /></Field>
        <Field label="Reminder"><select name="reminderMinutesBefore" defaultValue={event?.reminderMinutesBefore ? String(event.reminderMinutesBefore) : ""}><option value="">No reminder</option>{event?.reminderMinutesBefore && ![10, 30, 60, 120, 1440].includes(event.reminderMinutesBefore) && <option value={event.reminderMinutesBefore}>{event.reminderMinutesBefore} minutes before</option>}<option value="10">10 minutes before</option><option value="30">30 minutes before</option><option value="60">1 hour before</option><option value="120">2 hours before</option><option value="1440">1 day before</option></select></Field></div>
      <Field label="Meeting link"><input name="meetingUrl" type="url" maxLength={2000} defaultValue={event?.meetingUrl ?? ""} placeholder="https://…" /></Field>
      <Field label="Event details"><textarea name="description" rows={4} maxLength={10000} defaultValue={event?.description ?? ""} placeholder="Interview stage, people attending, topics to prepare, or notes to remember." /></Field>
      <div className={`modal-actions ${event ? "split-actions" : ""}`}>{event && <div className="calendar-delete-action">{confirmDelete ? <><span>Delete this event?</span><button type="button" className="text-button" onClick={() => setConfirmDelete(false)}>Keep</button><button type="button" className="danger-button" disabled={saving} onClick={() => void remove()}>Delete</button></>
        : <button type="button" className="text-button destructive" onClick={() => setConfirmDelete(true)}>Delete event</button>}</div>}
        <div><button type="button" className="text-button" onClick={onClose}>Cancel</button><button className="primary-button" disabled={!connected || saving}>{saving ? "Saving…" : event ? "Save changes" : "Add to calendar"}</button></div></div>
    </form>
  </section></div>;
}

type TodayScheduleEntry = { time: string; end: string; title: string; objective: string; context: string; status: string; tone: string };
function dailyFocusPlanForDate(value: string) {
  const day = new Date(`${value}T08:00:00`).getDay();
  return weeklyFocusPlans[day];
}

function TodaySchedule({ date, dailyActions, preparation, applicationFollowUps, outreach, focusPlan }: {
  date: string; dailyActions: DailyAction[]; preparation: PreparationOverview; applicationFollowUps: ActionItem[]; outreach: Outreach[];
  focusPlan: DailyFocusPlan;
}) {
  const allPrepTasks = flattenPreparationTasks(preparation.tracks);
  const sprintTasks = sprintTaskContexts(allPrepTasks, preparation.activeSprint?.tasks.map((task) => task.itemId) ?? []);
  const activeFallback = allPrepTasks.filter(({ item }) => !["BACKLOG", "COMPLETED", "SKIPPED"].includes(item.status));
  const prepQueue = [...sprintTasks, ...activeFallback.filter(({ item }) => !sprintTasks.some((task) => task.item.id === item.id))]
    .sort((left, right) => {
      const leftToday = left.item.scheduledFor === date || left.item.dueDate === date ? 0 : 1;
      const rightToday = right.item.scheduledFor === date || right.item.dueDate === date ? 0 : 1;
      return leftToday - rightToday || left.item.priority - right.item.priority
        || (left.item.dueDate ?? left.item.scheduledFor ?? "9999-12-31").localeCompare(right.item.dueDate ?? right.item.scheduledFor ?? "9999-12-31");
    });
  const actionQueue = [...dailyActions].sort((left, right) => Number(left.status === "DONE") - Number(right.status === "DONE") || left.priorityRank - right.priorityRank);
  const outreachQueue = outreach.filter((item) => !["CLOSED", "DECLINED", "REFERRED"].includes(item.status))
    .sort((left, right) => Number(right.overdue) - Number(left.overdue) || (left.followUpAt ?? "9999").localeCompare(right.followUpAt ?? "9999"));
  const followUpQueue = [...applicationFollowUps].sort((left, right) => left.nextActionAt.localeCompare(right.nextActionAt));
  let actionIndex = 0; let outreachIndex = 0; let followUpIndex = 0;
  const usedPrepIds = new Set<string>();
  const prep = (title: string, pattern: RegExp, useQueueFallback = true, fallbackContext?: string): TodayScheduleEntry => {
    const task = prepQueue.find(({ item, track, milestone }) => !usedPrepIds.has(item.id)
      && pattern.test(`${item.title} ${item.description ?? ""} ${track.name} ${milestone.title}`))
      ?? (useQueueFallback ? prepQueue.find(({ item }) => !usedPrepIds.has(item.id)) : undefined);
    if (task) usedPrepIds.add(task.item.id);
    return task ? { time: "", end: "", title, objective: "Sprint objective",
      context: `${task.item.title} · ${task.track.name} · ${task.item.estimatedMinutes} min`,
      status: sprintStageLabel(task.item.status), tone: "sprint" }
      : { time: "", end: "", title, objective: "Sprint objective", context: fallbackContext ?? "Select the matching task from the preparation board and record its evidence.", status: "Open", tone: "sprint" };
  };
  const action = (fallback: string): TodayScheduleEntry => {
    const task = actionQueue[actionIndex++] ?? null;
    return task ? { time: "", end: "", title: task.actionText, objective: "Opportunity execution",
      context: task.companyName ? `${task.companyName}${task.roleTitle ? ` · ${task.roleTitle}` : ""}` : `Imported priority ${task.priorityRank}`,
      status: task.status === "DONE" ? "Done" : "Planned", tone: "application" }
      : { time: "", end: "", title: fallback, objective: "Opportunity execution", context: "Use the highest-fit active opening as the decision anchor.", status: "Open", tone: "application" };
  };
  const referral = (): TodayScheduleEntry => {
    const task = outreachQueue[outreachIndex++];
    return task ? { time: "", end: "", title: `${task.overdue ? "Follow up with" : "Progress outreach to"} ${task.contactName}`,
      objective: "Referral conversion", context: `${task.companyName} · ${task.roleTitle}`, status: task.overdue ? "Due" : formatEnum(task.status), tone: "network" }
      : { time: "", end: "", title: "Complete referral outreach and due follow-ups", objective: "Referral conversion",
        context: "Prioritize the strongest path for an active high-fit opening.", status: "Open", tone: "network" };
  };
  const followUp = (): TodayScheduleEntry => {
    const task = followUpQueue[followUpIndex++];
    return task ? { time: "", end: "", title: task.nextAction, objective: "Pipeline momentum",
      context: `${task.companyName} · ${task.roleTitle}`, status: formatDue(task.nextActionAt), tone: "pipeline" }
      : action("Review active applications and set the next action");
  };
  const withTime = (time: string, end: string, entry: TodayScheduleEntry): TodayScheduleEntry => ({ ...entry, time, end });
  const breakBlock = (time: string, end: string, title: string): TodayScheduleEntry => ({ time, end, title,
    objective: "Recovery window", context: "Step away fully, reset, and protect the quality of the next focus block.", status: "Protected", tone: "recovery" });
  const morningEntries = [
      withTime("5:00 AM", "7:30 AM", prep("Coding practice", /(coding|problem|algorithm|thread|concurren|memory|production engineering)/i)),
      breakBlock("7:30 AM", "8:30 AM", "Breakfast and reset"),
      withTime("8:30 AM", "10:50 AM", action("Tailor and submit the strongest high-fit application")),
      withTime("11:00 AM", "11:30 AM", referral()),
      breakBlock("11:30 AM", "12:30 PM", "Lunch break"),
    ];
  const afternoonEntries = [
      withTime("12:30 PM", "2:25 PM", prep(focusPlan.title, focusPlan.pattern, false, focusPlan.description)),
      withTime("2:30 PM", "3:00 PM", action("Progress the next application-ready opening")),
      breakBlock("3:00 PM", "3:15 PM", "Short break"),
      withTime("3:15 PM", "3:45 PM", referral()),
      withTime("3:45 PM", "5:00 PM", prep(focusPlan.secondaryTitle, focusPlan.pattern, false, focusPlan.description)),
    ];
  const eveningBlock = outreachQueue[outreachIndex] ? referral() : actionQueue[actionIndex]
    ? action("Progress the next opportunity action") : followUpQueue[followUpIndex] ? followUp()
      : { time: "", end: "", title: "Referral conversion or opportunity execution", objective: "End-of-day execution",
        context: "Close one meaningful relationship or application loop before recording the day’s evidence.", status: "Open", tone: "review" };
  const columns = [
    { label: "Morning", range: "5:00 AM–12:30 PM", entries: morningEntries },
    { label: "Afternoon", range: "12:30 PM–5:00 PM", entries: afternoonEntries },
    { label: "Evening", range: "6:30 PM–7:30 PM", entries: [withTime("6:30 PM", "7:30 PM", eveningBlock)] },
  ];
  const focusBlockCount = columns.flatMap((column) => column.entries).filter((entry) => entry.tone !== "recovery").length;
  return <section className="panel today-schedule" aria-labelledby="today-schedule-title">
    <header className="today-schedule-head"><div><p className="eyebrow">Morning operating plan</p><h2 id="today-schedule-title">Today’s schedule — {formatScheduleDate(date)}</h2>
      <p>Time-blocked from live priorities so each session advances an opportunity, relationship, pipeline action, or sprint deliverable.</p></div>
      <div className="today-schedule-summary"><span><strong>3</strong>dayparts</span><span><strong>{focusBlockCount}</strong>focus blocks</span></div></header>
    <div className="today-schedule-columns">{columns.map((column, columnIndex) => <section className="schedule-daypart" key={column.label}>
      <header><span>0{columnIndex + 1}</span><div><strong>{column.label}</strong><small>{column.range}</small></div></header>
      <div className="schedule-stack">{column.entries.map((entry) => <article className={`schedule-block ${entry.tone}`} key={`${entry.time}-${entry.title}`}>
        <div className="schedule-time"><strong>{entry.time}</strong><span>{entry.end}</span></div><i aria-hidden="true" />
        <div className="schedule-task"><header><span>{entry.objective}</span><em>{entry.status}</em></header><h3>{entry.title}</h3><p>{entry.context}</p></div>
      </article>)}</div>
    </section>)}</div>
    <footer className="today-schedule-foot"><p><strong>Sprint connection:</strong> preparation blocks pull from the current two-week board first; opportunity and relationship blocks protect the outcomes that make that preparation useful.</p>
      <nav><a className="text-link" href="#preparation">Open sprint board →</a><a className="text-link" href="#opportunities">Review opportunities →</a></nav></footer>
  </section>;
}

function MonthlyProgressCalendar({ progress, loading, onMonthChange }: { progress: MonthlyProgress; loading: boolean; onMonthChange: (month: string) => void }) {
  const initialDay = progress.today >= progress.monthStart && progress.today <= progress.monthEnd ? progress.today : progress.monthStart;
  const [selectedDate, setSelectedDate] = useState(initialDay);
  const effectiveSelectedDate = selectedDate >= progress.monthStart && selectedDate <= progress.monthEnd ? selectedDate : initialDay;
  const selected = progress.days.find((day) => day.date === effectiveSelectedDate) ?? progress.days[0];
  const leading = (new Date(`${progress.monthStart}T08:00:00`).getDay() + 6) % 7;
  const cells: Array<MonthlyProgressDay | null> = [...Array.from({ length: leading }, () => null), ...progress.days];
  const monthLabel = new Date(`${progress.monthStart}T08:00:00`).toLocaleDateString("en-IN", { month: "long", year: "numeric" });
  function shiftMonth(offset: number) {
    const date = new Date(`${progress.monthStart}T08:00:00`); date.setMonth(date.getMonth() + offset);
    onMonthChange(`${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-01`);
  }
  return <section className={`monthly-progress ${loading ? "loading" : ""}`}>
    <header className="monthly-progress-head"><div><p className="section-label">Month at a glance</p><h3>{monthLabel}</h3></div>
      <div className="month-nav"><button type="button" aria-label="Previous month" onClick={() => shiftMonth(-1)}>‹</button>
        <button type="button" aria-label="Next month" onClick={() => shiftMonth(1)}>›</button></div></header>
    <div className="monthly-progress-totals">
      <span><b>{progress.totals.applicationsSubmitted}</b>Applications</span>
      <span><b>{progress.totals.outreachSent}</b>Outreach <small>{progress.totals.referralsSecured} outcomes</small></span>
      <span><b>{progress.totals.preparationTasksCompleted}/{progress.totals.preparationTasksPlanned}</b>Prep tasks</span>
      <span><b>{progress.totals.preparationMinutes}</b>Practice minutes</span>
    </div>
    <div className="monthly-calendar-wrap"><div className="monthly-calendar">
      {['Mon','Tue','Wed','Thu','Fri','Sat','Sun'].map((day) => <span className="calendar-weekday" key={day}>{day}</span>)}
      {cells.map((day, index) => day ? <button type="button" key={day.date} onClick={() => setSelectedDate(day.date)}
        className={`${day.date === progress.today ? "today" : ""} ${day.date === effectiveSelectedDate ? "selected" : ""} ${day.date > progress.today ? "future" : ""}`}
        aria-label={`View activity for ${formatDate(day.date)}`}>
        <b>{Number(day.date.slice(-2))}</b><small>A {day.applicationsSubmitted} · O {day.outreachSent} · P {day.preparationTasksCompleted}/{day.preparationTasksPlanned}</small>
        <i style={{ opacity: Math.min(1, .16 + (day.applicationsSubmitted + day.outreachSent + day.preparationTasksCompleted + day.preparationSessions) * .15) }} />
      </button> : <span className="calendar-empty" key={`blank-${index}`} />)}
    </div>
    {selected && <aside className="monthly-day-detail"><span>{formatDate(selected.date)}</span><strong>{selected.applicationsSubmitted + selected.outreachSent + selected.preparationTasksCompleted + selected.preparationSessions} recorded actions</strong>
      <p>{selected.applicationsSubmitted} applications · {selected.outreachSent} outreach · {selected.outreachResponses} responses · {selected.referralsSecured} referrals</p>
      <p>{selected.preparationTasksCompleted} of {selected.preparationTasksPlanned} prep tasks · {selected.preparationMinutes} focused minutes</p></aside>}</div>
  </section>;
}

function WeeklyKpi({ label, value, suffix, detail, delta }: { label: string; value: number; suffix?: string; detail: string; delta?: number }) {
  return <article><span>{label}</span><div><strong>{value}</strong>{suffix && <em>{suffix}</em>}</div><p>{detail}</p>
    {delta !== undefined && <small className={delta > 0 ? "up" : delta < 0 ? "down" : "neutral"}>{delta > 0 ? "+" : ""}{delta} vs prior week</small>}</article>;
}

function WeeklySignal({ label, value, total }: { label: string; value: number; total?: number }) {
  const percent = total && total > 0 ? Math.min(100, (value / total) * 100) : value > 0 ? 100 : 0;
  return <div className="weekly-signal"><div><span>{label}</span><strong>{value}{total !== undefined ? ` / ${total}` : ""}</strong></div>
    <i><b style={{ width: `${percent}%` }} /></i></div>;
}

function WeeklyReflection({ revision }: { revision: WeeklyReviewRevision }) {
  const fields = [["Wins", revision.wins], ["Challenges", revision.challenges], ["Reflection", revision.reflection],
    ["Next-week adjustments", revision.nextWeekAdjustments], ["Next-week focus", revision.nextWeekFocus]];
  return <div className="weekly-reflection-grid">{fields.filter(([, value]) => value).map(([label, value]) => <article key={label}>
    <span>{label}</span><p>{value}</p></article>)}</div>;
}

function WeeklyReviewRevisionModal({ review, onClose, onSaved, onError }: { review: WeeklyReview; onClose: () => void;
  onSaved: () => Promise<void>; onError: (message: string) => void }) {
  const [wins, setWins] = useState(""); const [challenges, setChallenges] = useState("");
  const [reflection, setReflection] = useState(""); const [adjustments, setAdjustments] = useState("");
  const [focus, setFocus] = useState(""); const [saving, setSaving] = useState(false);
  const hasContent = [wins, challenges, reflection, adjustments, focus].some((value) => value.trim());
  async function submit(event: FormEvent) {
    event.preventDefault(); setSaving(true);
    try {
      await api(`/api/v1/reviews/weekly/${review.id}/revisions`, { method: "POST", body: JSON.stringify({
        wins, challenges, reflection, nextWeekAdjustments: adjustments, nextWeekFocus: focus,
      }) });
      await onSaved();
    } catch (error) { onError(error instanceof Error ? error.message : "Could not preserve the weekly reflection."); }
    finally { setSaving(false); }
  }
  return <Modal title="Add weekly reflection revision" subtitle={`${formatDate(review.weekStart)}–${formatDate(review.weekEnd)} · metrics remain immutable`} onClose={onClose} wide>
    <form className="modal-form" onSubmit={submit}><div className="form-grid"><Field label="Wins"><textarea rows={4} value={wins} onChange={(event) => setWins(event.target.value)} /></Field>
      <Field label="Challenges"><textarea rows={4} value={challenges} onChange={(event) => setChallenges(event.target.value)} /></Field></div>
      <Field label="Reflection"><textarea rows={4} value={reflection} onChange={(event) => setReflection(event.target.value)} placeholder="What did the evidence say about this week?" /></Field>
      <div className="form-grid"><Field label="Next-week adjustments"><textarea rows={4} value={adjustments} onChange={(event) => setAdjustments(event.target.value)} /></Field>
        <Field label="Next-week focus"><textarea rows={4} value={focus} onChange={(event) => setFocus(event.target.value)} /></Field></div>
      <div className="immutable-note"><strong>Append-only record</strong><span>This creates revision {review.revisions.length + 1}. Earlier reflections and the metric snapshot stay unchanged.</span></div>
      <ModalActions onClose={onClose} saving={saving} disabled={!hasContent} label="Preserve revision" /></form>
  </Modal>;
}

function OpeningDetailModal({ detail, onClose, onStart }: { detail: OpeningDetail; onClose: () => void; onStart: () => void }) {
  const opening = detail.opening;
  return <Modal title={opening.roleTitle} subtitle={`${opening.companyName} · ${opening.location ?? "Location not listed"}`} onClose={onClose} wide>
    <div className="detail-body">
      <div className="detail-banner"><div><span className={`recommendation-pill ${recommendationClass(openingRecommendation(opening))}`}>{openingRecommendation(opening)}</span>
        <p>Rank #{opening.rank} on {formatDate(opening.observedOn)}</p></div><strong>{opening.weightedTotal.toFixed(2)}<small>/10 weighted fit</small></strong></div>
      <div className="score-grid">
        <Score label="Overall fit" value={opening.overallFit} />
        <Score label="Recruiter screen" value={opening.recruiterScreenStrength} />
        <Score label="Technical scope" value={opening.technicalScope} />
        <Score label="Growth potential" value={opening.growthPotential} />
      </div>
      <div className="detail-columns"><section><p className="section-label">Role summary</p><p>{detail.roleSummary}</p>
        <p className="section-label">Why it fits</p><p>{detail.fitRationale}</p></section>
        <section className="risk-panel"><p className="section-label">Risks and gaps</p><p>{detail.keyRisks ?? "No explicit risk notes were imported."}</p>
          <dl><div><dt>Recommended resume</dt><dd>{opening.recommendedResumeVariant ?? "Not assigned"}</dd></div>
            <div><dt>Eligibility</dt><dd>{detail.authorizationEligibility ?? "Not specified"}</dd></div>
            <div><dt>Verified</dt><dd>{formatDate(detail.verifiedDate)}</dd></div></dl></section></div>
      {detail.observationHistory.length > 1 && <section className="history-strip"><p className="section-label">Observation history</p>
        {detail.observationHistory.map((item) => <span key={item.observedOn}>{formatDate(item.observedOn)} · #{item.rank} · {item.weightedTotal.toFixed(2)}</span>)}</section>}
      {opening.status === "ARCHIVED" && <div className="archived-detail"><strong>Not pursuing</strong><span>{opening.archiveReason}</span></div>}
      <div className="detail-actions"><a className="secondary-button link-button" href={opening.sourceUrl} target="_blank" rel="noreferrer">Open original listing ↗</a>
        {opening.status === "ARCHIVED" ? <span className="archive-state">Restore this opening before starting an application.</span>
          : opening.applicationId ? <span className="applied-check">✓ Application already created</span>
          : <button className="primary-button" onClick={onStart}>Start application</button>}</div>
    </div>
  </Modal>;
}

function Score({ label, value }: { label: string; value: number }) {
  return <div className="score-card"><span>{label}</span><strong>{value.toFixed(1)}</strong><i><b style={{ width: `${value * 10}%` }} /></i></div>;
}

function ArchiveOpeningModal({ opening, onClose, onSaved, onError }: { opening: Opening; onClose: () => void;
  onSaved: () => Promise<void>; onError: (message: string) => void }) {
  const [saving, setSaving] = useState(false);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const data = new FormData(event.currentTarget); setSaving(true);
    try {
      await api(`/api/v1/opportunities/${opening.opportunityId}/archive`, { method: "POST", body: JSON.stringify({ reason: data.get("reason") }) });
      await onSaved();
    } catch (error) { onError(error instanceof Error ? error.message : "Could not archive the opening."); }
    finally { setSaving(false); }
  }
  return <Modal title="Archive opening" subtitle={`${opening.companyName} · ${opening.roleTitle}`} onClose={onClose}>
    <form className="modal-form" onSubmit={(event) => void submit(event)}>
      <p className="archive-guidance">This removes the opening from your active High-fit view. You can review the explanation and restore it later from Archived openings.</p>
      <Field label="Why are you not pursuing this opening?"><textarea name="reason" rows={4} required minLength={3}
        placeholder="For example: role scope is too junior, compensation range is misaligned, or location requirements do not work." /></Field>
      <ModalActions onClose={onClose} saving={saving} disabled={false} label="Archive opening" />
    </form>
  </Modal>;
}

function InboxIntakeModal({ onClose, onSaved, onError }: { onClose: () => void; onSaved: (result: InboxCreateResult) => Promise<void>; onError: (message: string) => void }) {
  const [sourceType, setSourceType] = useState<InboxSourceType>("PASTED_TEXT");
  const [sourceLabel, setSourceLabel] = useState("Manual research");
  const [sourceFilename, setSourceFilename] = useState<string | null>(null);
  const [mediaType, setMediaType] = useState("text/plain");
  const [content, setContent] = useState("");
  const [saving, setSaving] = useState(false);

  async function loadFile(event: React.ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (!file) return;
    const lower = file.name.toLowerCase();
    if (!lower.endsWith(".csv") && !lower.endsWith(".json")) {
      onError("Choose a CSV or JSON file for this milestone.");
      event.target.value = "";
      return;
    }
    setSourceFilename(file.name);
    setSourceLabel(sourceLabel === "Manual research" ? file.name : sourceLabel);
    setMediaType(file.type || (lower.endsWith(".csv") ? "text/csv" : "application/json"));
    setSourceType(lower.endsWith(".csv") ? "UPLOADED_CSV" : "UPLOADED_JSON");
    setContent(await file.text());
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    try {
      const result = await api<InboxCreateResult>("/api/v1/inbox/items", { method: "POST", body: JSON.stringify({
        sourceType, sourceLabel, sourceFilename, mediaType, content,
      }) });
      if (result.item.status === "FAILED") throw new Error(result.item.errorMessage ?? "The source could not be parsed.");
      await onSaved(result);
    } catch (error) { onError(error instanceof Error ? error.message : "Could not add the inbox source."); }
    finally { setSaving(false); }
  }

  function changePasteFormat(value: InboxSourceType) {
    setSourceType(value);
    setSourceFilename(null);
    setMediaType(value === "PASTED_JSON" ? "application/json" : "text/plain");
  }

  return <Modal title="Add an inbox source" subtitle="Capture first, review the parsed fields, and publish only after confirmation." onClose={onClose} wide>
    <form className="modal-form inbox-intake-form" onSubmit={submit}>
      <div className="form-grid"><label className="field"><span>Paste format</span><select value={sourceFilename ? sourceType : sourceType}
        disabled={sourceFilename !== null} onChange={(event) => changePasteFormat(event.target.value as InboxSourceType)}>
        <option value="PASTED_TEXT">Labeled or unstructured text</option><option value="PASTED_JSON">Structured JSON</option>
        {sourceFilename && <><option value="UPLOADED_CSV">Uploaded CSV</option><option value="UPLOADED_JSON">Uploaded JSON</option></>}
      </select></label>
      <label className="field"><span>Source label</span><input value={sourceLabel} maxLength={160} onChange={(event) => setSourceLabel(event.target.value)} placeholder="Manual research, ChatGPT, job export…" /></label></div>
      <label className="field inbox-file-field"><span>Or load a file</span><input type="file" accept=".csv,.json,text/csv,application/json" onChange={(event) => void loadFile(event)} />
        <small>CSV and JSON are read locally and sent to your local API; the original content is retained for provenance.</small></label>
      <label className="field"><span>{sourceFilename ? `Loaded content · ${sourceFilename}` : "Source content"}</span><textarea rows={13} required value={content}
        onChange={(event) => setContent(event.target.value)} placeholder={sourceType === "PASTED_JSON"
          ? `{"company":"Example","title":"Senior Engineer","url":"https://…"}`
          : "Company: Example\nTitle: Senior Engineer\nLocation: Hyderabad\nWork mode: Hybrid\nURL: https://…\nDescription:\nBuild reliable services…"} /></label>
      <div className="inbox-format-help"><strong>Accepted field names</strong><span>company, title, location, work mode, source, job/requisition ID, URL, and description. JSON may contain one object, an array, or a jobs/opportunities/items collection.</span></div>
      <div className="modal-actions"><button type="button" className="text-button" onClick={onClose}>Cancel</button>
        <button type="submit" className="primary-button" disabled={saving || !content.trim()}>{saving ? "Parsing…" : "Add to review queue"}</button></div>
    </form>
  </Modal>;
}

const assistedFieldLabels: [keyof AssistedFields, string][] = [
  ["companyName", "Company"], ["roleTitle", "Role title"], ["location", "Location"], ["workMode", "Work mode"],
  ["sourceName", "Source"], ["sourceExternalId", "External job ID"], ["sourceUrl", "Source URL"], ["description", "Description"],
];

function InboxAssistanceModal({ candidate, onClose, onSaved, onError }: { candidate: InboxCandidate; onClose: () => void;
  onSaved: (message: string) => Promise<void>; onError: (message: string) => void }) {
  const [overview, setOverview] = useState<InboxAssistanceOverview | null>(null);
  const [selectedFields, setSelectedFields] = useState<string[]>([]);
  const [confirmed, setConfirmed] = useState(false);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      const value = await api<InboxAssistanceOverview>(`/api/v1/assistance/inbox/${candidate.id}`);
      setOverview(value);
      const suggestion = value.runs.find((run) => run.status === "COMPLETED")?.suggestion;
      if (suggestion) setSelectedFields(assistedFieldLabels.filter(([field]) => suggestion.fields[field] !== null
        && String(suggestion.fields[field] ?? "") !== String(value.current[field] ?? "")).map(([field]) => field));
    } catch (error) { onError(error instanceof Error ? error.message : "Could not load AI assistance."); }
  }, [candidate.id, onError]);

  useEffect(() => { const timer = window.setTimeout(() => void load(), 0); return () => window.clearTimeout(timer); }, [load]);
  const run = overview?.runs.find((item) => item.status === "COMPLETED") ?? overview?.runs[0];
  const suggestion = run?.suggestion;

  async function generate() {
    setBusy(true);
    try {
      const result = await api<{ replayed: boolean; run: InboxAssistanceRun }>(`/api/v1/assistance/inbox/${candidate.id}`,
        { method: "POST", body: JSON.stringify({ confirmedTransmission: confirmed }) });
      await load();
      if (result.run.status === "FAILED") onError(result.run.errorMessage ?? "The provider could not structure this source.");
      else await onSaved(result.replayed ? "The existing schema-constrained result was reused." : "A new reviewable assistance result was generated.");
    } catch (error) { onError(error instanceof Error ? error.message : "Could not request AI assistance."); }
    finally { setBusy(false); }
  }

  async function applyFields() {
    if (!run) return;
    setBusy(true);
    try {
      await api(`/api/v1/assistance/runs/${run.id}/apply`, { method: "POST", body: JSON.stringify({ fields: selectedFields }) });
      await load(); await onSaved(`${selectedFields.length} selected suggestion${selectedFields.length === 1 ? " was" : "s were"} applied to the review candidate.`);
    } catch (error) { onError(error instanceof Error ? error.message : "Could not apply the selected fields."); }
    finally { setBusy(false); }
  }

  async function publishSkills() {
    if (!run) return;
    setBusy(true);
    try {
      const result = await api<{ publishedCount: number; unmatchedSkills: string[] }>(`/api/v1/assistance/runs/${run.id}/skills`, { method: "POST" });
      await load(); await onSaved(`${result.publishedCount} skill suggestion${result.publishedCount === 1 ? "" : "s"} entered the existing Proposed evidence queue.${result.unmatchedSkills.length ? ` ${result.unmatchedSkills.length} unmatched term${result.unmatchedSkills.length === 1 ? " remains" : "s remain"} for taxonomy review.` : ""}`);
    } catch (error) { onError(error instanceof Error ? error.message : "Could not publish the proposed skill evidence."); }
    finally { setBusy(false); }
  }

  function toggle(field: string) { setSelectedFields((current) => current.includes(field) ? current.filter((item) => item !== field) : [...current, field]); }

  return <Modal title="AI-assisted inbox structuring" subtitle="Review every suggestion before it can affect the candidate or skill catalog." onClose={onClose} wide>
    {!overview ? <p className="inline-empty">Loading the local assistance boundary…</p> : <div className="assistance-workspace">
      <div className={`assistance-config ${overview.configuration.configured ? "ready" : "off"}`}><div><strong>{overview.configuration.configured ? "Configured" : "AI assistance is off"}</strong>
        <span>{overview.configuration.provider} · {overview.configuration.model}</span></div><p>{overview.configuration.message}</p></div>
      {!suggestion && <><div className="assistance-transmission"><div><p className="section-label">Outbound preview</p><h3>Content sent only after confirmation</h3></div>
        <pre>{overview.outboundContent}</pre><label><input type="checkbox" checked={confirmed} onChange={(event) => setConfirmed(event.target.checked)} />
          <span>I reviewed this content and approve sending it to the configured provider for this structuring request.</span></label></div>
        {run?.status === "FAILED" && <p className="inbox-error">{run.errorMessage}</p>}
        <div className="modal-actions"><button type="button" className="text-button" onClick={onClose}>Close</button>
          <button type="button" className="assist-button" disabled={busy || !confirmed || !overview.configuration.configured} onClick={() => void generate()}>{busy ? "Structuring…" : "✦ Generate suggestions"}</button></div></>}
      {suggestion && <><div className="assistance-provenance"><span>Schema-constrained result</span><strong>{run.provider} · {run.model}</strong><small>{run.promptVersion} · generated {formatDateTime(run.createdAt)}</small></div>
        <div className="assisted-field-list"><div className="assisted-field-head"><span>Use</span><span>Field</span><strong>Current</strong><strong>Suggested</strong></div>
          {assistedFieldLabels.map(([field, label]) => <label className="assisted-field-row" key={field}><input type="checkbox" checked={selectedFields.includes(field)}
            disabled={suggestion.fields[field] === null} onChange={() => toggle(field)} /><span>{label}</span>
            <p>{String(overview.current[field] ?? "—")}</p><p>{String(suggestion.fields[field] ?? "—")}</p></label>)}</div>
        {(suggestion.warnings.length > 0 || suggestion.reviewQuestions.length > 0) && <div className="assistance-cautions">
          {suggestion.warnings.map((value) => <p key={value}><strong>Warning</strong>{value}</p>)}
          {suggestion.reviewQuestions.map((value) => <p key={value}><strong>Review</strong>{value}</p>)}</div>}
        <div className="assisted-skills"><div><p className="section-label">Proposed evidence</p><h3>{suggestion.skills.length} skill suggestion{suggestion.skills.length === 1 ? "" : "s"}</h3></div>
          {suggestion.skills.map((skill) => <article key={`${skill.name}-${skill.evidenceSnippet}`}><strong>{skill.name}</strong><span>{formatEnum(skill.strength)}</span><p>{skill.evidenceSnippet}</p></article>)}
          <small>Skill observations remain Proposed until reviewed in Market Skills. Unmatched names are retained for taxonomy review.</small></div>
        <div className="modal-actions"><button type="button" className="text-button" onClick={onClose}>Close</button>
          <button type="button" className="secondary-button" disabled={busy || !overview.current.opportunityId || suggestion.skills.length === 0} onClick={() => void publishSkills()}>Publish proposed skills</button>
          <button type="button" className="primary-button" disabled={busy || selectedFields.length === 0} onClick={() => void applyFields()}>{busy ? "Working…" : `Apply ${selectedFields.length} selected`}</button></div></>}
    </div>}
  </Modal>;
}

function WeeklyAssistanceModal({ review, onClose, onSaved, onError }: { review: WeeklyReview; onClose: () => void;
  onSaved: () => Promise<void>; onError: (message: string) => void }) {
  const [overview, setOverview] = useState<WeeklyAssistanceOverview | null>(null);
  const [confirmed, setConfirmed] = useState(false); const [busy, setBusy] = useState(false);
  const [wins, setWins] = useState(""); const [challenges, setChallenges] = useState(""); const [reflection, setReflection] = useState("");
  const [adjustments, setAdjustments] = useState(""); const [focus, setFocus] = useState("");

  const load = useCallback(async () => {
    try { const value = await api<WeeklyAssistanceOverview>(`/api/v1/assistance/weekly/${review.id}`); setOverview(value);
      const generatedDraft = value.runs.find((item) => item.status === "COMPLETED")?.draft;
      if (generatedDraft) { setWins(generatedDraft.wins ?? ""); setChallenges(generatedDraft.challenges ?? "");
        setReflection(generatedDraft.reflection ?? ""); setAdjustments(generatedDraft.nextWeekAdjustments ?? ""); setFocus(generatedDraft.nextWeekFocus ?? ""); } }
    catch (error) { onError(error instanceof Error ? error.message : "Could not load weekly assistance."); }
  }, [review.id, onError]);
  useEffect(() => { const timer = window.setTimeout(() => void load(), 0); return () => window.clearTimeout(timer); }, [load]);
  const run = overview?.runs.find((item) => item.status === "COMPLETED") ?? overview?.runs[0];
  const draft = run?.draft;

  async function generate() {
    setBusy(true);
    try {
      const result = await api<{ run: WeeklyAssistanceRun }>(`/api/v1/assistance/weekly/${review.id}`,
        { method: "POST", body: JSON.stringify({ confirmedTransmission: confirmed }) });
      await load(); if (result.run.status === "FAILED") onError(result.run.errorMessage ?? "The provider could not draft this reflection.");
    } catch (error) { onError(error instanceof Error ? error.message : "Could not draft the weekly reflection."); }
    finally { setBusy(false); }
  }

  async function save(event: FormEvent) {
    event.preventDefault(); setBusy(true);
    try {
      await api(`/api/v1/reviews/weekly/${review.id}/revisions`, { method: "POST", body: JSON.stringify({ wins, challenges,
        reflection, nextWeekAdjustments: adjustments, nextWeekFocus: focus }) }); await onSaved();
    } catch (error) { onError(error instanceof Error ? error.message : "Could not preserve the reviewed draft."); }
    finally { setBusy(false); }
  }

  return <Modal title="Draft weekly reflection with AI" subtitle={`${formatDate(review.weekStart)}–${formatDate(review.weekEnd)} · preserved metrics remain unchanged`} onClose={onClose} wide>
    {!overview ? <p className="inline-empty">Loading the local assistance boundary…</p> : !draft ? <div className="assistance-workspace">
      <div className={`assistance-config ${overview.configuration.configured ? "ready" : "off"}`}><div><strong>{overview.configuration.configured ? "Configured" : "AI assistance is off"}</strong>
        <span>{overview.configuration.provider} · {overview.configuration.model}</span></div><p>{overview.configuration.message}</p></div>
      <div className="assistance-transmission"><p>The immutable metric snapshot, week-over-week deltas, and existing reflection revisions will be transmitted. No contacts, resumes, or raw inbox content are included.</p>
        <label><input type="checkbox" checked={confirmed} onChange={(event) => setConfirmed(event.target.checked)} /><span>I approve this one drafting request.</span></label></div>
      {run?.status === "FAILED" && <p className="inbox-error">{run.errorMessage}</p>}
      <div className="modal-actions"><button type="button" className="text-button" onClick={onClose}>Close</button>
        <button type="button" className="assist-button" disabled={busy || !confirmed || !overview.configuration.configured} onClick={() => void generate()}>{busy ? "Drafting…" : "✦ Generate review draft"}</button></div>
    </div> : <form className="modal-form assistance-draft-form" onSubmit={save}><div className="assistance-provenance"><span>Editable draft</span><strong>{run.provider} · {run.model}</strong><small>Nothing is saved until you preserve this revision.</small></div>
      {draft.evidence.length > 0 && <div className="draft-evidence"><strong>Evidence used</strong>{draft.evidence.map((item) => <span key={item}>{item}</span>)}</div>}
      <div className="form-grid"><Field label="Wins"><textarea rows={4} value={wins} onChange={(event) => setWins(event.target.value)} /></Field>
        <Field label="Challenges"><textarea rows={4} value={challenges} onChange={(event) => setChallenges(event.target.value)} /></Field></div>
      <Field label="Reflection"><textarea rows={4} value={reflection} onChange={(event) => setReflection(event.target.value)} /></Field>
      <div className="form-grid"><Field label="Next-week adjustments"><textarea rows={4} value={adjustments} onChange={(event) => setAdjustments(event.target.value)} /></Field>
        <Field label="Next-week focus"><textarea rows={4} value={focus} onChange={(event) => setFocus(event.target.value)} /></Field></div>
      <div className="immutable-note"><strong>Human-reviewed save</strong><span>This creates revision {review.revisions.length + 1}; the generated draft and prior review history remain auditable.</span></div>
      <ModalActions onClose={onClose} saving={busy} disabled={!([wins, challenges, reflection, adjustments, focus].some((value) => value.trim()))} label="Preserve reviewed revision" /></form>}
  </Modal>;
}

function InboxCandidateModal({ candidate, onClose, onSaved, onError }: { candidate: InboxCandidate; onClose: () => void; onSaved: () => Promise<void>; onError: (message: string) => void }) {
  const [companyName, setCompanyName] = useState(candidate.companyName ?? "");
  const [roleTitle, setRoleTitle] = useState(candidate.roleTitle ?? "");
  const [location, setLocation] = useState(candidate.location ?? "");
  const [workMode, setWorkMode] = useState(candidate.workMode ?? "UNSPECIFIED");
  const [sourceName, setSourceName] = useState(candidate.sourceName ?? "");
  const [sourceExternalId, setSourceExternalId] = useState(candidate.sourceExternalId ?? "");
  const [sourceUrl, setSourceUrl] = useState(candidate.sourceUrl ?? "");
  const [description, setDescription] = useState(candidate.description ?? "");
  const [saving, setSaving] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    try {
      await api(`/api/v1/inbox/candidates/${candidate.id}`, { method: "PATCH", body: JSON.stringify({
        companyName, roleTitle, location, workMode, sourceName, sourceExternalId, sourceUrl, description,
      }) });
      await onSaved();
    } catch (error) { onError(error instanceof Error ? error.message : "Could not update the candidate."); }
    finally { setSaving(false); }
  }

  return <Modal title="Review candidate fields" subtitle="Correct deterministic parsing before the job enters your opportunity inbox." onClose={onClose} wide>
    <form className="modal-form" onSubmit={submit}>
      <div className="form-grid"><label className="field"><span>Company *</span><input required maxLength={240} value={companyName} onChange={(event) => setCompanyName(event.target.value)} /></label>
        <label className="field"><span>Role title *</span><input required maxLength={240} value={roleTitle} onChange={(event) => setRoleTitle(event.target.value)} /></label>
        <label className="field"><span>Location</span><input maxLength={240} value={location} onChange={(event) => setLocation(event.target.value)} /></label>
        <label className="field"><span>Work mode</span><select value={workMode} onChange={(event) => setWorkMode(event.target.value)}>
          <option value="UNSPECIFIED">Unspecified</option><option value="ONSITE">Onsite</option><option value="HYBRID">Hybrid</option><option value="REMOTE">Remote</option>
        </select></label>
        <label className="field"><span>Source name</span><input maxLength={120} value={sourceName} onChange={(event) => setSourceName(event.target.value)} /></label>
        <label className="field"><span>External job or requisition ID</span><input maxLength={240} value={sourceExternalId} onChange={(event) => setSourceExternalId(event.target.value)} /></label>
        <label className="field"><span>Source URL</span><input type="url" maxLength={1500} value={sourceUrl} onChange={(event) => setSourceUrl(event.target.value)} /></label></div>
      <label className="field"><span>Description</span><textarea rows={10} value={description} onChange={(event) => setDescription(event.target.value)} /></label>
      <div className="modal-actions"><button type="button" className="text-button" onClick={onClose}>Cancel</button>
        <button className="primary-button" type="submit" disabled={saving}>{saving ? "Saving…" : "Save review fields"}</button></div>
    </form>
  </Modal>;
}

const duplicateMergeFields = [
  ["COMPANY_NAME", "Company"], ["ROLE_TITLE", "Role title"], ["LOCATION", "Location"], ["WORK_MODE", "Work mode"],
  ["SOURCE_NAME", "Source name"], ["SOURCE_URL", "Source URL"], ["SOURCE_EXTERNAL_ID", "External job ID"], ["DESCRIPTION", "Description"],
] as const;

function DuplicateReviewModal({ candidate, onClose, onSaved, onError }: { candidate: InboxCandidate; onClose: () => void;
  onSaved: (message: string) => Promise<void>; onError: (message: string) => void }) {
  const openMatches = candidate.duplicateMatches.filter((match) => match.status === "OPEN");
  const [selectedMatchId, setSelectedMatchId] = useState(openMatches[0]?.id ?? "");
  const [mergeFields, setMergeFields] = useState<string[]>([]);
  const [saving, setSaving] = useState(false);
  const selected = openMatches.find((match) => match.id === selectedMatchId) ?? openMatches[0];

  function toggleMergeField(field: string) {
    setMergeFields((current) => current.includes(field) ? current.filter((value) => value !== field) : [...current, field]);
  }

  async function resolve(resolution: "LINK_EXISTING" | "MERGE_SELECTED_FIELDS" | "CREATE_SEPARATE") {
    setSaving(true);
    try {
      await api(`/api/v1/inbox/candidates/${candidate.id}/duplicate-resolution`, { method: "POST", body: JSON.stringify({
        resolution, matchId: resolution === "CREATE_SEPARATE" ? null : selected?.id, fields: resolution === "MERGE_SELECTED_FIELDS" ? mergeFields : [],
      }) });
      const message = resolution === "LINK_EXISTING" ? "The source was linked to the existing opening without changing its fields."
        : resolution === "MERGE_SELECTED_FIELDS" ? `The selected ${mergeFields.length} field${mergeFields.length === 1 ? " was" : "s were"} merged into the existing opening.`
          : "A separate opening was created; the duplicate evidence was retained as dismissed.";
      await onSaved(message);
    } catch (error) { onError(error instanceof Error ? error.message : "Could not resolve this duplicate review."); }
    finally { setSaving(false); }
  }

  async function reject() {
    setSaving(true);
    try {
      await api(`/api/v1/inbox/candidates/${candidate.id}/reject`, { method: "POST" });
      await onSaved("The candidate was rejected; its source and duplicate evidence were retained.");
    } catch (error) { onError(error instanceof Error ? error.message : "Could not reject this candidate."); }
    finally { setSaving(false); }
  }

  if (!selected) return <Modal title="Duplicate review" subtitle="No open duplicate evidence remains for this candidate." onClose={onClose}>
    <div className="modal-actions"><button className="primary-button" onClick={onClose}>Close</button></div></Modal>;

  const comparisonRows = [
    ["Company", candidate.companyName, selected.companyName], ["Role", candidate.roleTitle, selected.roleTitle],
    ["Location", candidate.location, selected.location], ["Work mode", formatEnum(candidate.workMode), formatEnum(selected.workMode)],
    ["Source", candidate.sourceName, selected.sourceName], ["External ID", candidate.sourceExternalId, selected.sourceExternalId],
    ["URL", candidate.sourceUrl, selected.sourceUrl],
  ];

  return <Modal title="Resolve possible duplicate" subtitle="Review the evidence and choose exactly how this source should be handled. No automatic merge is performed." onClose={onClose} wide>
    <div className="duplicate-review">
      {openMatches.length > 1 && <label className="field"><span>Compare against existing opening</span><select value={selected.id}
        onChange={(event) => setSelectedMatchId(event.target.value)}>{openMatches.map((match) => <option value={match.id} key={match.id}>
          {match.companyName} · {match.roleTitle} · {match.confidence}%</option>)}</select></label>}
      <div className={`duplicate-evidence ${selected.matchType.toLowerCase()}`}><div><strong>{selected.confidence}% confidence</strong>
        <span>{formatEnum(selected.matchType)}</span></div><p>{selected.explanation}</p></div>
      <div className="duplicate-comparison"><div className="duplicate-comparison-head"><span>Field</span><strong>Incoming source</strong><strong>Existing opening</strong></div>
        {comparisonRows.map(([label, incoming, existing]) => <div className="duplicate-comparison-row" key={label}><span>{label}</span>
          <p>{incoming || "—"}</p><p>{existing || "—"}</p></div>)}</div>
      <fieldset className="merge-field-picker"><legend>Fields to use when merging</legend><p>Select only the incoming values that should replace the corresponding existing fields.</p>
        <div>{duplicateMergeFields.map(([field, label]) => <label key={field}><input type="checkbox" checked={mergeFields.includes(field)}
          onChange={() => toggleMergeField(field)} /><span>{label}</span></label>)}</div></fieldset>
      <div className="duplicate-resolution-actions"><button type="button" className="text-button reject" disabled={saving} onClick={() => void reject()}>Reject candidate</button>
        <button type="button" className="text-button" disabled={saving} onClick={onClose}>Cancel</button>
        <button type="button" className="secondary-button" disabled={saving} onClick={() => void resolve("CREATE_SEPARATE")}>Create separate</button>
        <button type="button" className="secondary-button" disabled={saving || mergeFields.length === 0} onClick={() => void resolve("MERGE_SELECTED_FIELDS")}>Merge selected fields</button>
        <button type="button" className="primary-button" disabled={saving} onClick={() => void resolve("LINK_EXISTING")}>Link source only</button></div>
    </div>
  </Modal>;
}

function OpportunityModal({ connected, onClose, onSaved, onError }: { connected: boolean; onClose: () => void; onSaved: () => Promise<void>; onError: (message: string) => void }) {
  const [saving, setSaving] = useState(false);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const form = new FormData(event.currentTarget); setSaving(true);
    try {
      const created = await api<{ id: string }>("/api/v1/opportunities", { method: "POST", body: JSON.stringify({
        companyName: form.get("companyName"), roleTitle: form.get("roleTitle"), location: form.get("location"),
        workMode: form.get("workMode"), sourceName: form.get("sourceName"), sourceUrl: form.get("sourceUrl"),
        description: form.get("description"), discoveredAt: new Date().toISOString(),
      }) });
      const fitScore = Number(form.get("fitScore"));
      if (fitScore > 0) await api(`/api/v1/opportunities/${created.id}/decision`, { method: "POST", body: JSON.stringify({
        status: form.get("decision"), fitScore, fitSummary: form.get("fitSummary"),
      }) });
      await onSaved();
    } catch (error) { onError(error instanceof Error ? error.message : "Could not add opportunity."); }
    finally { setSaving(false); }
  }
  return <Modal title="Add an opportunity" subtitle="Capture the evidence now; enrich it as you review." onClose={onClose}>
    <form className="modal-form" onSubmit={(event) => void submit(event)}>
      <div className="form-grid"><Field label="Company"><input name="companyName" required /></Field><Field label="Role title"><input name="roleTitle" required /></Field></div>
      <div className="form-grid"><Field label="Location"><input name="location" placeholder="Hyderabad" /></Field><Field label="Work mode"><select name="workMode" defaultValue="HYBRID"><option>HYBRID</option><option>REMOTE</option><option>ONSITE</option><option>UNSPECIFIED</option></select></Field></div>
      <div className="form-grid"><Field label="Source"><input name="sourceName" placeholder="LinkedIn, referral, daily brief…" /></Field><Field label="Job URL"><input name="sourceUrl" type="url" placeholder="https://" /></Field></div>
      <Field label="Job description or notes"><textarea name="description" rows={4} /></Field>
      <div className="form-grid"><Field label="Initial decision"><select name="decision" defaultValue="REVIEWING"><option>REVIEWING</option><option>SHORTLISTED</option><option>SKIPPED</option></select></Field><Field label="Fit score"><input name="fitScore" type="number" min="0" max="100" placeholder="0–100" /></Field></div>
      <Field label="Why this fit score?"><textarea name="fitSummary" rows={2} /></Field>
      <ModalActions onClose={onClose} saving={saving} disabled={!connected} label={connected ? "Add opportunity" : "Start API to save"} />
    </form>
  </Modal>;
}

function ApplicationModal({ opportunity, resumes, preferredResumeName, onClose, onSaved, onError }: {
  opportunity: Opportunity; resumes: ResumeVariant[]; preferredResumeName?: string | null; onClose: () => void;
  onSaved: () => Promise<void>; onError: (message: string) => void;
}) {
  const [saving, setSaving] = useState(false);
  const [noNextAction, setNoNextAction] = useState(true);
  const preferredResume = resumes.find((resume) => resume.name.toLowerCase() === preferredResumeName?.toLowerCase());
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const form = new FormData(event.currentTarget); setSaving(true);
    try {
      const resumeFile = form.get("resumeFile");
      if (!(resumeFile instanceof File) || resumeFile.size === 0) throw new Error("Choose the exact PDF resume used for this application.");
      const nextAction = noNextAction ? null : String(form.get("nextAction") ?? "").trim();
      const nextActionDate = noNextAction ? null : String(form.get("nextActionAt") ?? "");
      if (!noNextAction && (!nextAction || !nextActionDate)) throw new Error("Add both a next action and its date, or select no next action to track.");
      const request = {
        resumeVariantId: form.get("resumeVariantId"), stage: form.get("stage"),
        appliedOn: form.get("stage") === "APPLIED" ? new Date().toISOString().slice(0, 10) : null,
        channel: form.get("channel"), nextAction,
        nextActionAt: nextActionDate ? new Date(nextActionDate).toISOString() : null,
        note: noNextAction ? null : form.get("note"),
      };
      const payload = new FormData();
      payload.append("request", new Blob([JSON.stringify(request)], { type: "application/json" }));
      payload.append("resumeFile", resumeFile);
      const jobDescription = String(form.get("jobDescription") ?? "");
      if (jobDescription.trim()) payload.append("jobDescription", jobDescription);
      await api(`/api/v1/opportunities/${opportunity.id}/applications`, { method: "POST", body: payload });
      await onSaved();
    } catch (error) { onError(error instanceof Error ? error.message : "Could not create application."); }
    finally { setSaving(false); }
  }
  return <Modal title="Start application" subtitle={`${opportunity.companyName} · ${opportunity.roleTitle}`} onClose={onClose}>
    <form className="modal-form" onSubmit={(event) => void submit(event)}>
      <Field label="Resume variant"><select name="resumeVariantId" required defaultValue={preferredResume?.id ?? resumes[0]?.id}>{resumes.map((resume) => <option value={resume.id} key={resume.id}>{resume.name} · {resume.versionLabel}</option>)}</select></Field>
      {preferredResumeName && <p className="form-hint">Workbook recommendation: <strong>{preferredResumeName}</strong></p>}
      <label className="field application-file-field"><span>Exact submitted resume PDF</span><input name="resumeFile" type="file" accept=".pdf,application/pdf" required />
        <small>Required for new applications. A hashed, immutable copy will be stored under <strong>application-resumes/{opportunity.companyName}/</strong>.</small></label>
      <Field label="Job description (optional)"><textarea name="jobDescription" rows={5} placeholder="Paste the job description to preserve it beside this application’s resume." /></Field>
      <p className="artifact-path-hint">Resume filename: <strong>Submitted_Resume_&lt;role or team summary&gt;.pdf</strong> · Job description: <strong>JobDescription_&lt;role or team summary&gt;.txt</strong></p>
      <div className="form-grid"><Field label="Current stage"><select name="stage" defaultValue="DRAFT"><option>DRAFT</option><option>APPLIED</option></select></Field><Field label="Application channel"><input name="channel" placeholder="Company careers page" /></Field></div>
      <label className="application-followup-toggle" aria-label="No next action to track">
        <input type="checkbox" checked={noNextAction} onChange={(event) => setNoNextAction(event.target.checked)} />
        <span><strong>No next action to track</strong><small>Selected by default. Uncheck this when you want to schedule a follow-up for the application.</small></span>
      </label>
      {!noNextAction && <div className="application-next-action-fields">
        <Field label="Next action"><input name="nextAction" required placeholder="e.g. Follow up with the recruiter" /></Field>
        <Field label="Next action date"><input name="nextActionAt" type="datetime-local" required defaultValue={localDateTime(24)} /></Field>
        <Field label="Comments (optional)"><textarea name="note" rows={2} placeholder="What should future-you remember?" /></Field>
      </div>}
      <ModalActions onClose={onClose} saving={saving} disabled={resumes.length === 0} label="Create application" />
    </form>
  </Modal>;
}

function ApplicationFollowUpModal({ item, onClose, onSaved, onError }: {
  item: ActionItem; onClose: () => void; onSaved: (message: string) => Promise<void>; onError: (message: string) => void;
}) {
  const [saving, setSaving] = useState(false);
  const stageOptions = [item.stage, ...(applicationStageProgression[item.stage] ?? [])];
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setSaving(true);
    try {
      await api(`/api/v1/applications/${item.applicationId}/follow-up`, { method: "PATCH", body: JSON.stringify({
        drop: false,
        stage: form.get("stage"),
        nextAction: form.get("nextAction"),
        nextActionAt: new Date(String(form.get("nextActionAt"))).toISOString(),
        note: form.get("note"),
      }) });
      await onSaved("Application status and next follow-up were updated.");
    } catch (error) { onError(error instanceof Error ? error.message : "Could not update the application follow-up."); }
    finally { setSaving(false); }
  }
  async function dropFollowUp() {
    setSaving(true);
    try {
      await api(`/api/v1/applications/${item.applicationId}/follow-up`, { method: "PATCH", body: JSON.stringify({
        drop: true, note: "Follow-up removed from the executive summary by the user.",
      }) });
      await onSaved("Application follow-up dropped; the application remains in the pipeline.");
    } catch (error) { onError(error instanceof Error ? error.message : "Could not drop the application follow-up."); }
    finally { setSaving(false); }
  }
  return <Modal title="Update application follow-up" subtitle={`${item.companyName} · ${item.roleTitle}`} onClose={onClose}>
    <form className="modal-form" onSubmit={(event) => void submit(event)}>
      <div className="form-grid"><Field label="Application status"><select name="stage" defaultValue={item.stage}>
        {stageOptions.map((stage) => <option value={stage} key={stage}>{stageLabels[stage]}</option>)}</select></Field>
        <Field label="Next action date"><input name="nextActionAt" type="datetime-local" required defaultValue={isoToLocalInput(item.nextActionAt)} /></Field></div>
      <Field label="Next action"><input name="nextAction" required defaultValue={item.nextAction} /></Field>
      <Field label="Update note"><textarea name="note" rows={2} placeholder="Optional context for the application timeline" /></Field>
      <div className="followup-modal-note"><strong>Drop only the reminder</strong><span>The application and its history stay in your pipeline; it simply leaves this follow-up queue.</span></div>
      <div className="modal-actions split-actions"><button type="button" className="danger-button" disabled={saving} onClick={() => void dropFollowUp()}>Drop follow-up</button>
        <div><button type="button" className="text-button" onClick={onClose}>Cancel</button><button className="primary-button" disabled={saving}>{saving ? "Saving…" : "Update follow-up"}</button></div></div>
    </form>
  </Modal>;
}

function OutreachFollowUpModal({ item, onClose, onSaved, onError }: {
  item: Outreach; onClose: () => void; onSaved: (message: string) => Promise<void>; onError: (message: string) => void;
}) {
  const [saving, setSaving] = useState(false);
  async function recordFollowUp(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setSaving(true);
    try {
      await api(`/api/v1/outreach/${item.id}/follow-ups`, { method: "POST", body: JSON.stringify({
        nextFollowUpAt: new Date(String(form.get("nextFollowUpAt"))).toISOString(),
        notes: form.get("notes"),
      }) });
      await onSaved(`Follow-up ${item.followUpCount + 1} recorded and the next reminder was scheduled.`);
    } catch (error) { onError(error instanceof Error ? error.message : "Could not record the outreach follow-up."); }
    finally { setSaving(false); }
  }
  async function markResponded() {
    setSaving(true);
    try {
      await api(`/api/v1/outreach/${item.id}`, { method: "PATCH", body: JSON.stringify({
        status: "RESPONDED", followUpAt: null, outcome: "Response received",
      }) });
      await onSaved("Outreach marked as responded and removed from the follow-up queue.");
    } catch (error) { onError(error instanceof Error ? error.message : "Could not mark the outreach as responded."); }
    finally { setSaving(false); }
  }
  async function dropFollowUp() {
    setSaving(true);
    try {
      await api(`/api/v1/outreach/${item.id}`, { method: "PATCH", body: JSON.stringify({
        status: "CLOSED", followUpAt: null, outcome: "Follow-up dropped",
        notes: "Follow-up tracking closed by the user; outreach history retained.",
      }) });
      await onSaved("Follow-up dropped; the outreach remains available in history.");
    } catch (error) { onError(error instanceof Error ? error.message : "Could not drop the outreach follow-up."); }
    finally { setSaving(false); }
  }
  return <Modal title="Update outreach follow-up" subtitle={`${item.contactName} · ${item.companyName} · ${item.roleTitle}`} onClose={onClose}>
    <form className="modal-form" onSubmit={(event) => void recordFollowUp(event)}>
      <div className="outreach-follow-up-summary"><span>Follow-ups recorded</span><strong>{item.followUpCount ?? 0}</strong><small>The original outreach is not included in this count.</small></div>
      <Field label="Next follow-up date"><input name="nextFollowUpAt" type="datetime-local" required defaultValue={localDateTime(72)} /></Field>
      <Field label="Follow-up note"><textarea name="notes" rows={2} placeholder="Optional context about the message you sent" /></Field>
      <div className="followup-modal-note"><strong>Preserve the history</strong><span>Recording a follow-up increments the counter. Dropping it closes active tracking without deleting the outreach record.</span></div>
      <div className="modal-actions split-actions"><button type="button" className="danger-button" disabled={saving} onClick={() => void dropFollowUp()}>Drop follow-up</button>
        <div><button type="button" className="secondary-button" disabled={saving} onClick={() => void markResponded()}>Mark responded</button>
          <button className="primary-button" disabled={saving}>{saving ? "Saving…" : "Record follow-up"}</button></div></div>
    </form>
  </Modal>;
}

function ReferralModal({ opening, contacts, onClose, onSaved, onError }: {
  opening: Opening; contacts: Contact[]; onClose: () => void; onSaved: () => Promise<void>; onError: (message: string) => void;
}) {
  const [saving, setSaving] = useState(false);
  const [contactMode, setContactMode] = useState(contacts.length > 0 ? contacts[0].id : "new");
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    setSaving(true);
    try {
      const existingContact = contactMode !== "new";
      await api("/api/v1/outreach", { method: "POST", body: JSON.stringify({
        opportunityId: opening.opportunityId,
        contactId: existingContact ? contactMode : null,
        newContact: existingContact ? null : {
          fullName: data.get("fullName"), companyName: data.get("contactCompany"), roleTitle: data.get("contactRole"),
          profileUrl: data.get("profileUrl"), relationshipStrength: data.get("relationshipStrength"), notes: data.get("contactNotes"),
        },
        outreachType: data.get("outreachType"), status: data.get("status"), channel: data.get("channel"),
        messageSummary: data.get("messageSummary"), followUpAt: new Date(String(data.get("followUpAt"))).toISOString(),
        notes: data.get("notes"),
      }) });
      await onSaved();
    } catch (error) { onError(error instanceof Error ? error.message : "Could not create referral activity."); }
    finally { setSaving(false); }
  }
  return <Modal title="Track referral" subtitle={`${opening.companyName} · ${opening.roleTitle}`} onClose={onClose}>
    <form className="modal-form" onSubmit={(event) => void submit(event)}>
      {contacts.length > 0 && <Field label="Contact"><select value={contactMode} onChange={(event) => setContactMode(event.target.value)}>
        {contacts.map((contact) => <option key={contact.id} value={contact.id}>{contact.fullName}{contact.companyName ? ` · ${contact.companyName}` : ""}</option>)}
        <option value="new">+ Add a new contact</option>
      </select></Field>}
      {contactMode === "new" && <>
        <div className="form-grid"><Field label="Contact name"><input name="fullName" required /></Field>
          <Field label="Contact company"><input name="contactCompany" defaultValue={opening.companyName} /></Field></div>
        <div className="form-grid"><Field label="Contact role"><input name="contactRole" /></Field>
          <Field label="Profile URL"><input name="profileUrl" type="url" placeholder="https://linkedin.com/in/…" /></Field></div>
        <div className="form-grid"><Field label="Relationship"><select name="relationshipStrength" defaultValue="ACQUAINTANCE">
          <option value="COLD">Cold</option><option value="ACQUAINTANCE">Acquaintance</option>
          <option value="FORMER_COLLEAGUE">Former colleague</option><option value="WARM">Warm</option><option value="STRONG">Strong</option>
        </select></Field><Field label="Contact notes"><input name="contactNotes" placeholder="How do you know them?" /></Field></div>
      </>}
      <div className="form-grid"><Field label="Outreach type"><select name="outreachType" defaultValue="REFERRAL_REQUEST">
        <option value="REFERRAL_REQUEST">Referral request</option><option value="INTRODUCTION_REQUEST">Introduction request</option>
        <option value="RECRUITER_MESSAGE">Recruiter message</option><option value="FOLLOW_UP">Follow-up</option>
      </select></Field><Field label="Current status"><select name="status" defaultValue="PLANNED">
        <option value="PLANNED">Planned</option><option value="SENT">Sent</option>
      </select></Field></div>
      <div className="form-grid"><Field label="Channel"><input name="channel" placeholder="LinkedIn, email, phone…" /></Field>
        <Field label="Follow up"><input name="followUpAt" type="datetime-local" required defaultValue={localDateTime(48)} /></Field></div>
      <Field label="Message plan"><textarea name="messageSummary" rows={3} placeholder="What will you ask, and what context will you provide?" /></Field>
      <Field label="Notes"><textarea name="notes" rows={2} /></Field>
      <ModalActions onClose={onClose} saving={saving} disabled={false} label="Save referral plan" />
    </form>
  </Modal>;
}

function ReferralDiscoveryModal({ dialog, opening, onClose, onSaved, onError }: { dialog: ReferralDiscoveryDialog; opening: Opening;
  onClose: () => void; onSaved: (message: string) => Promise<void>; onError: (message: string) => void }) {
  const [saving,setSaving]=useState(false);
  async function submit(event: FormEvent<HTMLFormElement>){
    event.preventDefault();const data=new FormData(event.currentTarget);setSaving(true);
    try{
      if(dialog.kind==="candidate"){
        await api("/api/v1/referral-candidates",{method:"POST",body:JSON.stringify({
          opportunityId:opening.opportunityId,fullName:data.get("fullName"),companyName:data.get("companyName"),roleTitle:data.get("roleTitle"),
          profileUrl:data.get("profileUrl"),connectionDegree:data.get("connectionDegree"),discoveryChannel:data.get("discoveryChannel"),
          relationshipStrength:data.get("relationshipStrength"),mutualConnectionName:data.get("mutualConnectionName"),mutualConnectionUrl:data.get("mutualConnectionUrl"),
          currentCompanyMatch:Boolean(data.get("currentCompanyMatch")),formerCompanyMatch:Boolean(data.get("formerCompanyMatch")),
          roleRelevance:Number(data.get("roleRelevance")),responsiveness:Number(data.get("responsiveness")),
          lastInteractionOn:data.get("lastInteractionOn")||null,notes:data.get("notes"),
        })});
        await onSaved("Candidate saved with an explainable referral-path score.");
      }else{
        await api(`/api/v1/referral-candidates/${dialog.candidate.id}/outreach`,{method:"POST",body:JSON.stringify({
          channel:data.get("channel"),messageSummary:data.get("messageSummary"),
          followUpAt:new Date(String(data.get("followUpAt"))).toISOString(),notes:data.get("notes"),
        })});
        await onSaved("Candidate converted into a tracked referral plan.");
      }
    }catch(error){onError(error instanceof Error?error.message:"Could not save the referral path.");}finally{setSaving(false);}
  }
  if(dialog.kind==="outreach")return <Modal title="Start thoughtful outreach" subtitle={`${dialog.candidate.fullName} · ${opening.companyName}`} onClose={onClose}>
    <form className="modal-form" onSubmit={(event)=>void submit(event)}>
      <div className={`candidate-score-banner ${dialog.candidate.pathStrength.toLowerCase()}`}><strong>{dialog.candidate.pathScore}</strong><div><span>{formatEnum(dialog.candidate.pathStrength)} referral path</span><p>{dialog.candidate.scoreExplanation}</p></div></div>
      <div className="form-grid"><Field label="Channel"><input name="channel" required defaultValue={formatEnum(dialog.candidate.discoveryChannel)} /></Field>
        <Field label="Follow up"><input name="followUpAt" type="datetime-local" required defaultValue={localDateTime(48)} /></Field></div>
      <Field label="Message plan"><textarea name="messageSummary" rows={4} required defaultValue={`Ask for context on the ${opening.roleTitle} role and whether they would feel comfortable referring me after reviewing my background.`} /></Field>
      <Field label="Private notes"><textarea name="notes" rows={2} placeholder="Context to remember before reaching out" /></Field>
      <ModalActions onClose={onClose} saving={saving} disabled={false} label="Create outreach plan" />
    </form></Modal>;
  return <Modal title="Save a referral candidate" subtitle={`${opening.companyName} · ${opening.roleTitle}`} onClose={onClose}>
    <form className="modal-form" onSubmit={(event)=>void submit(event)}>
      <div className="form-grid"><Field label="Person"><input name="fullName" required /></Field><Field label="Current company"><input name="companyName" defaultValue={opening.companyName} /></Field></div>
      <div className="form-grid"><Field label="Current role"><input name="roleTitle" placeholder="Staff Engineer, Recruiter…" /></Field><Field label="Profile URL"><input name="profileUrl" type="url" placeholder="https://linkedin.com/in/…" /></Field></div>
      <div className="form-grid"><Field label="Connection degree"><select name="connectionDegree" defaultValue="FIRST"><option value="FIRST">1st degree</option><option value="SECOND">2nd degree</option><option value="OTHER">Other path</option></select></Field>
        <Field label="Discovery channel"><select name="discoveryChannel" defaultValue={dialog.channel}>{referralChannels.map((channel)=><option value={channel.value} key={channel.value}>{channel.label}</option>)}<option value="WHATSAPP">WhatsApp</option><option value="OTHER">Other</option></select></Field></div>
      <div className="form-grid"><Field label="Relationship"><select name="relationshipStrength" defaultValue="ACQUAINTANCE"><option value="COLD">Cold</option><option value="ACQUAINTANCE">Acquaintance</option><option value="FORMER_COLLEAGUE">Former colleague</option><option value="WARM">Warm</option><option value="STRONG">Strong</option></select></Field>
        <Field label="Last interaction"><input name="lastInteractionOn" type="date" /></Field></div>
      <div className="checkbox-row"><label><input type="checkbox" name="currentCompanyMatch" defaultChecked /><span>Currently at target company</span></label>
        <label><input type="checkbox" name="formerCompanyMatch" /><span>Formerly at target company</span></label></div>
      <div className="form-grid"><Field label="Role proximity"><select name="roleRelevance" defaultValue="3"><option value="1">1 · Distant</option><option value="2">2 · Adjacent</option><option value="3">3 · Relevant</option><option value="4">4 · Close</option><option value="5">5 · Same team/domain</option></select></Field>
        <Field label="Likely responsiveness"><select name="responsiveness" defaultValue="3"><option value="1">1 · Unknown / low</option><option value="2">2 · Limited</option><option value="3">3 · Reasonable</option><option value="4">4 · Likely</option><option value="5">5 · Very likely</option></select></Field></div>
      <div className="form-grid"><Field label="Mutual connection"><input name="mutualConnectionName" placeholder="Especially useful for 2nd degree" /></Field><Field label="Mutual profile URL"><input name="mutualConnectionUrl" type="url" /></Field></div>
      <Field label="Why this path may work"><textarea name="notes" rows={3} placeholder="Shared work, specific team relevance, or context for a warm introduction" /></Field>
      <p className="form-hint">The strength score is an explainable prioritization aid based only on the evidence entered here.</p>
      <ModalActions onClose={onClose} saving={saving} disabled={false} label="Save candidate" />
    </form></Modal>;
}

function StoryDeleteModal({ target, onClose, onSaved, onError }: {
  target: { milestone: PrepMilestone; trackName: string }; onClose: () => void;
  onSaved: (message: string) => Promise<void>; onError: (message: string) => void;
}) {
  const [confirmed, setConfirmed] = useState(false); const [saving, setSaving] = useState(false);
  const taskCount = target.milestone.items.length;
  async function remove() {
    setSaving(true);
    try {
      await api(`/api/v1/preparation/milestones/${target.milestone.id}`, { method: "DELETE" });
      await onSaved(`“${target.milestone.title}” was deleted.`);
    } catch (error) { onError(error instanceof Error ? error.message : "Could not delete the story."); }
    finally { setSaving(false); }
  }
  return <Modal title="Delete story" subtitle={`Track — ${target.trackName}`} onClose={onClose}>
    <div className="story-delete-dialog"><div className="story-delete-warning"><span>Permanent deletion</span><strong>{target.milestone.title}</strong>
      <p>This will remove the Story and {taskCount} task{taskCount === 1 ? "" : "s"}. Any linked sprint placement, practice sessions, and skill-plan links for those tasks will also be removed.</p></div>
      <label className="delete-confirmation"><input type="checkbox" checked={confirmed} onChange={(event) => setConfirmed(event.target.checked)} />
        <span>I understand this Story and its task history cannot be recovered.</span></label>
      <div className="modal-actions"><button type="button" className="text-button" onClick={onClose}>Cancel</button>
        <button type="button" className="danger-button" disabled={!confirmed || saving} onClick={() => void remove()}>{saving ? "Deleting…" : "Delete story"}</button></div></div>
  </Modal>;
}

function SprintCloseModal({ sprint, saving, onClose, onConfirm }: {
  sprint: PrepSprint; saving: boolean; onClose: () => void; onConfirm: () => void;
}) {
  const [confirmed, setConfirmed] = useState(false);
  const statusOrder: PrepItemStatus[] = ["COMPLETED", "IN_REVIEW", "IN_PROGRESS", "READY", "BACKLOG", "SKIPPED"];
  const tasks = [...sprint.tasks].sort((left, right) => statusOrder.indexOf(left.status) - statusOrder.indexOf(right.status)
    || left.title.localeCompare(right.title));
  return <Modal title="Close current sprint" subtitle={formatSprintRange(sprint)} onClose={onClose} wide>
    <div className="sprint-close-dialog">
      <p className="sprint-close-intro">Review the final sprint state before closing it. The completed sprint and its task snapshot will remain in your history.</p>
      <div className="sprint-close-summary">
        <article><strong>{sprint.summary.done}</strong><span>Done</span><small>Remain completed</small></article>
        <article><strong>{sprint.summary.inReview}</strong><span>In review</span><small>Remain in review</small></article>
        <article><strong>{sprint.summary.inProgress}</strong><span>In progress</span><small>Remain in progress</small></article>
        <article><strong>{sprint.summary.open}</strong><span>Not completed</span><small>Open work returns to backlog</small></article>
      </div>
      <div className="sprint-close-task-list">{tasks.map((task) => <article key={task.itemId}>
        <span className={`status-pill ${task.status.toLowerCase()}`}>{sprintStageLabel(task.status)}</span><strong>{task.title}</strong>
      </article>)}</div>
      <label className="delete-confirmation"><input type="checkbox" checked={confirmed} onChange={(event) => setConfirmed(event.target.checked)} />
        <span>I have reviewed the sprint summary and want to preserve this sprint as closed.</span></label>
      <div className="modal-actions"><button type="button" className="text-button" onClick={onClose}>Cancel</button>
        <button type="button" className="danger-button" disabled={!confirmed || saving} onClick={onConfirm}>{saving ? "Closing…" : "Close current sprint"}</button></div>
    </div>
  </Modal>;
}

function TaskDetailsDialog({ target, openings, onClose, onSaved, onError }: {
  target: TaskDetailsTarget; openings: Opening[]; onClose: () => void;
  onSaved: (message: string) => Promise<void>; onError: (message: string) => void;
}) {
  const { item, track, milestone } = target.context;
  const [tab, setTab] = useState<"DETAILS" | "SESSION">(target.initialTab);
  const [sessions, setSessions] = useState<PrepSession[]>([]);
  const [loadingSessions, setLoadingSessions] = useState(true);
  const [saving, setSaving] = useState(false);
  const onSessionsError = useEffectEvent((error: unknown) => {
    onError(error instanceof Error ? error.message : "Could not load task sessions.");
  });
  useEffect(() => {
    let active = true;
    api<PrepSession[]>(`/api/v1/preparation/items/${item.id}/sessions`)
      .then((result) => { if (active) setSessions(result); })
      .catch((error) => { if (active) onSessionsError(error); })
      .finally(() => { if (active) setLoadingSessions(false); });
    return () => { active = false; };
  }, [item.id]);
  const loggedMinutes = sessions.reduce((total, session) => total + session.durationMinutes, 0);
  async function saveDetails(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setSaving(true); const data = new FormData(event.currentTarget);
    const date = (name: string) => String(data.get(name) ?? "") || null;
    try {
      await api(`/api/v1/preparation/items/${item.id}`, { method: "PUT", body: JSON.stringify({
        title: data.get("title"), description: data.get("description"), status: data.get("status"),
        priority: Number(data.get("priority")), estimatedMinutes: Number(data.get("estimatedMinutes")),
        scheduledFor: date("scheduledFor"), dueDate: date("dueDate"), skillFocus: data.get("skillFocus"),
        nextReviewOn: date("nextReviewOn"), opportunityId: data.get("opportunityId") || null,
      }) });
      await onSaved("Task details updated.");
    } catch (error) { onError(error instanceof Error ? error.message : "Could not update the task."); }
    finally { setSaving(false); }
  }
  async function logSession(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setSaving(true); const data = new FormData(event.currentTarget);
    const practicedAt = String(data.get("practicedAt") ?? "");
    try {
      await api(`/api/v1/preparation/items/${item.id}/sessions`, { method: "POST", body: JSON.stringify({
        sessionType: data.get("sessionType"), practicedAt: practicedAt ? new Date(practicedAt).toISOString() : new Date().toISOString(),
        durationMinutes: Number(data.get("durationMinutes")), resultSummary: data.get("resultSummary"),
        mistakes: data.get("mistakes"), nextSteps: data.get("nextSteps"),
        confidenceBefore: Number(data.get("confidenceBefore")) || null, confidenceAfter: Number(data.get("confidenceAfter")) || null,
        nextReviewOn: String(data.get("nextReviewOn") ?? "") || null,
      }) });
      await onSaved("Work session recorded against the task.");
    } catch (error) { onError(error instanceof Error ? error.message : "Could not record the session."); }
    finally { setSaving(false); }
  }
  return <Modal title="Task details" subtitle={`Track ${String(track.displayOrder + 1).padStart(2, "0")} · ${track.name} · ${milestone.title}`} onClose={onClose} wide>
    <div className="task-detail-shell">
      <header className="task-detail-summary"><div><span className={`status-pill ${item.status.toLowerCase()}`}>{formatEnum(item.status)}</span><h3>{item.title}</h3></div>
        <div><article><strong>{item.estimatedMinutes}</strong><span>estimated min</span></article><article><strong>{loggedMinutes}</strong><span>logged min</span></article>
          <article><strong>{sessions.length}</strong><span>sessions</span></article></div></header>
      <nav className="task-detail-tabs" aria-label="Task detail sections"><button type="button" className={tab === "DETAILS" ? "active" : ""} onClick={() => setTab("DETAILS")}>Details & planning</button>
        <button type="button" className={tab === "SESSION" ? "active" : ""} onClick={() => setTab("SESSION")}>Sessions & time <span>{sessions.length}</span></button></nav>
      {tab === "DETAILS" ? <form className="modal-form task-detail-form" onSubmit={(event) => void saveDetails(event)}>
        <Field label="Task title"><input name="title" required defaultValue={item.title} /></Field>
        <div className="form-grid"><Field label="Status"><select name="status" defaultValue={item.status}><option value="BACKLOG">Backlog</option><option value="READY">Open</option>
          <option value="IN_PROGRESS">In progress</option><option value="IN_REVIEW">In review</option><option value="COMPLETED">Done</option><option value="SKIPPED">Skipped</option></select></Field>
          <Field label="Priority"><select name="priority" defaultValue={item.priority}>{[1,2,3,4,5].map((priority) => <option value={priority} key={priority}>P{priority}{priority === 1 ? " · Highest" : ""}</option>)}</select></Field></div>
        <div className="form-grid"><Field label="Estimated minutes"><input name="estimatedMinutes" type="number" min="1" required defaultValue={item.estimatedMinutes} /></Field>
          <Field label="Scheduled for"><input name="scheduledFor" type="date" defaultValue={item.scheduledFor ?? ""} /></Field></div>
        <div className="form-grid"><Field label="Due date"><input name="dueDate" type="date" defaultValue={item.dueDate ?? ""} /></Field>
          <Field label="Next review"><input name="nextReviewOn" type="date" defaultValue={item.nextReviewOn ?? ""} /></Field></div>
        <Field label="Skill focus"><input name="skillFocus" defaultValue={item.skillFocus ?? ""} placeholder="Concepts and capabilities this task develops" /></Field>
        <Field label="Relevant opening (optional)"><select name="opportunityId" defaultValue={item.opportunityId ?? ""}><option value="">No specific opening</option>
          {openings.map((opening) => <option key={opening.opportunityId} value={opening.opportunityId}>{opening.companyName} · {opening.roleTitle}</option>)}</select></Field>
        <Field label="Task brief / notes"><textarea name="description" rows={4} defaultValue={item.description ?? ""} /></Field>
        <ModalActions onClose={onClose} saving={saving} disabled={false} label="Save task" />
      </form> : <div className="task-session-layout"><form className="task-session-form" onSubmit={(event) => void logSession(event)}>
        <div><p className="section-label">Record focused work</p><h3>Log a session</h3><p>Capture the actual time and evidence produced today.</p></div>
        <div className="form-grid"><Field label="Session type"><select name="sessionType" defaultValue="DRILL"><option value="DRILL">Drill</option><option value="STUDY">Study</option>
          <option value="MOCK_INTERVIEW">Mock interview</option><option value="REVIEW">Review</option><option value="BUILD">Build</option></select></Field>
          <Field label="Duration (minutes)"><input name="durationMinutes" type="number" min="1" required defaultValue={item.estimatedMinutes} /></Field></div>
        <Field label="Session date and time"><input name="practicedAt" type="datetime-local" required defaultValue={localDateTimeInputValue(new Date())} /></Field>
        <div className="form-grid"><Field label="Confidence before"><select name="confidenceBefore" defaultValue=""><option value="">Not scored</option>{[1,2,3,4,5].map((n) => <option key={n}>{n}</option>)}</select></Field>
          <Field label="Confidence after"><select name="confidenceAfter" defaultValue=""><option value="">Not scored</option>{[1,2,3,4,5].map((n) => <option key={n}>{n}</option>)}</select></Field></div>
        <Field label="Outcome / evidence"><textarea name="resultSummary" rows={3} placeholder="What did you complete, learn, or produce?" /></Field>
        <Field label="Mistakes or gaps"><textarea name="mistakes" rows={2} placeholder="What needs correction or more practice?" /></Field>
        <Field label="Next step"><textarea name="nextSteps" rows={2} placeholder="What is the smallest useful follow-up?" /></Field>
        <Field label="Review again on"><input name="nextReviewOn" type="date" defaultValue={item.nextReviewOn ?? ""} /></Field>
        <ModalActions onClose={onClose} saving={saving} disabled={false} label="Save session" />
      </form><aside className="task-session-history"><header><p className="section-label">Task history</p><h3>{loggedMinutes} minutes recorded</h3></header>
        {loadingSessions ? <p className="inline-empty">Loading session history…</p> : sessions.length === 0 ? <p className="inline-empty">No sessions recorded yet. Your first entry will establish the task’s actual time.</p>
          : <div>{sessions.map((session) => <article key={session.id}><header><strong>{formatEnum(session.sessionType)}</strong><span>{session.durationMinutes} min</span></header>
            <small>{formatDateTime(session.practicedAt)}{session.confidenceAfter ? ` · confidence ${session.confidenceAfter}/5` : ""}</small>
            <p>{session.resultSummary ?? session.nextSteps ?? "Session recorded"}</p></article>)}</div>}</aside></div>}
    </div>
  </Modal>;
}

function PreparationDialog({ dialog, openings, onClose, onSaved, onError }: { dialog: PrepDialog; openings: Opening[];
  onClose: () => void; onSaved: (message: string) => Promise<void>; onError: (message: string) => void }) {
  const [saving, setSaving] = useState(false);
  const titles = { track: "Create preparation track", milestone: "Add story", "story-edit": "Edit story", item: "Add an actionable prep item",
    focus: "Make today’s commitment", session: "Log practice evidence" };
  const subtitles = { track: "Create a durable lane for one capability.", milestone: dialog.kind === "milestone" ? `Track — ${dialog.label}` : "Define the next outcome.",
    "story-edit": dialog.kind === "story-edit" ? `Track — ${dialog.trackName}` : "Update this story.",
    item: dialog.kind === "item" ? dialog.label : "Break the outcome into practice.", focus: dialog.kind === "focus" ? dialog.item.title : "Choose today’s focus.",
    session: dialog.kind === "session" ? dialog.item.title : "Preserve what changed." };
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const data = new FormData(event.currentTarget); setSaving(true);
    const date = (name: string) => String(data.get(name) ?? "") || null;
    try {
      if (dialog.kind === "track") await api("/api/v1/preparation/tracks", { method: "POST", body: JSON.stringify({
        name: data.get("name"), description: data.get("description"), category: data.get("category"), targetDate: date("targetDate"), displayOrder: 0,
      }) });
      if (dialog.kind === "milestone") await api(`/api/v1/preparation/tracks/${dialog.trackId}/milestones`, { method: "POST", body: JSON.stringify({
        title: data.get("title"), description: data.get("description"), targetDate: date("targetDate"), displayOrder: 0,
      }) });
      if (dialog.kind === "story-edit") await api(`/api/v1/preparation/milestones/${dialog.milestone.id}`, { method: "PATCH", body: JSON.stringify({
        title: data.get("title"), description: data.get("description"), targetDate: date("targetDate"),
      }) });
      if (dialog.kind === "item") await api(`/api/v1/preparation/milestones/${dialog.milestoneId}/items`, { method: "POST", body: JSON.stringify({
        title: data.get("title"), description: data.get("description"), priority: Number(data.get("priority")), estimatedMinutes: Number(data.get("estimatedMinutes")),
        scheduledFor: null, dueDate: date("dueDate"), skillFocus: data.get("skillFocus"), opportunityId: data.get("opportunityId") || null, displayOrder: 0,
      }) });
      if (dialog.kind === "focus") await api("/api/v1/preparation/today", { method: "PUT", body: JSON.stringify({
        prepItemId: dialog.item.id, plannedMinutes: Number(data.get("plannedMinutes")), intention: data.get("intention"),
      }) });
      if (dialog.kind === "session") await api(`/api/v1/preparation/items/${dialog.item.id}/sessions`, { method: "POST", body: JSON.stringify({
        sessionType: data.get("sessionType"), practicedAt: new Date().toISOString(), durationMinutes: Number(data.get("durationMinutes")),
        resultSummary: data.get("resultSummary"), mistakes: data.get("mistakes"), nextSteps: data.get("nextSteps"),
        confidenceBefore: Number(data.get("confidenceBefore")) || null, confidenceAfter: Number(data.get("confidenceAfter")) || null, nextReviewOn: date("nextReviewOn"),
      }) });
      const messages = { track: "Preparation track created.", milestone: "Story added to the track.", "story-edit": "Story updated.", item: "Preparation task added to the backlog.",
        focus: "Today’s focused preparation commitment is set.", session: "Practice session saved as evidence." };
      await onSaved(messages[dialog.kind]);
    } catch (error) { onError(error instanceof Error ? error.message : "Could not save preparation work."); }
    finally { setSaving(false); }
  }
  return <Modal title={titles[dialog.kind]} subtitle={subtitles[dialog.kind]} onClose={onClose}>
    <form className="modal-form" onSubmit={(event) => void submit(event)}>
      {dialog.kind === "track" && <><Field label="Track name"><input name="name" required placeholder="System Design" /></Field>
        <div className="form-grid"><Field label="Category"><select name="category" defaultValue="SYSTEM_DESIGN"><option value="SYSTEM_DESIGN">System design</option><option value="OBJECT_ORIENTED_DESIGN">Object-oriented design</option><option value="API_DESIGN">API design</option><option value="JAVA_BACKEND">Java backend</option><option value="DATA_STRUCTURES">Data structures</option><option value="BEHAVIORAL">Behavioral</option><option value="DOMAIN">Domain depth</option><option value="AI_DATA">AI & data</option><option value="OTHER">Other</option></select></Field>
          <Field label="Target date"><input name="targetDate" type="date" /></Field></div>
        <Field label="Purpose"><textarea name="description" rows={3} placeholder="What capability will this track build?" /></Field></>}
      {dialog.kind === "milestone" && <><Field label="Story title"><input name="title" required placeholder="Design three production-scale systems end to end" /></Field>
        <Field label="Target date (optional)"><input name="targetDate" type="date" /></Field><Field label="Outcome"><textarea name="description" rows={3} placeholder="What evidence will prove this story is complete?" /></Field></>}
      {dialog.kind === "story-edit" && <><Field label="Story title"><input name="title" required defaultValue={dialog.milestone.title} /></Field>
        <Field label="Target date (optional)"><input name="targetDate" type="date" defaultValue={dialog.milestone.targetDate ?? ""} /></Field>
        <Field label="Outcome"><textarea name="description" rows={3} defaultValue={dialog.milestone.description ?? ""} placeholder="What evidence will prove this story is complete?" /></Field></>}
      {dialog.kind === "item" && <><Field label="Preparation item"><input name="title" required placeholder="Design a multi-region notification service" /></Field>
        <div className="form-grid"><Field label="Priority"><select name="priority" defaultValue="2"><option value="1">1 · Highest</option><option value="2">2 · High</option><option value="3">3 · Medium</option><option value="4">4 · Low</option><option value="5">5 · Someday</option></select></Field>
          <Field label="Estimated minutes"><input name="estimatedMinutes" type="number" min="1" required defaultValue="45" /></Field></div>
        <Field label="Due date (optional)"><input name="dueDate" type="date" /></Field>
        <Field label="Skill focus"><input name="skillFocus" placeholder="Capacity estimation, queues, idempotency" /></Field>
        {openings.length > 0 && <Field label="Relevant opening (optional)"><select name="opportunityId" defaultValue=""><option value="">No specific opening</option>{openings.map((opening) => <option key={opening.opportunityId} value={opening.opportunityId}>{opening.companyName} · {opening.roleTitle}</option>)}</select></Field>}
        <Field label="Practice brief"><textarea name="description" rows={3} placeholder="Scope, constraints, and what good practice looks like." /></Field></>}
      {dialog.kind === "focus" && <><div className="focus-form-banner"><span>One commitment</span><strong>{dialog.item.title}</strong><small>{dialog.item.estimatedMinutes} minute estimate</small></div>
        <Field label="Planned minutes"><input name="plannedMinutes" type="number" min="1" required defaultValue={dialog.item.estimatedMinutes} /></Field>
        <Field label="Success intention"><textarea name="intention" rows={3} placeholder="At the end of this session I will have…" /></Field></>}
      {dialog.kind === "session" && <><div className="form-grid"><Field label="Session type"><select name="sessionType" defaultValue="DRILL"><option value="DRILL">Drill</option><option value="STUDY">Study</option><option value="MOCK_INTERVIEW">Mock interview</option><option value="REVIEW">Review</option><option value="BUILD">Build</option></select></Field>
        <Field label="Duration (minutes)"><input name="durationMinutes" type="number" min="1" required defaultValue={dialog.item.estimatedMinutes} /></Field></div>
        <div className="form-grid"><Field label="Confidence before"><select name="confidenceBefore" defaultValue=""><option value="">Not scored</option>{[1,2,3,4,5].map((n) => <option key={n}>{n}</option>)}</select></Field>
          <Field label="Confidence after"><select name="confidenceAfter" defaultValue=""><option value="">Not scored</option>{[1,2,3,4,5].map((n) => <option key={n}>{n}</option>)}</select></Field></div>
        <Field label="What changed"><textarea name="resultSummary" rows={3} placeholder="Result, insight, or concrete artifact produced." /></Field>
        <Field label="Mistakes / gaps"><textarea name="mistakes" rows={2} placeholder="Where did your reasoning or recall break down?" /></Field>
        <Field label="Next step"><textarea name="nextSteps" rows={2} placeholder="The smallest follow-up that compounds this session." /></Field>
        <Field label="Review again on"><input name="nextReviewOn" type="date" /></Field></>}
      <ModalActions onClose={onClose} saving={saving} disabled={false} label={dialog.kind === "milestone" ? "Add story" : dialog.kind === "story-edit" ? "Save story" : dialog.kind === "session" ? "Save session" : dialog.kind === "focus" ? "Commit for today" : "Save"} />
    </form>
  </Modal>;
}

function SkillEvidenceDialog({ dialog, openings, skills, onClose, onSaved, onError }: { dialog: SkillDialog; openings: Opening[];
  skills: SkillView[]; onClose: () => void; onSaved: (message: string) => Promise<void>; onError: (message: string) => void }) {
  const [saving, setSaving] = useState(false);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setSaving(true); const data = new FormData(event.currentTarget);
    try {
      if (dialog.kind === "skill") {
        const aliases = String(data.get("aliases") ?? "").split(",").map((item) => item.trim()).filter(Boolean);
        await api("/api/v1/skills", { method: "POST", body: JSON.stringify({
          name: data.get("name"), category: data.get("category"), description: data.get("description"), aliases,
        }) });
        await onSaved("Canonical skill added to the market taxonomy.");
      } else if (dialog.kind === "evidence") {
        const snapshot = await api<{ id: string }>("/api/v1/skills/snapshots", { method: "POST", body: JSON.stringify({
          opportunityId: data.get("opportunityId"), sourceType: "PASTED_DESCRIPTION",
          sourceLabel: data.get("sourceLabel"), content: data.get("content"),
        }) });
        await api("/api/v1/skills/observations", { method: "POST", body: JSON.stringify({
          snapshotId: snapshot.id, skillId: data.get("skillId"), strength: data.get("strength"),
          evidenceSnippet: data.get("evidenceSnippet"), extractionMethod: "MANUAL",
        }) });
        await onSaved("Evidence captured as Proposed and added to your review queue.");
      } else if (dialog.kind === "correction") {
        await api(`/api/v1/skills/observations/${dialog.observation.id}/correction`, { method: "PATCH", body: JSON.stringify({
          skillId: data.get("skillId"), strength: data.get("strength"), evidenceSnippet: data.get("evidenceSnippet"),
          note: data.get("note") || "Corrected and accepted during human review.",
        }) });
        await onSaved("The corrected evidence was accepted and preserved in the review history.");
      } else {
        await api(`/api/v1/skills/${dialog.source.id}/merge`, { method: "POST", body: JSON.stringify({ targetSkillId: data.get("targetSkillId") }) });
        await onSaved(`${dialog.source.name} was merged into the selected canonical skill.`);
      }
    } catch (error) { onError(error instanceof Error ? error.message : "Could not save the skill evidence."); }
    finally { setSaving(false); }
  }
  const title = dialog.kind === "skill" ? "Add canonical skill" : dialog.kind === "evidence" ? "Capture job-description evidence"
    : dialog.kind === "correction" ? "Correct proposed evidence" : "Merge duplicate skill";
  const subtitle = dialog.kind === "skill" ? "Create one reusable taxonomy entry and its matching aliases."
    : dialog.kind === "evidence" ? "Preserve the source first; review and accept the signal separately."
      : dialog.kind === "correction" ? "Correct the canonical skill, strength, or exact source excerpt; the result will be accepted as a human-reviewed signal."
        : `Move aliases and evidence from ${dialog.source.name} into the canonical target.`;
  return <Modal title={title}
    subtitle={subtitle} onClose={onClose} wide={dialog.kind === "evidence" || dialog.kind === "correction"}>
    <form className="modal-form" onSubmit={submit}>
      {dialog.kind === "skill" ? <>
        <div className="form-grid"><Field label="Canonical name"><input name="name" required placeholder="LangGraph" /></Field>
          <Field label="Category"><select name="category" defaultValue="AI_AGENT">{(["AI_AGENT", "BACKEND", "CLOUD_INFRASTRUCTURE", "DATA_PLATFORM", "DATABASE", "DEVOPS", "SECURITY", "OTHER"] as SkillCategory[]).map((category) => <option value={category} key={category}>{formatEnum(category)}</option>)}</select></Field></div>
        <Field label="Description"><textarea name="description" rows={3} placeholder="What this capability means in the roles you are targeting." /></Field>
        <Field label="Aliases (comma-separated)"><input name="aliases" placeholder="Lang Graph, stateful agent graphs" /></Field>
      </> : dialog.kind === "evidence" ? <>
        <div className="evidence-form-callout"><strong>Human-in-the-loop by design</strong><span>Saving creates a Proposed observation. It will not count as trusted evidence until you accept it in the review queue.</span></div>
        <div className="form-grid"><Field label="Opening"><select name="opportunityId" required defaultValue=""><option value="" disabled>Select an active opening</option>{openings.map((opening) => <option value={opening.opportunityId} key={opening.opportunityId}>{opening.companyName} · {opening.roleTitle}</option>)}</select></Field>
          <Field label="Canonical skill"><select name="skillId" required defaultValue=""><option value="" disabled>Select a skill</option>{skills.map((skill) => <option value={skill.id} key={skill.id}>{skill.name}</option>)}</select></Field></div>
        <div className="form-grid"><Field label="Signal strength"><select name="strength" defaultValue="REQUIRED"><option value="REQUIRED">Required</option><option value="PREFERRED">Preferred</option><option value="MENTIONED">Mentioned</option></select></Field>
          <Field label="Source label"><input name="sourceLabel" placeholder="Company careers page · 26 Aug 2026" /></Field></div>
        <Field label="Source job description"><textarea name="content" rows={8} required placeholder="Paste the complete source text so the evidence remains auditable." /></Field>
        <Field label="Exact evidence snippet"><textarea name="evidenceSnippet" rows={3} required placeholder="Paste the sentence or bullet that supports this skill signal." /></Field>
      </> : dialog.kind === "correction" ? <>
        <div className="evidence-form-callout"><strong>Human correction</strong><span>The corrected record will be marked Accepted and its extraction method changed to Manual.</span></div>
        <div className="form-grid"><Field label="Canonical skill"><select name="skillId" required defaultValue={dialog.observation.skillId}>{skills.map((skill) => <option value={skill.id} key={skill.id}>{skill.name}</option>)}</select></Field>
          <Field label="Signal strength"><select name="strength" defaultValue={dialog.observation.strength}><option value="REQUIRED">Required</option><option value="PREFERRED">Preferred</option><option value="MENTIONED">Mentioned</option></select></Field></div>
        <Field label="Exact evidence snippet"><textarea name="evidenceSnippet" rows={5} required defaultValue={dialog.observation.evidenceSnippet} /></Field>
        <Field label="Review note"><textarea name="note" rows={2} defaultValue="Corrected and accepted during human review." /></Field>
      </> : <>
        <Field label="Merge into"><select name="targetSkillId" required defaultValue=""><option value="" disabled>Select the canonical target</option>
          {skills.filter((skill) => skill.id !== dialog.source.id).map((skill) => <option value={skill.id} key={skill.id}>{skill.name} · {formatEnum(skill.category)}</option>)}</select></Field>
        <div className="evidence-form-callout"><strong>Evidence-preserving merge</strong><span>{dialog.source.name} will become an alias. Existing observations move to the target and duplicate evidence is collapsed without losing accepted review decisions.</span></div>
      </>}
      <ModalActions onClose={onClose} saving={saving} disabled={dialog.kind === "evidence" && (openings.length === 0 || skills.length === 0)}
        label={dialog.kind === "skill" ? "Add skill" : dialog.kind === "evidence" ? "Add to review queue" : dialog.kind === "correction" ? "Save correction" : "Merge skill"} />
    </form>
  </Modal>;
}

function Modal({ title, subtitle, onClose, children, wide = false }: { title: string; subtitle: string; onClose: () => void; children: React.ReactNode; wide?: boolean }) {
  const ref = useAccessibleDialog(onClose);
  const titleId = useId();
  return <div className="modal-backdrop"><section ref={ref} tabIndex={-1} className={`modal ${wide ? "modal-wide" : ""}`} role="dialog" aria-modal="true" aria-labelledby={titleId}>
    <header><div><p className="eyebrow">Workflow</p><h2 id={titleId}>{title}</h2><p>{subtitle}</p></div><button className="close-button" onClick={onClose} aria-label="Close">×</button></header>{children}</section></div>;
}

/** Keep keyboard/screen-reader interaction inside the topmost dialog, including nested calendar editors. */
function useAccessibleDialog(onClose: () => void) {
  const ref = useRef<HTMLElement>(null);
  const close = useEffectEvent(onClose);
  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    const opener = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const hidden: HTMLElement[] = [];
    let branch: HTMLElement = dialog;
    while (branch.parentElement && branch !== document.body) {
      for (const sibling of branch.parentElement.children) {
        if (sibling !== branch && sibling instanceof HTMLElement && !sibling.inert) { sibling.inert = true; hidden.push(sibling); }
      }
      branch = branch.parentElement;
    }
    const oldOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    const focusable = () => Array.from(dialog.querySelectorAll<HTMLElement>('button:not(:disabled), a[href], input:not(:disabled), select:not(:disabled), textarea:not(:disabled), summary, [tabindex="0"]'))
      .filter((element) => element.getClientRects().length > 0 && !element.closest('[inert]'));
    (focusable()[0] ?? dialog).focus();
    const topmost = () => Array.from(document.querySelectorAll<HTMLElement>('[role="dialog"]'))
      .filter((element) => element.getClientRects().length > 0 && !element.closest('[inert]')).at(-1) === dialog;
    const keydown = (event: KeyboardEvent) => {
      if (!topmost()) return;
      if (event.key === "Escape") { event.preventDefault(); event.stopPropagation(); close(); }
      if (event.key === "Tab") {
        const items = focusable(); const first = items[0]; const last = items[items.length - 1];
        if (!first) { event.preventDefault(); dialog.focus(); }
        else if (event.shiftKey && (document.activeElement === first || !dialog.contains(document.activeElement))) { event.preventDefault(); last.focus(); }
        else if (!event.shiftKey && (document.activeElement === last || !dialog.contains(document.activeElement))) { event.preventDefault(); first.focus(); }
      }
    };
    const containFocus = (event: FocusEvent) => { if (topmost() && !dialog.contains(event.target as Node)) (focusable()[0] ?? dialog).focus(); };
    document.addEventListener("keydown", keydown);
    document.addEventListener("focusin", containFocus);
    return () => {
      document.removeEventListener("keydown", keydown); document.removeEventListener("focusin", containFocus);
      hidden.forEach((element) => { element.inert = false; }); document.body.style.overflow = oldOverflow;
      if (opener?.isConnected) opener.focus();
    };
  }, []);
  return ref;
}
function Field({ label, children }: { label: string; children: React.ReactNode }) { return <label className="field"><span>{label}</span>{children}</label>; }
function ModalActions({ onClose, saving, disabled, label }: { onClose: () => void; saving: boolean; disabled: boolean; label: string }) {
  return <div className="modal-actions"><button type="button" className="text-button" onClick={onClose}>Cancel</button><button className="primary-button" disabled={saving || disabled}>{saving ? "Saving…" : label}</button></div>;
}

type PrepTaskContext = { item: PrepItem; track: PrepTrack; milestone: PrepMilestone };
type SprintBoardStage = "OPEN" | "IN_PROGRESS" | "IN_REVIEW" | "DONE";

function PreparationPortfolio({ preparation, onOpenTrack, onCreateTrack, busyTaskId, onUpdateStatus, onUpdatePriority,
  onOpenTask, sprintLifecycleBusy, onStartSprint, onRequestCloseSprint, trackOrderBusy, onReorderTracks }: {
  preparation: PreparationOverview; onOpenTrack: (trackId: string) => void; onCreateTrack: () => void;
  busyTaskId: string | null; onUpdateStatus: (item: PrepItem, status: PrepItemStatus) => void;
  onUpdatePriority: (item: PrepItem, priority: number) => void;
  onOpenTask: (context: PrepTaskContext, initialTab?: "DETAILS" | "SESSION") => void;
  sprintLifecycleBusy: boolean; onStartSprint: () => void; onRequestCloseSprint: (sprint: PrepSprint) => void;
  trackOrderBusy: boolean; onReorderTracks: (trackIds: string[]) => void;
}) {
  const [draggedTrackId, setDraggedTrackId] = useState<string | null>(null);
  const [dragOverTrackId, setDragOverTrackId] = useState<string | null>(null);
  const tasks = flattenPreparationTasks(preparation.tracks);
  const sprint = preparation.activeSprint;
  const sprintTasks = sprintTaskContexts(tasks, sprint?.tasks.map((task) => task.itemId) ?? []);
  const visibleSprintTasks = sprintTasks;
  const sprintCompleted = visibleSprintTasks.filter(({ item }) => item.status === "COMPLETED").length;
  const sprintProgress = visibleSprintTasks.length > 0 ? Math.round((sprintCompleted / visibleSprintTasks.length) * 100) : 0;
  const activeMinutes = visibleSprintTasks.filter(({ item }) => !["COMPLETED", "SKIPPED"].includes(item.status))
    .reduce((sum, { item }) => sum + item.estimatedMinutes, 0);
  const sprintColumns: { stage: SprintBoardStage; label: string; guidance: string }[] = [
    { stage: "OPEN", label: "Open", guidance: "Ready to begin" },
    { stage: "IN_PROGRESS", label: "In progress", guidance: "Actively being worked" },
    { stage: "IN_REVIEW", label: "In review", guidance: "Validate the outcome" },
    { stage: "DONE", label: "Done", guidance: "Completed this sprint" },
  ];
  const orderedTracks = [...preparation.tracks].sort((left, right) => left.displayOrder - right.displayOrder || left.name.localeCompare(right.name));
  const trackNumberById = new Map(orderedTracks.map((track, index) => [track.id, index + 1]));
  function dropTrack(targetTrackId: string) {
    if (!draggedTrackId || draggedTrackId === targetTrackId || trackOrderBusy) { setDraggedTrackId(null); setDragOverTrackId(null); return; }
    const reordered = [...orderedTracks];
    const sourceIndex = reordered.findIndex((track) => track.id === draggedTrackId);
    const targetIndex = reordered.findIndex((track) => track.id === targetTrackId);
    if (sourceIndex < 0 || targetIndex < 0) return;
    const [moved] = reordered.splice(sourceIndex, 1); reordered.splice(targetIndex, 0, moved);
    setDraggedTrackId(null); setDragOverTrackId(null); onReorderTracks(reordered.map((track) => track.id));
  }

  return <div className="preparation-portfolio">
    <section className="sprint-dashboard" aria-label="Current two-week preparation sprint">
      <div className="sprint-summary"><div><p className="section-label">Current two-week sprint</p><h3>{sprint ? formatSprintRange(sprint) : "No active sprint"}</h3>
        <p>{sprint ? (sprintTasks.length > 0 ? "Explicitly scoped tasks across every active track." : "No tasks have been pulled into this sprint yet. Open a track and add the next task from its backlog.")
          : "Start the next sprint when you are ready. Its dates and task membership will remain fixed until you close it."}</p></div>
        <div className="sprint-summary-side"><div className="sprint-summary-metrics"><span><strong>{visibleSprintTasks.length}</strong> scoped tasks</span>
          <span><strong>{activeMinutes}</strong> min remaining</span><span><strong>{sprintProgress}%</strong> sprint complete</span></div>
          <div className="sprint-lifecycle-actions">{!sprint
            ? <button className="primary-button" disabled={sprintLifecycleBusy} onClick={onStartSprint}>{sprintLifecycleBusy ? "Starting…" : "Start new sprint"}</button>
            : sprint.canClose
              ? <button className="danger-button" disabled={sprintLifecycleBusy} onClick={() => onRequestCloseSprint(sprint)}>Close current sprint</button>
              : <small>Close available from {formatDate(sprint.endDate)}</small>}</div></div></div>
      {sprint && <SprintCalendar tasks={sprintTasks} sprint={sprint} />}
      <div className="sprint-task-board"><div className="sprint-board-heading"><div><p className="section-label">Delivery board</p><h3>Current sprint work</h3>
        <p>Your tasks across every active preparation track.</p></div><div className="sprint-board-progress"><span><strong>{sprintCompleted}</strong> of {visibleSprintTasks.length} done</span>
          <i role="progressbar" aria-label="Current preparation sprint completion" aria-valuemin={0} aria-valuemax={100} aria-valuenow={sprintProgress}><b style={{ width: `${sprintProgress}%` }} /></i></div></div>
        {visibleSprintTasks.length === 0 ? <p className="inline-empty">Open a preparation track and add a backlog task to the current sprint.</p>
          : <div className="sprint-board-columns">{sprintColumns.map((column) => {
            const columnTasks = visibleSprintTasks.filter(({ item }) => sprintBoardStage(item.status) === column.stage);
            return <section className={`sprint-board-column ${column.stage.toLowerCase().replaceAll("_", "-")}`} aria-labelledby={`sprint-${column.stage.toLowerCase()}`} key={column.stage}>
              <header><div><span aria-hidden="true" /><strong id={`sprint-${column.stage.toLowerCase()}`}>{column.label}</strong><b>{columnTasks.length}</b></div><small>{column.guidance}</small></header>
              <div>{columnTasks.length === 0 ? <p className="sprint-column-empty">No tasks here</p>
                : columnTasks.map((context) => { const { item, track } = context;
                  const trackNumber = trackNumberById.get(track.id) ?? orderedTracks.findIndex((candidate) => candidate.id === track.id) + 1;
                  return <article className="sprint-board-card" key={item.id}>
                  <header><span className={`sprint-track-tag ${trackTone(trackNumber)}`}><b>T{trackNumber}</b><span>{trackShortLabel(track)}</span></span>
                    <label className="sprint-priority-control"><span>Priority</span><select aria-label={`Priority for ${item.title}`} value={item.priority}
                    disabled={busyTaskId === item.id} onChange={(event) => onUpdatePriority(item, Number(event.target.value))}>
                    {[1,2,3,4,5].map((priority) => <option value={priority} key={priority}>P{priority}</option>)}</select></label></header>
                  <button type="button" className="sprint-card-title" onClick={() => onOpenTask(context)}>{item.title}</button>
                  <footer><label><span>Stage</span><select aria-label={`Sprint stage for ${item.title}`} value={sprintStageStatus(item.status)} disabled={busyTaskId === item.id}
                    onChange={(event) => onUpdateStatus(item, event.target.value as PrepItemStatus)}>
                    <option value="READY">Open</option><option value="IN_PROGRESS">In progress</option><option value="IN_REVIEW">In review</option><option value="COMPLETED">Done</option>
                  </select></label><div className="sprint-card-actions"><button type="button" className="text-button" onClick={() => onOpenTask(context, "SESSION")}>Log session</button>
                    <button type="button" className="text-button" onClick={() => onOpenTask(context)}>Details →</button></div></footer>
                </article>;})}</div>
            </section>;
          })}</div>}
      </div>
    </section>

    <section className="track-portfolio"><div className="section-heading"><div><p className="section-label">Portfolio overview</p><h3>Preparation tracks</h3></div>
      <span>{preparation.tracks.length} active track{preparation.tracks.length === 1 ? "" : "s"}</span></div>
      {orderedTracks.length === 0 ? <div className="prep-empty"><span>01</span><div><h3>Build your first preparation track</h3><p>Start with a project, interview practice lane, or market-driven skill plan.</p></div>
        <button className="secondary-button" onClick={onCreateTrack}>Create track</button></div>
        : <div className={`track-card-grid ${trackOrderBusy ? "saving-order" : ""}`}>{orderedTracks.map((track, index) => {
          const metrics = prepTrackMetrics(track);
          const trackNumber = index + 1;
          return <article className={`track-summary-card ${draggedTrackId === track.id ? "dragging" : ""} ${dragOverTrackId === track.id ? "drag-over" : ""}`} key={track.id}
            onDragOver={(event) => { event.preventDefault(); if (draggedTrackId && draggedTrackId !== track.id) setDragOverTrackId(track.id); }}
            onDragLeave={() => { if (dragOverTrackId === track.id) setDragOverTrackId(null); }} onDrop={(event) => { event.preventDefault(); dropTrack(track.id); }}>
            <button type="button" className="track-drag-handle" disabled={trackOrderBusy} draggable={!trackOrderBusy} aria-label={`Reorder Track ${trackNumber}. Use Up or Down arrow keys, or drag.`} aria-keyshortcuts="ArrowUp ArrowDown"
              onKeyDown={(event) => {
                if (event.key !== "ArrowUp" && event.key !== "ArrowDown") return;
                event.preventDefault();
                const index = orderedTracks.findIndex((candidate) => candidate.id === track.id);
                const destination = index + (event.key === "ArrowUp" ? -1 : 1);
                if (destination < 0 || destination >= orderedTracks.length || trackOrderBusy) return;
                const ids = orderedTracks.map((candidate) => candidate.id);
                [ids[index], ids[destination]] = [ids[destination], ids[index]];
                onReorderTracks(ids);
              }}
              title="Drag, or use Up/Down arrow keys to reorder" onDragStart={(event) => { setDraggedTrackId(track.id); event.dataTransfer.effectAllowed = "move"; event.dataTransfer.setData("text/plain", track.id); }}
              onDragEnd={() => { setDraggedTrackId(null); setDragOverTrackId(null); }}>⋮⋮</button>
            <div className="track-summary-identity"><header><span className={`track-number ${trackTone(trackNumber)}`}>Track {String(trackNumber).padStart(2, "0")}</span>
              <span className="track-type">{preparationTrackType(track)}</span>
              <span className="category-chip">{formatEnum(track.category)}</span></header><h3>{track.name}</h3></div>
            <div className="track-row-metric"><span>Tasks</span><strong>{metrics.total}</strong></div>
            <div className="track-row-progress"><div><span>Overall progress</span><strong>{metrics.progress}%</strong></div>
              <i><b style={{ width: `${metrics.progress}%` }} /></i></div>
            <button className="track-open-button" aria-label={`Open ${track.name}`} title="Open track" onClick={() => onOpenTrack(track.id)}>→</button>
          </article>;
        })}</div>}
    </section>
  </div>;
}

function PreparationTrackDashboard({ track, backlog, activeSprint, onAddTask, onEditStory, onDeleteStory, busyTaskId, onAddToSprint,
  onOpenTask, onResourceChanged, onResourceError }: {
  track: PrepTrack; backlog: PersonalSkillBacklogOverview; onAddTask: (milestone: PrepMilestone) => void;
  onEditStory: (milestone: PrepMilestone) => void; onDeleteStory: (milestone: PrepMilestone) => void;
  activeSprint: PrepSprint | null; busyTaskId: string | null; onAddToSprint: (item: PrepItem) => void;
  onOpenTask: (context: PrepTaskContext, initialTab?: "DETAILS" | "SESSION") => void;
  onResourceChanged: (message: string) => Promise<void>; onResourceError: (message: string) => void;
}) {
  const metrics = prepTrackMetrics(track);
  const tasks = flattenPreparationTasks([track]);
  const stories = [...track.milestones].sort((left, right) => milestonePriority(left) - milestonePriority(right)
    || (left.targetDate ?? "9999-12-31").localeCompare(right.targetDate ?? "9999-12-31"));
  return <div className="track-dashboard-layout"><div className="track-dashboard">
    <div className="track-dashboard-summary"><article><span>Stories</span><strong>{track.milestones.length}</strong><small>ordered by task priority</small></article>
      <article><span>Tasks</span><strong>{metrics.total}</strong><small>{metrics.inProgress} currently in progress</small></article>
      <article><span>Completion</span><strong>{metrics.progress}%</strong><small>{metrics.completed} tasks complete</small></article>
      <article><span>Estimate</span><strong>{metrics.estimatedWeeks}w</strong><small>{metrics.remainingMinutes} min at 5h/week</small></article></div>
    {activeSprint && <SprintCalendar tasks={sprintTaskContexts(tasks, activeSprint.tasks.map((task) => task.itemId))} sprint={activeSprint} />}
    <div className="story-list"><div className="section-heading"><div><p className="section-label">Track backlog</p><h3>Stories and tasks</h3></div><span>{stories.length} stories</span></div>
      {stories.length === 0 ? <p className="inline-empty">Add the first story to turn this track into scheduled work.</p>
        : stories.map((milestone) => <section className="milestone story-card" key={milestone.id}>
          <div className="milestone-heading"><div><span>Priority {milestonePriority(milestone)} · {milestone.targetDate ? `Target ${formatDate(milestone.targetDate)}` : "No target date"}</span><h4>{milestone.title}</h4>
            {milestone.description && <p>{milestone.description}</p>}</div><div className="story-actions">
              <button className="text-button" onClick={() => onEditStory(milestone)}>Edit</button>
              <button className="text-button reject" onClick={() => onDeleteStory(milestone)}>Delete</button>
              <button className="secondary-button" onClick={() => onAddTask(milestone)}>+ Task</button></div></div>
          <PrepItemCollection track={track} milestone={milestone} backlog={backlog} activeSprint={activeSprint} busyTaskId={busyTaskId}
            onAddToSprint={onAddToSprint} onOpenTask={onOpenTask} />
        </section>)}
    </div>
  </div><TrackResourcesPanel track={track} onChanged={onResourceChanged} onError={onResourceError} /></div>;
}

function TrackResourcesPanel({ track, onChanged, onError }: {
  track: PrepTrack; onChanged: (message: string) => Promise<void>; onError: (message: string) => void;
}) {
  const [adding, setAdding] = useState(false); const [saving, setSaving] = useState(false); const [deletingId, setDeletingId] = useState<string | null>(null);
  const [title, setTitle] = useState(""); const [url, setUrl] = useState(""); const [notes, setNotes] = useState("");
  async function addResource(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setSaving(true);
    try {
      await api(`/api/v1/preparation/tracks/${track.id}/resources`, { method: "POST", body: JSON.stringify({ title, url, notes }) });
      setTitle(""); setUrl(""); setNotes(""); setAdding(false); await onChanged("Track resource saved.");
    } catch (error) { onError(error instanceof Error ? error.message : "Could not save the resource."); }
    finally { setSaving(false); }
  }
  async function removeResource(resource: PrepTrackResource) {
    setDeletingId(resource.id);
    try { await api(`/api/v1/preparation/resources/${resource.id}`, { method: "DELETE" }); await onChanged(`“${resource.title}” was removed.`); }
    catch (error) { onError(error instanceof Error ? error.message : "Could not remove the resource."); }
    finally { setDeletingId(null); }
  }
  return <aside className="track-resources-panel"><header><div><p className="section-label">Handy reference</p><h3>Resources</h3></div>
    <button type="button" className="track-resource-add" onClick={() => setAdding((current) => !current)}>{adding ? "×" : "+"}<span>{adding ? "Close" : "Add"}</span></button></header>
    {adding && <form className="track-resource-form" onSubmit={(event) => void addResource(event)}>
      <input required value={title} onChange={(event) => setTitle(event.target.value)} placeholder="Resource title" maxLength={180} />
      <input required type="url" value={url} onChange={(event) => setUrl(event.target.value)} placeholder="https://…" maxLength={2000} />
      <textarea value={notes} onChange={(event) => setNotes(event.target.value)} placeholder="Optional note" rows={2} />
      <button className="primary-button" disabled={saving}>{saving ? "Saving…" : "Save resource"}</button></form>}
    <div className="track-resource-list">{track.resources.length === 0 ? <p className="inline-empty">Add documentation, courses, notebooks, or reference articles for this track.</p>
      : track.resources.map((resource) => <article key={resource.id}><a href={resource.url} target="_blank" rel="noreferrer" title={resource.url}>
        <span>↗</span><div><strong>{resource.title}</strong><small>{resource.notes ?? compactResourceHost(resource.url)}</small></div></a>
        <button type="button" aria-label={`Remove ${resource.title}`} title="Remove resource" disabled={deletingId === resource.id} onClick={() => void removeResource(resource)}>×</button></article>)}</div>
    {track.resources.length > 5 && <small className="resource-scroll-hint">Scroll to view all {track.resources.length} resources</small>}
  </aside>;
}

function SprintCalendar({ tasks, sprint }: { tasks: PrepTaskContext[]; sprint: PrepSprint }) {
  const days = sprintDays(sprint);
  return <section className="sprint-calendar" aria-label="Two-week task calendar"><header><div><p className="section-label">Calendar</p><h3>Two-week delivery window</h3></div>
    <span>Scheduled date + due date indicators</span></header><div>{days.map((day) => {
      const scheduled = tasks.filter(({ item }) => item.scheduledFor === day.iso).length;
      const due = tasks.filter(({ item }) => item.dueDate === day.iso).length;
      return <article className={`${day.today ? "today" : ""} ${scheduled || due ? "active" : ""}`} key={day.iso}><span>{day.weekday}</span><strong>{day.day}</strong>
        <small>{scheduled ? `${scheduled} start${scheduled === 1 ? "" : "s"}` : ""}{scheduled && due ? " · " : ""}{due ? `${due} due` : ""}</small></article>;
    })}</div></section>;
}

function SkillCatalogNetwork({ skills, backlog, signals, onAddToPlan, onOpenPlan }: {
  skills: SkillView[]; backlog: PersonalSkillBacklogOverview; signals: MarketSkillSignal[];
  onAddToPlan: (skill: SkillView) => void; onOpenPlan: (item: PersonalSkillBacklogItem) => void;
}) {
  const [selectedSkillId, setSelectedSkillId] = useState<string | null>(null);
  const [focusedCategory, setFocusedCategory] = useState<SkillCategory | null>(null);
  const backlogBySkillId = useMemo(() => new Map(backlog.items.map((item) => [item.skillId, item])), [backlog.items]);
  const signalBySkillId = useMemo(() => new Map(signals.map((signal) => [signal.skillId, signal])), [signals]);
  const categories = useMemo(() => {
    const groups = new Map<SkillCategory, SkillView[]>();
    skills.forEach((skill) => groups.set(skill.category, [...(groups.get(skill.category) ?? []), skill]));
    return Array.from(groups, ([category, categorySkills]) => ({
      category,
      skills: [...categorySkills].sort((left, right) => (signalBySkillId.get(right.id)?.currentOpeningCount ?? 0)
        - (signalBySkillId.get(left.id)?.currentOpeningCount ?? 0) || left.name.localeCompare(right.name)),
    })).sort((left, right) => right.skills.length - left.skills.length || left.category.localeCompare(right.category));
  }, [signalBySkillId, skills]);
  const effectiveSelectedSkillId = selectedSkillId && skills.some((skill) => skill.id === selectedSkillId) ? selectedSkillId
    : [...skills].sort((left, right) => (signalBySkillId.get(right.id)?.currentOpeningCount ?? 0)
      - (signalBySkillId.get(left.id)?.currentOpeningCount ?? 0))[0]?.id ?? null;
  const layout = useMemo(() => {
    const width = 1200;
    const height = focusedCategory ? 760 : 700;
    const visibleGroups = focusedCategory ? categories.filter((group) => group.category === focusedCategory) : categories;
    const centers = visibleGroups.map((group, index) => {
      if (focusedCategory) return { group, x: width / 2, y: height / 2 };
      if (index === 0) return { group, x: width / 2, y: height / 2 };
      const outerCount = Math.max(1, visibleGroups.length - 1);
      const angle = -Math.PI / 2 + ((index - 1) * Math.PI * 2) / outerCount;
      return { group, x: width / 2 + Math.cos(angle) * 415, y: height / 2 + Math.sin(angle) * 245 };
    });
    const nodes = centers.flatMap(({ group, x, y }, groupIndex) => {
      const count = group.skills.length;
      const capacities = focusedCategory ? [8, 10, 14, 18] : groupIndex === 0 ? [8, 10, 14] : [6, 10, 16];
      const radii = focusedCategory ? [105, 185, 265, 320] : groupIndex === 0 ? [75, 125, 178] : [58, 98, 125];
      let consumed = 0;
      return group.skills.map((skill, skillIndex) => {
        let ring = 0;
        while (ring < capacities.length - 1 && skillIndex >= consumed + capacities[ring]) {
          consumed += capacities[ring]; ring += 1;
        }
        const ringCount = Math.min(capacities[ring], count - consumed);
        const position = skillIndex - consumed;
        const angle = -Math.PI / 2 + (position * Math.PI * 2) / Math.max(1, ringCount) + (ring % 2 ? Math.PI / Math.max(1, ringCount) : 0);
        const demand = signalBySkillId.get(skill.id)?.currentOpeningCount ?? 0;
        return { skill, category: group.category, categoryX: x, categoryY: y,
          x: x + Math.cos(angle) * radii[ring], y: y + Math.sin(angle) * radii[ring],
          radius: 6 + Math.min(7, Math.sqrt(demand) * 2.1), demand, rank: skillIndex };
      });
    });
    return { width, height, centers, nodes };
  }, [categories, focusedCategory, signalBySkillId]);
  const selectedSkill = skills.find((skill) => skill.id === effectiveSelectedSkillId) ?? null;
  const selectedPlan = selectedSkill ? backlogBySkillId.get(selectedSkill.id) ?? null : null;
  const selectedSignal = selectedSkill ? signalBySkillId.get(selectedSkill.id) ?? null : null;
  const focusBranch = (category: SkillCategory | null) => {
    setFocusedCategory(category);
    if (category) setSelectedSkillId(categories.find((group) => group.category === category)?.skills[0]?.id ?? null);
  };
  return <section className="skill-network" aria-labelledby="skill-network-title">
    <header className="skill-network-heading"><div><p className="section-label">Graphical view</p><h4 id="skill-network-title">Category–skill network</h4>
      <p>Category hubs organize the catalog; skill node size reflects demand in the current reviewed-opening cohort.</p></div>
      <div className="skill-network-view-controls" aria-label="Skill network category focus">
        <button type="button" className={focusedCategory === null ? "active" : ""} aria-pressed={focusedCategory === null} onClick={() => focusBranch(null)}>All clusters</button>
        {categories.map((group) => <button type="button" className={`${skillCategoryClass(group.category)} ${focusedCategory === group.category ? "active" : ""}`}
          aria-pressed={focusedCategory === group.category} onClick={() => focusBranch(group.category)} key={group.category}>
          <i aria-hidden="true" />{formatEnum(group.category)} <span>{group.skills.length}</span></button>)}
      </div>
    </header>
    <div className="skill-network-layout">
      <div className="skill-network-canvas">
        <svg viewBox={`0 0 ${layout.width} ${layout.height}`} role="img" aria-labelledby="skill-network-svg-title skill-network-svg-description">
          <title id="skill-network-svg-title">Canonical skill catalog network</title>
          <desc id="skill-network-svg-description">{focusedCategory
            ? `${formatEnum(focusedCategory)} category with its connected skill nodes.`
            : `${categories.length} category hubs connected to ${skills.length} canonical skill nodes.`}</desc>
          <g className="skill-network-links" aria-hidden="true">{layout.nodes.map((node) => <line x1={node.categoryX} y1={node.categoryY} x2={node.x} y2={node.y} key={`link-${node.skill.id}`} />)}</g>
          {layout.centers.map(({ group, x, y }) => <g className={`skill-network-category-node ${skillCategoryClass(group.category)}`} role="button"
            aria-label={`${formatEnum(group.category)}, ${group.skills.length} skills. Focus this category.`} tabIndex={0}
            onClick={() => focusBranch(focusedCategory === group.category ? null : group.category)}
            onKeyDown={(event) => { if (event.key === "Enter" || event.key === " ") { event.preventDefault(); focusBranch(focusedCategory === group.category ? null : group.category); } }} key={group.category}>
            <circle cx={x} cy={y} r={30 + Math.min(10, group.skills.length / 3)} /><text className="skill-network-category-count" x={x} y={y + 5}>{group.skills.length}</text>
            <text className="skill-network-category-label" x={x} y={y + 52}>{formatEnum(group.category)}</text>
          </g>)}
          {layout.nodes.map((node) => {
            const selected = node.skill.id === effectiveSelectedSkillId;
            const showLabel = Boolean(focusedCategory) || selected || node.rank < 2;
            const labelOnRight = node.x >= node.categoryX;
            return <g className={`skill-network-skill-node ${skillCategoryClass(node.category)} ${selected ? "selected" : ""}`} role="button" tabIndex={0}
              aria-label={`${node.skill.name}, ${formatEnum(node.category)}${node.demand ? `, ${node.demand} current opening signals` : ""}`}
              onClick={() => setSelectedSkillId(node.skill.id)} onKeyDown={(event) => { if (event.key === "Enter" || event.key === " ") { event.preventDefault(); setSelectedSkillId(node.skill.id); } }} key={node.skill.id}>
              <title>{node.skill.name} · {formatEnum(node.category)} · {node.demand} current opening signals</title>
              <circle className="skill-network-hit" cx={node.x} cy={node.y} r={Math.max(18, node.radius + 8)} />
              <circle className="skill-network-dot" cx={node.x} cy={node.y} r={node.radius} />
              {showLabel && <text className="skill-network-skill-label" textAnchor={labelOnRight ? "start" : "end"}
                x={node.x + (labelOnRight ? node.radius + 6 : -node.radius - 6)} y={node.y + 3}>{shortSkillName(node.skill.name, focusedCategory ? 27 : 18)}</text>}
            </g>;
          })}
        </svg>
      </div>
      <article className="skill-network-detail" aria-live="polite">
        {selectedSkill ? <><header><div><span className={`category-chip ${skillCategoryClass(selectedSkill.category)}`}>{formatEnum(selectedSkill.category)}</span><h4>{selectedSkill.name}</h4></div>
          <button type="button" className="text-button" onClick={() => focusBranch(selectedSkill.category)}>Focus branch</button></header>
          <p>{selectedSkill.description ?? "No description yet."}</p>
          {selectedSkill.aliases.length > 0 && <div className="skill-network-aliases"><span>Also matches</span><p>{selectedSkill.aliases.join(", ")}</p></div>}
          <div className="skill-network-demand"><span><strong>{selectedSignal?.currentOpeningCount ?? 0}</strong> current openings</span>
            <span><strong>{selectedSignal?.requiredCount ?? 0}</strong> required</span><span><strong>{selectedSignal?.preferredCount ?? 0}</strong> preferred</span></div>
          <footer>{selectedPlan ? <><span className={`status-pill ${selectedPlan.status.toLowerCase()}`}>{formatEnum(selectedPlan.status)}</span>
            <button type="button" className="secondary-button" onClick={() => onOpenPlan(selectedPlan)}>Edit plan</button></>
            : <><span>Not yet in your personal backlog</span><button type="button" className="primary-button" onClick={() => onAddToPlan(selectedSkill)}>Add to plan</button></>}</footer></>
          : <p className="inline-empty">Choose a skill node to view its catalog description and plan status.</p>}
      </article>
    </div>
    <footer className="skill-network-legend"><span><i className="category-node" />Category hub</span><span><i className="skill-node" />Canonical skill</span>
      <span><i className="demand-node" />Larger skill node = more current demand</span><small>Select a hub to isolate a category branch; select any skill node for details.</small></footer>
  </section>;
}

function PersonalSkillBacklogPanel({ overview, busy, onSync, onOpen }: {
  overview: PersonalSkillBacklogOverview; busy: boolean; onSync: () => void; onOpen: (dialog: BacklogDialog) => void;
}) {
  const [category, setCategory] = useState<"" | SkillCategory>("");
  const [status, setStatus] = useState<"" | PersonalSkillStatus>("BACKLOG");
  const [selectedItemId, setSelectedItemId] = useState<string | null>(null);
  const [categoryPages, setCategoryPages] = useState<Partial<Record<SkillCategory, number>>>({});
  const filtered = useMemo(() => overview.items.filter((item) => (!category || item.category === category)
    && (!status || item.status === status)), [category, overview.items, status]);
  const grouped = useMemo(() => {
    const groups = new Map<SkillCategory, PersonalSkillBacklogItem[]>();
    filtered.forEach((item) => groups.set(item.category, [...(groups.get(item.category) ?? []), item]));
    return Array.from(groups, ([groupCategory, groupItems]) => ({
      category: groupCategory,
      popularity: groupItems.reduce((total, item) => total + item.currentOpeningCount, 0),
      items: [...groupItems].sort((left, right) => right.currentOpeningCount - left.currentOpeningCount
        || right.requiredCount - left.requiredCount
        || right.currentFrequencyPercent - left.currentFrequencyPercent
        || left.skillName.localeCompare(right.skillName)),
    })).sort((left, right) => right.popularity - left.popularity || left.category.localeCompare(right.category));
  }, [filtered]);
  const selectedItem = filtered.find((item) => item.id === selectedItemId) ?? null;
  const categories = Array.from(new Set(overview.items.map((item) => item.category))).sort();
  const updateCategory = (value: "" | SkillCategory) => { setCategory(value); setCategoryPages({}); setSelectedItemId(null); };
  const updateStatus = (value: "" | PersonalSkillStatus) => { setStatus(value); setCategoryPages({}); setSelectedItemId(null); };
  const updateCategoryPage = (groupCategory: SkillCategory, page: number) => {
    setCategoryPages((current) => ({ ...current, [groupCategory]: page }));
    setSelectedItemId(null);
  };

  return <section className="personal-backlog" aria-labelledby="personal-backlog-heading">
    <header className="personal-backlog-heading"><div><p className="section-label">Milestone 3D · persisted planning loop</p>
      <h3 id="personal-backlog-heading">Personal skill backlog</h3>
      <p>Your selected capabilities remain durable. Accepted job evidence informs demand and order; it never removes a learning goal.</p></div>
      <button className="secondary-button" disabled={busy} onClick={onSync}>{busy ? "Synchronizing…" : "Sync preparation backlog"}</button></header>
    <div className="personal-backlog-stats">
      <article><strong>{overview.backlogSize}</strong><span>canonical capabilities</span></article>
      <article><strong>{overview.activeCount}</strong><span>active plans</span></article>
      <article><strong>{overview.linkedPreparationItems}</strong><span>linked prep goals</span></article>
      <article><strong>{overview.reviewedOpeningSampleSize}</strong><span>reviewed openings</span></article>
    </div>
    <div className="personal-backlog-controls">
      <label><span>Category</span><select value={category} onChange={(event) => updateCategory(event.target.value as "" | SkillCategory)}>
        <option value="">All categories</option>{categories.map((item) => <option value={item} key={item}>{formatEnum(item)}</option>)}</select></label>
      <label><span>Plan status</span><select value={status} onChange={(event) => updateStatus(event.target.value as "" | PersonalSkillStatus)}>
        <option value="">All statuses</option><option value="BACKLOG">Backlog</option><option value="ACTIVE">Active</option><option value="PAUSED">Paused</option><option value="ACHIEVED">Achieved</option></select></label>
      <p>Demand window {formatDate(overview.demandFrom)}–{formatDate(overview.demandTo)} · accepted evidence only</p>
    </div>
    {filtered.length === 0 ? <EmptyState text={overview.backlogSize === 0
      ? "Synchronize the preparation backlog to create durable skill plans without losing any preparation items."
      : "No personal skill plans match these filters."} />
      : <div className={`personal-backlog-browser ${selectedItem ? "has-detail" : ""}`}>
        <div className="personal-backlog-groups">{grouped.map((group) => {
          const groupPageCount = Math.max(1, Math.ceil(group.items.length / 5));
          const groupPage = Math.min(categoryPages[group.category] ?? 1, groupPageCount);
          const groupItems = group.items.slice((groupPage - 1) * 5, groupPage * 5);
          return <section className="personal-backlog-group" key={group.category}>
          <header><div><span className="category-chip">{formatEnum(group.category)}</span><strong>{group.items.length} skill{group.items.length === 1 ? "" : "s"}</strong></div>
            <small>{group.popularity} opening signal{group.popularity === 1 ? "" : "s"}</small></header>
          <div>{groupItems.map((item, index) => <button type="button" className={`personal-backlog-row ${selectedItem?.id === item.id ? "selected" : ""}`}
            aria-expanded={selectedItem?.id === item.id} onClick={() => setSelectedItemId(item.id)} key={item.id}>
            <span className="backlog-rank">{String((groupPage - 1) * 5 + index + 1).padStart(2, "0")}</span>
            <span className="personal-backlog-row-title"><strong>{item.skillName}</strong><small>{formatEnum(item.currentLevel)} → {formatEnum(item.targetLevel)}</small></span>
            <span className="personal-backlog-row-demand"><strong>{item.currentOpeningCount}</strong><small>{item.currentFrequencyPercent.toFixed(1)}%</small></span>
            <span className={`status-pill ${item.status.toLowerCase()}`}>{formatEnum(item.status)}</span><b>→</b>
          </button>)}</div>
          {groupPageCount > 1 && <nav className="category-pagination" aria-label={`${formatEnum(group.category)} skill pages`}>
            <button disabled={groupPage === 1} onClick={() => updateCategoryPage(group.category, groupPage - 1)}>←</button>
            <span>Page {groupPage} of {groupPageCount} · showing {groupItems.length} of {group.items.length}</span>
            <button disabled={groupPage === groupPageCount} onClick={() => updateCategoryPage(group.category, groupPage + 1)}>→</button>
          </nav>}
        </section>;
        })}</div>
        {selectedItem && <article className="personal-skill-card personal-skill-detail">
          <header><span className="backlog-rank">↗</span><div><span className="category-chip">{formatEnum(selectedItem.category)}</span><h4>{selectedItem.skillName}</h4></div>
            <button className="close-button" onClick={() => setSelectedItemId(null)} aria-label="Close skill details">×</button></header>
          <div className="proficiency-path"><span><small>Current</small><strong>{formatEnum(selectedItem.currentLevel)}</strong></span><b>→</b>
            <span><small>Target</small><strong>{formatEnum(selectedItem.targetLevel)}</strong></span></div>
          <div className="backlog-demand"><strong>{selectedItem.currentOpeningCount} of {overview.reviewedOpeningSampleSize}</strong>
            <span>{selectedItem.currentFrequencyPercent.toFixed(1)}% accepted demand</span>
            <i><b style={{ width: `${Math.min(100, selectedItem.currentFrequencyPercent)}%` }} /></i>
            <small>{selectedItem.requiredCount} required · {selectedItem.preferredCount} preferred · {selectedItem.mentionedCount} mentioned</small></div>
          <p>{selectedItem.rationale ?? "Add a rationale connecting this skill to your target roles and preparation plan."}</p>
          <div className="backlog-links"><span>{selectedItem.preparationLinks.length} prep</span><span>{selectedItem.resources.length} resources</span><span>{selectedItem.projectEvidence.length} project evidence</span>
            <span>Priority {selectedItem.priority}</span>{selectedItem.nextReviewOn && <span>Review {formatDate(selectedItem.nextReviewOn)}</span>}</div>
          {selectedItem.preparationLinks.length > 0 && <small className="linked-prep-title">Linked: {selectedItem.preparationLinks.map((link) => link.prepItemTitle).join(", ")}</small>}
          <footer><button className="text-button" onClick={() => onOpen({ kind: "profile", item: selectedItem })}>Edit plan</button>
            <button className="text-button" onClick={() => onOpen({ kind: "preparation", item: selectedItem })}>Link prep</button>
            <button className="text-button" onClick={() => onOpen({ kind: "resource", item: selectedItem })}>+ Resource</button>
            <button className="text-button" onClick={() => onOpen({ kind: "project", item: selectedItem })}>+ Evidence</button></footer>
        </article>}
      </div>}
  </section>;
}

function PersonalBacklogDialog({ dialog, preparation, onClose, onSaved, onError }: {
  dialog: BacklogDialog; preparation: PreparationOverview; onClose: () => void;
  onSaved: (message: string) => Promise<void>; onError: (message: string) => void;
}) {
  const item = dialog.item;
  const [saving, setSaving] = useState(false);
  const [currentLevel, setCurrentLevel] = useState<SkillProficiencyLevel>(item.currentLevel);
  const [targetLevel, setTargetLevel] = useState<SkillProficiencyLevel>(item.targetLevel);
  const [priority, setPriority] = useState(String(item.priority));
  const [status, setStatus] = useState<PersonalSkillStatus>(item.status);
  const [rationale, setRationale] = useState(item.rationale ?? "");
  const [nextReviewOn, setNextReviewOn] = useState(item.nextReviewOn ?? "");
  const [title, setTitle] = useState(""); const [url, setUrl] = useState(""); const [notes, setNotes] = useState("");
  const [resourceType, setResourceType] = useState("DOCUMENTATION");
  const [resourceStatus, setResourceStatus] = useState("PLANNED");
  const [evidenceType, setEvidenceType] = useState("PROJECT"); const [outcome, setOutcome] = useState("");
  const [completedOn, setCompletedOn] = useState(""); const [prepItemId, setPrepItemId] = useState("");
  const prepItems = preparation.tracks.flatMap((track) => track.milestones.flatMap((milestone) => milestone.items))
    .filter((candidate) => !item.preparationLinks.some((link) => link.prepItemId === candidate.id));
  const levels: SkillProficiencyLevel[] = ["NOT_ASSESSED", "AWARENESS", "PRACTICING", "WORKING_PROFICIENCY", "PROFICIENT", "ADVANCED"];
  const titles = { profile: "Edit personal skill plan", resource: "Add learning resource", project: "Add project evidence", preparation: "Link preparation item" };

  async function submit(event: FormEvent) {
    event.preventDefault(); setSaving(true);
    try {
      if (dialog.kind === "profile") await api(`/api/v1/skills/backlog/skills/${item.skillId}`, { method: "PUT", body: JSON.stringify({
        currentLevel, targetLevel, priority: Number(priority), rationale, status, nextReviewOn: nextReviewOn || null,
      }) });
      if (dialog.kind === "resource") await api(`/api/v1/skills/backlog/${item.id}/resources`, { method: "POST", body: JSON.stringify({
        title, url: url || null, resourceType, status: resourceStatus, notes: notes || null,
      }) });
      if (dialog.kind === "project") await api(`/api/v1/skills/backlog/${item.id}/project-evidence`, { method: "POST", body: JSON.stringify({
        title, url: url || null, evidenceType, description: notes || null, outcome: outcome || null, completedOn: completedOn || null,
      }) });
      if (dialog.kind === "preparation") await api(`/api/v1/skills/backlog/${item.id}/preparation-links`, { method: "POST", body: JSON.stringify({
        prepItemId, note: notes || null,
      }) });
      await onSaved(dialog.kind === "profile" ? "Personal skill plan updated."
        : dialog.kind === "resource" ? "Learning resource linked."
          : dialog.kind === "project" ? "Project evidence linked." : "Preparation item linked.");
    } catch (error) { onError(error instanceof Error ? error.message : "Could not save the personal skill plan."); }
    finally { setSaving(false); }
  }

  return <Modal title={titles[dialog.kind]} subtitle={`${item.skillName} · durable preparation evidence`} onClose={onClose}>
    <form className="modal-form" onSubmit={submit}>
      {dialog.kind === "profile" && <><div className="form-grid"><Field label="Current level"><select value={currentLevel} onChange={(event) => setCurrentLevel(event.target.value as SkillProficiencyLevel)}>
        {levels.map((level) => <option value={level} key={level}>{formatEnum(level)}</option>)}</select></Field>
        <Field label="Target level"><select value={targetLevel} onChange={(event) => setTargetLevel(event.target.value as SkillProficiencyLevel)}>
          {levels.filter((level) => level !== "NOT_ASSESSED").map((level) => <option value={level} key={level}>{formatEnum(level)}</option>)}</select></Field>
        <Field label="Priority (1 highest)"><input type="number" min="1" max="5" value={priority} onChange={(event) => setPriority(event.target.value)} /></Field>
        <Field label="Plan status"><select value={status} onChange={(event) => setStatus(event.target.value as PersonalSkillStatus)}>
          <option value="BACKLOG">Backlog</option><option value="ACTIVE">Active</option><option value="PAUSED">Paused</option><option value="ACHIEVED">Achieved</option></select></Field>
        <Field label="Next review"><input type="date" value={nextReviewOn} onChange={(event) => setNextReviewOn(event.target.value)} /></Field></div>
        <Field label="Rationale"><textarea value={rationale} onChange={(event) => setRationale(event.target.value)} placeholder="Why this skill matters for target roles and what evidence should change its priority." /></Field></>}
      {dialog.kind === "resource" && <><Field label="Resource title"><input required value={title} onChange={(event) => setTitle(event.target.value)} /></Field>
        <Field label="URL"><input type="url" value={url} onChange={(event) => setUrl(event.target.value)} placeholder="https://…" /></Field>
        <div className="form-grid"><Field label="Resource type"><select value={resourceType} onChange={(event) => setResourceType(event.target.value)}>
          {(["COURSE", "BOOK", "DOCUMENTATION", "NOTEBOOK", "ARTICLE", "VIDEO", "OTHER"] as const).map((value) => <option value={value} key={value}>{formatEnum(value)}</option>)}</select></Field>
          <Field label="Status"><select value={resourceStatus} onChange={(event) => setResourceStatus(event.target.value)}>
            <option value="PLANNED">Planned</option><option value="IN_PROGRESS">In progress</option><option value="COMPLETED">Completed</option></select></Field></div>
        <Field label="Notes"><textarea value={notes} onChange={(event) => setNotes(event.target.value)} /></Field></>}
      {dialog.kind === "project" && <><Field label="Evidence title"><input required value={title} onChange={(event) => setTitle(event.target.value)} /></Field>
        <Field label="URL"><input type="url" value={url} onChange={(event) => setUrl(event.target.value)} placeholder="Repository, demo, or document URL" /></Field>
        <div className="form-grid"><Field label="Evidence type"><select value={evidenceType} onChange={(event) => setEvidenceType(event.target.value)}>
          {(["PROJECT", "REPOSITORY", "DEMO", "DESIGN", "DEPLOYMENT", "WRITE_UP", "OTHER"] as const).map((value) => <option value={value} key={value}>{formatEnum(value)}</option>)}</select></Field>
          <Field label="Completed on"><input type="date" value={completedOn} onChange={(event) => setCompletedOn(event.target.value)} /></Field></div>
        <Field label="Description"><textarea value={notes} onChange={(event) => setNotes(event.target.value)} /></Field>
        <Field label="Outcome"><textarea value={outcome} onChange={(event) => setOutcome(event.target.value)} placeholder="What this proves and how it was verified." /></Field></>}
      {dialog.kind === "preparation" && <>{prepItems.length === 0 ? <p className="inline-empty">Every preparation item is already linked to this skill.</p>
        : <><Field label="Preparation item"><select required value={prepItemId} onChange={(event) => setPrepItemId(event.target.value)}>
          <option value="">Choose an item</option>{prepItems.map((candidate) => <option value={candidate.id} key={candidate.id}>{candidate.title}</option>)}</select></Field>
          <Field label="Link note"><textarea value={notes} onChange={(event) => setNotes(event.target.value)} placeholder="How this preparation item develops or proves the skill." /></Field></>}</>}
      <ModalActions onClose={onClose} saving={saving} disabled={dialog.kind === "preparation" ? !prepItemId : dialog.kind === "profile" ? !priority : !title}
        label={dialog.kind === "profile" ? "Save plan" : dialog.kind === "resource" ? "Add resource" : dialog.kind === "project" ? "Add evidence" : "Link preparation"} />
    </form>
  </Modal>;
}

function PrepItemCollection({ track, milestone, backlog, activeSprint, busyTaskId, onAddToSprint, onOpenTask }: {
  track: PrepTrack; milestone: PrepMilestone; backlog: PersonalSkillBacklogOverview;
  activeSprint: PrepSprint | null; busyTaskId: string | null; onAddToSprint: (item: PrepItem) => void;
  onOpenTask: (context: PrepTaskContext, initialTab?: "DETAILS" | "SESSION") => void;
}) {
  const [page, setPage] = useState(1);
  const marketRanked = track.name.toLowerCase().includes("agentic systems") && milestone.title.toLowerCase().includes("skill backlog");
  const rankedItems = useMemo(() => milestone.items.map((item, originalIndex) => ({
    item, originalIndex, demand: marketRanked ? persistedDemandFor(item, backlog) : { mentions: 0, frequency: 0, linkedSkills: 0 },
  })).sort((left, right) => marketRanked
    ? right.demand.mentions - left.demand.mentions
      || left.item.title.localeCompare(right.item.title)
    : left.originalIndex - right.originalIndex), [backlog, marketRanked, milestone.items]);
  const pageCount = Math.max(1, Math.ceil(rankedItems.length / PREP_ITEMS_PER_PAGE));
  const effectivePage = Math.min(page, pageCount);
  const pageItems = rankedItems.slice((effectivePage - 1) * PREP_ITEMS_PER_PAGE, effectivePage * PREP_ITEMS_PER_PAGE);
  const rangeStart = rankedItems.length === 0 ? 0 : (effectivePage - 1) * PREP_ITEMS_PER_PAGE + 1;
  const rangeEnd = Math.min(effectivePage * PREP_ITEMS_PER_PAGE, rankedItems.length);

  return <>
    {marketRanked && <div className="market-demand-note">
      <div><strong>Trusted market-demand order</strong><span>Ranked by accepted evidence across {backlog.reviewedOpeningSampleSize} reviewed openings; zero-demand items remain retained.</span></div>
      <span className="market-signal-chip">Persisted 3D signal</span>
    </div>}
    <div className="prep-item-list">{rankedItems.length === 0 ? <p className="inline-empty">No actionable items yet.</p> : pageItems.map(({ item, demand }, pageIndex) =>
      <article className={`prep-item ${item.status === "COMPLETED" ? "complete" : ""}`} key={item.id}>
        <span className={`priority-mark p${item.priority}`} aria-label={marketRanked ? `Market rank ${(effectivePage - 1) * PREP_ITEMS_PER_PAGE + pageIndex + 1}` : `Priority ${item.priority}`}>
          {marketRanked ? (effectivePage - 1) * PREP_ITEMS_PER_PAGE + pageIndex + 1 : item.priority}
        </span>
        <div className="prep-item-copy"><div><button type="button" className="prep-item-title" onClick={() => onOpenTask({ item, track, milestone })}>{item.title}</button><span className={`status-pill ${item.status.toLowerCase()}`}>{formatEnum(item.status)}</span>
          {marketRanked && <span className={`demand-pill ${demand.mentions === 0 ? "quiet" : ""}`}>{demand.mentions} reviewed opening{demand.mentions === 1 ? "" : "s"}</span>}</div>
          <p>{item.description ?? item.skillFocus ?? "No notes yet."}</p><small>{item.estimatedMinutes} min{item.nextReviewOn ? ` · review ${formatDate(item.nextReviewOn)}` : ""}{item.opportunityLabel ? ` · ${item.opportunityLabel}` : ""}</small></div>
        <div className="prep-item-actions"><button type="button" className="text-button" onClick={() => onOpenTask({ item, track, milestone })}>Details</button>
          {taskIsInCurrentSprint(item, activeSprint?.tasks.map((task) => task.itemId) ?? [])
            ? <><span className="sprint-membership">{item.status === "COMPLETED" ? "Done · reopen from board" : "In current sprint"}</span>
              {item.status !== "COMPLETED" && <button type="button" className="secondary-button" onClick={() => onOpenTask({ item, track, milestone }, "SESSION")}>Log session</button>}</>
            : <button className="secondary-button" disabled={!activeSprint || busyTaskId === item.id} onClick={() => onAddToSprint(item)}>
              {!activeSprint ? "Start a sprint first" : item.status === "COMPLETED" ? "Reopen in current sprint" : "Add to current sprint"}
            </button>}</div>
      </article>)}</div>
    {rankedItems.length > PREP_ITEMS_PER_PAGE && <nav className="opening-pagination prep-pagination" aria-label={`${milestone.title} preparation item pages`}>
      <button className="secondary-button" disabled={effectivePage === 1} onClick={() => setPage(effectivePage - 1)}>← Previous</button>
      <div className="page-numbers">{Array.from({ length: pageCount }, (_, index) => index + 1).map((pageNumber) =>
        <button className={pageNumber === effectivePage ? "active" : ""} aria-current={pageNumber === effectivePage ? "page" : undefined}
          onClick={() => setPage(pageNumber)} key={pageNumber}>{pageNumber}</button>)}</div>
      <span>Showing {rangeStart}–{rangeEnd} of {rankedItems.length} · {PREP_ITEMS_PER_PAGE} prep items per page</span>
      <button className="secondary-button" disabled={effectivePage === pageCount} onClick={() => setPage(effectivePage + 1)}>Next →</button>
    </nav>}
  </>;
}

function persistedDemandFor(item: PrepItem, backlog: PersonalSkillBacklogOverview) {
  const linked = backlog.items.filter((profile) => profile.preparationLinks.some((link) => link.prepItemId === item.id));
  return { mentions: linked.reduce((highest, profile) => Math.max(highest, profile.currentOpeningCount), 0),
    frequency: linked.reduce((highest, profile) => Math.max(highest, profile.currentFrequencyPercent), 0), linkedSkills: linked.length };
}

function workspaceFromHash(hash: string): WorkspaceView {
  const value = hash.replace(/^#/, "").split("?")[0];
  if (["opportunities", "openings", "inbox"].includes(value)) return "opportunities";
  if (["outreach", "referrals"].includes(value)) return "outreach";
  if (value === "skills") return "skills";
  if (value === "preparation") return "preparation";
  if (value === "reviews") return "reviews";
  return "overview";
}

function flattenPreparationTasks(tracks: PrepTrack[]): PrepTaskContext[] {
  return tracks.flatMap((track) => track.milestones.flatMap((milestone) => milestone.items.map((item) => ({ item, track, milestone }))));
}

function sprintDays(sprint: PrepSprint) {
  const start = new Date(`${sprint.startDate}T08:00:00`);
  const today = new Date().toISOString().slice(0, 10);
  return Array.from({ length: 14 }, (_, index) => {
    const date = new Date(start); date.setDate(start.getDate() + index);
    const iso = localIsoDate(date);
    return { iso, today: iso === today, weekday: date.toLocaleDateString("en-IN", { weekday: "short" }), day: date.getDate() };
  });
}

function formatSprintRange(sprint: PrepSprint) { return `${formatDate(sprint.startDate)}–${formatDate(sprint.endDate)}`; }

function sprintTaskContexts(tasks: PrepTaskContext[], sprintItemIds: string[]) {
  return tasks.filter(({ item }) => taskIsInCurrentSprint(item, sprintItemIds))
    .sort((left, right) => left.item.priority - right.item.priority
      || (left.item.dueDate ?? left.item.scheduledFor ?? "9999-12-31").localeCompare(right.item.dueDate ?? right.item.scheduledFor ?? "9999-12-31"));
}

function taskIsInCurrentSprint(item: PrepItem, sprintItemIds: string[]) { return sprintItemIds.includes(item.id); }

function sprintBoardStage(status: PrepItemStatus): SprintBoardStage {
  if (status === "IN_PROGRESS") return "IN_PROGRESS";
  if (status === "IN_REVIEW") return "IN_REVIEW";
  if (status === "COMPLETED") return "DONE";
  return "OPEN";
}
function sprintStageStatus(status: PrepItemStatus): "READY" | "IN_PROGRESS" | "IN_REVIEW" | "COMPLETED" {
  if (status === "IN_PROGRESS" || status === "IN_REVIEW" || status === "COMPLETED") return status;
  return "READY";
}
function sprintStageLabel(status: PrepItemStatus) { return formatEnum(sprintBoardStage(status)); }

function localIsoDate(value: Date) {
  const year = value.getFullYear(); const month = String(value.getMonth() + 1).padStart(2, "0"); const day = String(value.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}
function localDateTimeInputValue(value: Date) {
  const pad = (number: number) => String(number).padStart(2, "0");
  return `${value.getFullYear()}-${pad(value.getMonth() + 1)}-${pad(value.getDate())}T${pad(value.getHours())}:${pad(value.getMinutes())}`;
}
function milestonePriority(milestone: PrepMilestone) {
  const active = milestone.items.filter((item) => !["COMPLETED", "SKIPPED"].includes(item.status));
  const candidates = active.length > 0 ? active : milestone.items;
  return candidates.length > 0 ? Math.min(...candidates.map((item) => item.priority)) : 3;
}
function prepTrackMetrics(track: PrepTrack) {
  const items = track.milestones.flatMap((milestone) => milestone.items);
  const completed = items.filter((item) => item.status === "COMPLETED").length;
  const inProgress = items.filter((item) => ["IN_PROGRESS", "IN_REVIEW"].includes(item.status)).length;
  const remainingMinutes = items.filter((item) => !["COMPLETED", "SKIPPED"].includes(item.status)).reduce((sum, item) => sum + item.estimatedMinutes, 0);
  return { total: items.length, completed, inProgress, remainingMinutes,
    progress: items.length > 0 ? Math.round((completed / items.length) * 100) : 0,
    estimatedWeeks: remainingMinutes > 0 ? Math.max(1, Math.ceil(remainingMinutes / 300)) : 0 };
}
function preparationTrackType(track: PrepTrack) {
  const text = `${track.name} ${track.description ?? ""} ${track.category}`.toLowerCase();
  if (/(project|releaseguard|build|deployment|portfolio)/.test(text)) return "Project";
  if (/(skill|backlog|expertise|ai data)/.test(text)) return "Skill plan";
  return "Practice track";
}
function trackTone(trackNumber: number) { return `track-tone-${((Math.max(trackNumber, 1) - 1) % 7) + 1}`; }
function trackShortLabel(track: PrepTrack) {
  const name = track.name.toLowerCase();
  if (name.includes("practice coding")) return "Coding practice";
  if (name.includes("systems design")) return "System design";
  if (name.includes("governed agent")) return "Governed agent project";
  if (name.includes("multi-threaded")) return "Concurrency design";
  if (name.includes("ocp java")) return "Java certification";
  if (name.includes("agentic systems")) return "Agentic systems";
  if (name.includes("big data")) return "Big data";
  return track.name.split(/\s+/).slice(0, 3).join(" ");
}
function compactResourceHost(value: string) { try { return new URL(value).hostname.replace(/^www\./, ""); } catch { return value; } }

function openingToOpportunity(opening: Opening): Opportunity {
  return { id: opening.opportunityId, companyName: opening.companyName, roleTitle: opening.roleTitle,
    location: opening.location, workMode: opening.workMode, status: opening.status,
    fitScore: Math.round(opening.weightedTotal * 10), fitSummary: opening.fitRationale,
    discoveredAt: `${opening.observedOn}T00:00:00+05:30`, applicationId: opening.applicationId };
}
function linkedinPeopleSearch(opening: Opening, degree: "FIRST" | "SECOND", includeRoleKeywords = false) {
  const company = quoteLinkedInTerm(opening.companyName);
  const keywords = includeRoleKeywords ? `${company} AND ${quoteLinkedInTerm(linkedinRoleKeywords(opening))}` : company;
  const params = new URLSearchParams({ keywords, network: degree === "FIRST" ? '["F"]' : '["S"]', origin: "GLOBAL_SEARCH_HEADER" });
  return `https://www.linkedin.com/search/results/people/?${params.toString()}`;
}
function linkedinReferralPathSearch(opening: Opening, path: LinkedInPathSearch, includeRoleKeywords = false) {
  const company = quoteLinkedInTerm(opening.companyName);
  const pathKeywords = path === "RECRUITER"
    ? `${company} AND (Recruiter OR "Talent Acquisition" OR "Talent Partner")`
    : path === "ENGINEERING_MANAGER"
      ? `${company} AND ("Engineering Manager" OR "Software Engineering Manager" OR "Director of Engineering" OR "Engineering Director")`
      : path === "ENGINEER"
        ? `${company} AND ("Software Engineer" OR "Senior Software Engineer" OR "Staff Engineer" OR "Platform Engineer")`
    : path === "FORMER_COLLEAGUE"
      ? `${company} AND ("Previous Employer")`
      : `${company} AND ("Your School")`;
  const keywords = includeRoleKeywords ? `${pathKeywords} AND ${quoteLinkedInTerm(linkedinRoleKeywords(opening))}` : pathKeywords;
  const params = new URLSearchParams({ keywords, origin: "GLOBAL_SEARCH_HEADER" });
  if (path === "ALUMNI" || path === "FORMER_COLLEAGUE") params.set("network", '["F","S"]');
  return `https://www.linkedin.com/search/results/people/?${params.toString()}`;
}
function linkedinRoleKeywords(opening: Opening) {
  const roleSegments = opening.roleTitle.split(/\s[-–—]\s/).map((segment) => segment.trim()).filter(Boolean);
  if (roleSegments.length > 1) return roleSegments[roleSegments.length - 1];
  return opening.roleTitle;
}
function quoteLinkedInTerm(value: string) {
  return `"${value.replaceAll('"', "").trim()}"`;
}
function recommendationClass(value: string) {
  const normalized = value.toLowerCase();
  if (normalized === "applied") return "already-applied";
  if (normalized.includes("apply now")) return "apply-now";
  if (normalized.includes("referral")) return "referral-first";
  if (normalized.includes("skip")) return "skip-now";
  return "tailor";
}
function openingRecommendation(opening: Opening) {
  return opening.applicationStage && opening.applicationStage !== "DRAFT" ? "Applied" : opening.recommendation;
}
function outreachMessageTemplate(scenario: Exclude<MessageTemplateScenario, "">, opening: Opening) {
  const company = opening.companyName;
  const role = opening.roleTitle;
  if (scenario === "CONNECTION_SECOND_DEGREE") {
    return `Hi [Name], I’m exploring the ${role} opportunity at ${company}. My distributed-systems and cloud-platform background aligns well, and I’d value connecting and learning about your experience there. Thanks!`;
  }
  if (scenario === "CONNECTION_ALUMNI") {
    return `Hi [Name], fellow [School] alum here. I’m exploring the ${role} opportunity at ${company}, aligned with my distributed-systems and cloud background. I’d value connecting and hearing about your experience there. Thanks!`;
  }
  if (scenario === "CONNECTION_FORMER_COLLEAGUE") {
    return `Hi [Name], we share a background at [Former company]. I’m exploring the ${role} role at ${company}, which aligns with my backend and distributed-systems experience. I’d value reconnecting and hearing your perspective. Thanks!`;
  }
  if (scenario === "CONNECTION_RECRUITER") {
    return `Hi [Name], I saw the ${role} opening at ${company}. My background in distributed systems and cloud platforms appears relevant, and I’d value connecting to learn more. Thanks!`;
  }
  if (scenario === "INMAIL_RECRUITER") {
    return `Subject: ${role} at ${company}\n\nHi [Name],\n\nI’m reaching out regarding the ${role} opportunity at ${company}. My background includes Java-based distributed systems, cloud platforms, and reliable production services.\n\n[Add 1–2 role-specific achievements that directly match the team’s requirements.]\n\nThe opportunity looks closely aligned with my background, and I would appreciate your consideration or guidance on the most relevant next step. I’ve attached my resume for review.\n\nBest,\n[Your name]`;
  }
  return `Hi [Name],\n\nI hope you’re doing well. I’m interested in the ${role} opportunity at ${company}, which aligns strongly with my experience in distributed systems, backend engineering, and cloud platforms.\n\n[Add a brief personal connection and 1–2 role-specific achievements.]\n\nWould you be open to reviewing the role and my resume? If you feel my background is a good match, I’d be grateful for a referral or guidance on the right person to contact. No worries at all if the role is outside your area.\n\nThanks,\n[Your name]`;
}
function formatDate(value: string) { return new Date(`${value}T08:00:00`).toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" }); }
function formatScheduleDate(value: string) {
  const date = new Date(`${value}T08:00:00`); const day = date.getDate();
  const suffix = day >= 11 && day <= 13 ? "th" : day % 10 === 1 ? "st" : day % 10 === 2 ? "nd" : day % 10 === 3 ? "rd" : "th";
  return `${day}${suffix} ${date.toLocaleDateString("en-IN", { month: "long" })}, ${date.toLocaleDateString("en-IN", { weekday: "long" })}`;
}
function formatDue(value: string) {
  const date = new Date(value); const today = new Date(); const sameDay = date.toDateString() === today.toDateString();
  return `${sameDay ? "Today" : date.toLocaleDateString("en-IN", { day: "numeric", month: "short" })}, ${date.toLocaleTimeString("en-IN", { hour: "numeric", minute: "2-digit" })}`;
}
function formatDateTime(value: string) {
  return new Date(value).toLocaleString("en-IN", { day: "numeric", month: "short", hour: "numeric", minute: "2-digit" });
}
function formatFileSize(value: number) {
  if (value < 1024) return `${value} B`;
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`;
  return `${(value / (1024 * 1024)).toFixed(1)} MB`;
}
function formatEnum(value: string) {
  return value.toLowerCase().split("_").map((part) => part[0].toUpperCase() + part.slice(1)).join(" ");
}
function skillCategoryClass(category: SkillCategory) { return `category-${category.toLowerCase().replaceAll("_", "-")}`; }
function shortSkillName(name: string, maxLength: number) {
  if (name.length <= maxLength) return name;
  return `${name.slice(0, Math.max(1, maxLength - 1)).trimEnd()}…`;
}
function compareOutreach(left: Outreach, right: Outreach, sort: OutreachSort) {
  if (sort === "COMPANY") return left.companyName.localeCompare(right.companyName) || left.contactName.localeCompare(right.contactName);
  if (sort === "RECENT") return Date.parse(right.updatedAt) - Date.parse(left.updatedAt);
  const leftFollowUp = left.followUpAt ? Date.parse(left.followUpAt) : Number.POSITIVE_INFINITY;
  const rightFollowUp = right.followUpAt ? Date.parse(right.followUpAt) : Number.POSITIVE_INFINITY;
  if (sort === "FOLLOW_UP") return leftFollowUp - rightFollowUp || Date.parse(right.updatedAt) - Date.parse(left.updatedAt);
  const urgencyRank = (item: Outreach) => item.overdue ? 0 : item.status === "PLANNED" ? 1 : item.followUpAt ? 2 : 3;
  return urgencyRank(left) - urgencyRank(right)
    || leftFollowUp - rightFollowUp
    || Date.parse(right.updatedAt) - Date.parse(left.updatedAt);
}
function nextOutreachStatus(status: OutreachStatus): OutreachStatus | null {
  if (status === "PLANNED") return "SENT";
  if (status === "SENT") return "RESPONDED";
  if (status === "RESPONDED") return "REFERRED";
  if (status === "REFERRED") return "CLOSED";
  return null;
}
function outreachActionLabel(status: OutreachStatus) {
  if (status === "PLANNED") return "Mark sent";
  if (status === "SENT") return "Mark responded";
  if (status === "RESPONDED") return "Mark referred";
  if (status === "REFERRED") return "Close loop";
  return "Update";
}
