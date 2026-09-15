alter table job_opportunity
    add column source_external_id varchar(240);

create unique index job_opportunity_source_external_uidx
    on job_opportunity (lower(source_name), source_external_id)
    where source_name is not null and source_external_id is not null;

alter table generic_inbox_candidate
    add column source_external_id varchar(240);

create table inbox_duplicate_match (
    id uuid primary key,
    candidate_id uuid not null references generic_inbox_candidate(id) on delete cascade,
    opportunity_id uuid not null references job_opportunity(id) on delete cascade,
    match_type varchar(32) not null,
    confidence integer not null,
    explanation text not null,
    status varchar(24) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    resolved_at timestamp with time zone,
    version bigint not null default 0,
    constraint inbox_duplicate_match_candidate_opportunity_uidx unique (candidate_id, opportunity_id),
    constraint inbox_duplicate_match_confidence_check check (confidence between 0 and 100)
);

create index inbox_duplicate_match_candidate_status_idx
    on inbox_duplicate_match (candidate_id, status, confidence desc);
create index inbox_duplicate_match_opportunity_idx
    on inbox_duplicate_match (opportunity_id);
