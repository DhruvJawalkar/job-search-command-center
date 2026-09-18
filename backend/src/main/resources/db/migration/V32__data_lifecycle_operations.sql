create table data_lifecycle_operation (
    id uuid primary key,
    operation_type varchar(32) not null,
    category_scope varchar(500) not null,
    status varchar(40) not null,
    plan_digest char(64) not null,
    confirmation_token_hash char(64) not null,
    confirmation_phrase varchar(160),
    planned_database_rows bigint not null,
    planned_file_count bigint not null,
    planned_byte_count bigint not null,
    skipped_unsafe_entry_count bigint not null,
    affected_database_rows bigint,
    deleted_file_count bigint,
    failed_file_count bigint,
    export_byte_count bigint,
    created_at timestamp with time zone not null,
    expires_at timestamp with time zone not null,
    completed_at timestamp with time zone,
    constraint ck_data_lifecycle_operation_type check
        (operation_type in ('EXPORT', 'DELETE_CATEGORIES', 'DELETE_ALL')),
    constraint ck_data_lifecycle_operation_status check
        (status in ('PREVIEWED', 'EXPORT_COMPLETED', 'DATABASE_DELETED', 'COMPLETED',
                    'PARTIAL_FILESYSTEM_FAILURE', 'FAILED', 'STALE', 'EXPIRED')),
    constraint ck_data_lifecycle_operation_counts check
        (planned_database_rows >= 0 and planned_file_count >= 0 and planned_byte_count >= 0
         and skipped_unsafe_entry_count >= 0
         and (affected_database_rows is null or affected_database_rows >= 0)
         and (deleted_file_count is null or deleted_file_count >= 0)
         and (failed_file_count is null or failed_file_count >= 0)
         and (export_byte_count is null or export_byte_count >= 0))
);

create index idx_data_lifecycle_operation_created_at
    on data_lifecycle_operation(created_at desc);
