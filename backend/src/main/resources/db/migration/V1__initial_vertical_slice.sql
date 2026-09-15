create table resume_variant (
    id uuid primary key,
    name varchar(160) not null,
    target_role varchar(200) not null,
    version_label varchar(80) not null,
    file_path varchar(1000),
    content_hash varchar(128),
    active boolean not null default true,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null default 0
);

create table job_opportunity (
    id uuid primary key,
    company_name varchar(240) not null,
    role_title varchar(240) not null,
    location varchar(240),
    work_mode varchar(32),
    source_name varchar(120),
    source_url varchar(1500),
    canonical_url varchar(1500),
    description text,
    status varchar(40) not null,
    fit_score integer,
    fit_summary text,
    discovered_at timestamp with time zone not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null default 0,
    constraint job_opportunity_fit_score_check check (fit_score is null or (fit_score between 0 and 100))
);

create unique index job_opportunity_canonical_url_uidx
    on job_opportunity (canonical_url)
    where canonical_url is not null;
create index job_opportunity_status_idx on job_opportunity (status);
create index job_opportunity_discovered_at_idx on job_opportunity (discovered_at desc);

create table job_application (
    id uuid primary key,
    opportunity_id uuid not null references job_opportunity(id),
    resume_variant_id uuid not null references resume_variant(id),
    stage varchar(48) not null,
    applied_on date,
    channel varchar(120),
    next_action varchar(500) not null,
    next_action_at timestamp with time zone not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null default 0,
    constraint job_application_opportunity_uidx unique (opportunity_id)
);

create index job_application_stage_idx on job_application (stage);
create index job_application_next_action_at_idx on job_application (next_action_at);

create table application_event (
    id uuid primary key,
    application_id uuid not null references job_application(id) on delete cascade,
    from_stage varchar(48),
    to_stage varchar(48) not null,
    note text,
    occurred_at timestamp with time zone not null
);

create index application_event_application_time_idx
    on application_event (application_id, occurred_at desc);

