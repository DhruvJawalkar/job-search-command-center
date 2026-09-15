create table assistance_run (
    id uuid primary key,
    use_case varchar(40) not null,
    target_id uuid not null,
    input_hash char(64) not null,
    provider varchar(80) not null,
    model varchar(160) not null,
    prompt_version varchar(80) not null,
    schema_version varchar(80) not null,
    status varchar(32) not null,
    result_payload text,
    provider_response_id varchar(240),
    input_tokens integer,
    output_tokens integer,
    error_message text,
    created_at timestamp with time zone not null,
    completed_at timestamp with time zone,
    constraint uk_assistance_run_replay unique
        (use_case, target_id, input_hash, provider, model, prompt_version, schema_version),
    constraint ck_assistance_run_status check (status in ('RUNNING', 'COMPLETED', 'FAILED')),
    constraint ck_assistance_run_tokens check
        ((input_tokens is null or input_tokens >= 0) and (output_tokens is null or output_tokens >= 0))
);

create table assistance_decision (
    id uuid primary key,
    run_id uuid not null references assistance_run(id),
    decision_type varchar(40) not null,
    selected_fields text,
    note text,
    created_at timestamp with time zone not null,
    constraint ck_assistance_decision_type check
        (decision_type in ('FIELDS_APPLIED', 'SKILLS_PUBLISHED', 'DISMISSED'))
);

create index idx_assistance_run_target on assistance_run(use_case, target_id, created_at desc);
create index idx_assistance_decision_run on assistance_decision(run_id, created_at desc);
