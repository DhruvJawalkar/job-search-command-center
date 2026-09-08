create table weekly_review (
    id uuid primary key,
    week_start date not null,
    week_end date not null,
    effective_through date not null,
    complete_week boolean not null,
    generated_at timestamp with time zone not null,
    created_at timestamp with time zone not null,
    constraint weekly_review_week_uidx unique (week_start),
    constraint weekly_review_period_check check (week_end = week_start + 6),
    constraint weekly_review_effective_check check (effective_through between week_start and week_end)
);

create table weekly_metric_snapshot (
    id uuid primary key,
    review_id uuid not null references weekly_review(id) on delete cascade,
    openings_discovered integer not null,
    applications_submitted integer not null,
    high_fit_applications integer not null,
    application_progressions integer not null,
    outreach_sent integer not null,
    outreach_responses integer not null,
    referrals_secured integer not null,
    preparation_sessions integer not null,
    preparation_minutes integer not null,
    commitments_planned integer not null,
    commitments_completed integer not null,
    daily_actions_planned integer not null,
    daily_actions_completed integer not null,
    active_pipeline integer not null,
    overdue_application_actions integer not null,
    overdue_outreach_follow_ups integer not null,
    stage_applied integer not null,
    stage_recruiter_screen integer not null,
    stage_interviewing integer not null,
    stage_offer integer not null,
    captured_at timestamp with time zone not null,
    constraint weekly_metric_snapshot_review_uidx unique (review_id),
    constraint weekly_metric_snapshot_nonnegative_check check (
        openings_discovered >= 0 and applications_submitted >= 0 and high_fit_applications >= 0
        and application_progressions >= 0 and outreach_sent >= 0 and outreach_responses >= 0
        and referrals_secured >= 0 and preparation_sessions >= 0 and preparation_minutes >= 0
        and commitments_planned >= 0 and commitments_completed >= 0 and daily_actions_planned >= 0
        and daily_actions_completed >= 0 and active_pipeline >= 0 and overdue_application_actions >= 0
        and overdue_outreach_follow_ups >= 0 and stage_applied >= 0 and stage_recruiter_screen >= 0
        and stage_interviewing >= 0 and stage_offer >= 0
    )
);

create table weekly_review_revision (
    id uuid primary key,
    review_id uuid not null references weekly_review(id) on delete cascade,
    revision_number integer not null,
    wins text,
    challenges text,
    reflection text,
    next_week_adjustments text,
    next_week_focus text,
    created_at timestamp with time zone not null,
    constraint weekly_review_revision_number_uidx unique (review_id, revision_number),
    constraint weekly_review_revision_number_check check (revision_number > 0)
);

create index weekly_review_week_idx on weekly_review (week_start desc);
create index weekly_review_revision_review_idx on weekly_review_revision (review_id, revision_number desc);
