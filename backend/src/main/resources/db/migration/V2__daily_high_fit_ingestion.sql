alter table job_opportunity
    add column is_demo boolean not null default false;

update job_opportunity
set is_demo = true
where source_url like 'https://example.com/%';

create index job_opportunity_demo_status_idx
    on job_opportunity (is_demo, status);

create table import_batch (
    id uuid primary key,
    source_type varchar(80) not null,
    source_file varchar(500) not null,
    action_source_file varchar(500),
    source_date date not null,
    content_hash varchar(64) not null,
    status varchar(32) not null,
    rows_seen integer not null default 0,
    opportunities_created integer not null default 0,
    opportunities_updated integer not null default 0,
    observations_created integer not null default 0,
    observations_updated integer not null default 0,
    actions_created integer not null default 0,
    actions_updated integer not null default 0,
    error_message text,
    started_at timestamp with time zone not null,
    completed_at timestamp with time zone,
    constraint import_batch_content_hash_uidx unique (content_hash)
);

create index import_batch_source_date_idx
    on import_batch (source_date desc, started_at desc);

create table opportunity_observation (
    id uuid primary key,
    import_batch_id uuid not null references import_batch(id),
    opportunity_id uuid not null references job_opportunity(id) on delete cascade,
    recommended_resume_variant_id uuid references resume_variant(id),
    observed_on date not null,
    source_rank integer not null,
    posting_date date,
    verified_date date not null,
    overall_fit numeric(5,3) not null,
    recruiter_screen_strength numeric(5,3) not null,
    technical_scope numeric(5,3) not null,
    growth_potential numeric(5,3) not null,
    weighted_total numeric(6,3) not null,
    recommendation varchar(80) not null,
    role_summary text not null,
    fit_rationale text not null,
    key_risks text,
    authorization_eligibility text,
    recommended_resume_name varchar(200),
    row_fingerprint varchar(64) not null,
    raw_payload text not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null default 0,
    constraint opportunity_observation_day_uidx unique (opportunity_id, observed_on),
    constraint opportunity_observation_rank_check check (source_rank > 0),
    constraint opportunity_observation_score_check check (
        overall_fit between 0 and 10
        and recruiter_screen_strength between 0 and 10
        and technical_scope between 0 and 10
        and growth_potential between 0 and 10
        and weighted_total between 0 and 10
    )
);

create index opportunity_observation_day_rank_idx
    on opportunity_observation (observed_on desc, source_rank);
create index opportunity_observation_score_idx
    on opportunity_observation (weighted_total desc);
create index opportunity_observation_resume_idx
    on opportunity_observation (recommended_resume_name);

create table daily_priority_action (
    id uuid primary key,
    import_batch_id uuid not null references import_batch(id),
    opportunity_id uuid references job_opportunity(id) on delete set null,
    action_date date not null,
    priority_rank integer not null,
    action_text text not null,
    status varchar(32) not null,
    source_file varchar(500) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null default 0,
    constraint daily_priority_action_day_rank_uidx unique (action_date, priority_rank),
    constraint daily_priority_action_rank_check check (priority_rank between 1 and 20)
);

create index daily_priority_action_date_status_idx
    on daily_priority_action (action_date desc, status, priority_rank);
