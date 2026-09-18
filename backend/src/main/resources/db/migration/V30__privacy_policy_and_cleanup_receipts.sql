create table privacy_policy (
    id smallint primary key,
    revision bigint not null,
    assistance_context_mode varchar(32) not null,
    derived_context_retention_days integer,
    connected_assistance_enabled boolean not null,
    consent_text_version varchar(80),
    consent_accepted_at timestamp with time zone,
    last_successful_cleanup_at timestamp with time zone,
    next_scheduled_cleanup_at timestamp with time zone,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    lock_version bigint not null,
    constraint ck_privacy_policy_singleton check (id = 1),
    constraint ck_privacy_policy_revision check (revision >= 1),
    constraint ck_privacy_policy_mode check
        (assistance_context_mode in ('STATELESS', 'SESSION_ONLY', 'TIME_BOUND')),
    constraint ck_privacy_policy_derived_retention check
        ((assistance_context_mode = 'TIME_BOUND' and derived_context_retention_days in (7, 30, 90))
         or (assistance_context_mode <> 'TIME_BOUND' and derived_context_retention_days is null)),
    constraint ck_privacy_policy_consent check
        ((consent_text_version is null and consent_accepted_at is null)
         or (consent_text_version is not null and consent_accepted_at is not null))
);

insert into privacy_policy (
    id, revision, assistance_context_mode, derived_context_retention_days,
    connected_assistance_enabled, created_at, updated_at, lock_version
) values (1, 1, 'STATELESS', null, false, current_timestamp, current_timestamp, 0);

create table privacy_cleanup_receipt (
    id uuid primary key,
    policy_revision bigint not null,
    category varchar(64) not null,
    cutoff timestamp with time zone not null,
    assistance_run_count integer not null,
    assistance_decision_count integer not null,
    outcome varchar(24) not null,
    created_at timestamp with time zone not null,
    constraint ck_privacy_cleanup_receipt_category check
        (category in ('DERIVED_ASSISTANCE_CONTEXT')),
    constraint ck_privacy_cleanup_receipt_counts check
        (assistance_run_count >= 0 and assistance_decision_count >= 0),
    constraint ck_privacy_cleanup_receipt_outcome check
        (outcome in ('SUCCESS'))
);

create index idx_privacy_cleanup_receipt_created_at
    on privacy_cleanup_receipt(created_at desc);
