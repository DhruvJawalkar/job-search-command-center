create table transmission_preview (
    id uuid primary key,
    token_hash char(64) not null unique,
    operation varchar(48) not null,
    destination varchar(500) not null,
    purpose varchar(240) not null,
    minimized_fields varchar(1000) not null,
    payload_hash char(64) not null,
    expires_at timestamp with time zone not null,
    consumed_at timestamp with time zone,
    created_at timestamp with time zone not null,
    constraint ck_transmission_preview_operation check
        (operation in ('OPENAI_INBOX_STRUCTURING', 'OPENAI_WEEKLY_REFLECTION', 'LIVE_JOB_PAGE_FETCH'))
);

create table transmission_receipt (
    id uuid primary key,
    preview_id uuid not null references transmission_preview(id),
    operation varchar(48) not null,
    destination varchar(500) not null,
    purpose varchar(240) not null,
    minimized_fields varchar(1000) not null,
    payload_hash char(64) not null,
    outcome varchar(24) not null,
    created_at timestamp with time zone not null,
    completed_at timestamp with time zone not null,
    constraint ck_transmission_receipt_operation check
        (operation in ('OPENAI_INBOX_STRUCTURING', 'OPENAI_WEEKLY_REFLECTION', 'LIVE_JOB_PAGE_FETCH')),
    constraint ck_transmission_receipt_outcome check
        (outcome in ('SUCCESS', 'PARTIAL', 'FAILED'))
);

create index idx_transmission_preview_expires_at on transmission_preview(expires_at);
create index idx_transmission_receipt_created_at on transmission_receipt(created_at desc);
