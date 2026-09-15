create table application_artifact (
    id uuid primary key,
    application_id uuid not null references job_application(id) on delete cascade,
    artifact_type varchar(40) not null,
    original_filename varchar(500),
    stored_relative_path varchar(1200) not null,
    content_hash varchar(64) not null,
    media_type varchar(160) not null,
    size_bytes bigint not null,
    created_at timestamp with time zone not null,
    constraint uk_application_artifact_type unique (application_id, artifact_type),
    constraint uk_application_artifact_path unique (stored_relative_path),
    constraint ck_application_artifact_type check (artifact_type in ('RESUME_PDF', 'JOB_DESCRIPTION_TEXT')),
    constraint ck_application_artifact_size check (size_bytes >= 0)
);

create index idx_application_artifact_application
    on application_artifact(application_id, artifact_type);
