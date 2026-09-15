create table canonical_skill (
    id uuid primary key,
    name varchar(160) not null,
    normalized_name varchar(160) not null,
    category varchar(48) not null,
    description text,
    active boolean not null default true,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null default 0,
    constraint canonical_skill_normalized_name_uidx unique (normalized_name)
);

create table skill_alias (
    id uuid primary key,
    skill_id uuid not null references canonical_skill(id) on delete cascade,
    alias varchar(160) not null,
    normalized_alias varchar(160) not null,
    created_at timestamp with time zone not null,
    constraint skill_alias_normalized_uidx unique (normalized_alias)
);

create index skill_alias_skill_idx on skill_alias (skill_id, alias);

create table job_description_snapshot (
    id uuid primary key,
    opportunity_id uuid not null references job_opportunity(id) on delete cascade,
    source_type varchar(48) not null,
    source_label varchar(500),
    content text not null,
    content_hash varchar(64) not null,
    captured_at timestamp with time zone not null,
    constraint job_description_snapshot_content_uidx unique (opportunity_id, content_hash)
);

create index job_description_snapshot_opportunity_idx
    on job_description_snapshot (opportunity_id, captured_at desc);

create table job_skill_observation (
    id uuid primary key,
    opportunity_id uuid not null references job_opportunity(id) on delete cascade,
    snapshot_id uuid not null references job_description_snapshot(id) on delete cascade,
    skill_id uuid not null references canonical_skill(id),
    strength varchar(32) not null,
    evidence_snippet text not null,
    evidence_fingerprint varchar(64) not null,
    extraction_method varchar(48) not null,
    review_status varchar(32) not null,
    review_note text,
    observed_at timestamp with time zone not null,
    reviewed_at timestamp with time zone,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null default 0,
    constraint job_skill_observation_evidence_uidx unique (snapshot_id, skill_id, evidence_fingerprint)
);

create index job_skill_observation_review_idx
    on job_skill_observation (review_status, updated_at desc);
create index job_skill_observation_skill_idx
    on job_skill_observation (skill_id, review_status, observed_at desc);
create index job_skill_observation_opportunity_idx
    on job_skill_observation (opportunity_id, observed_at desc);
