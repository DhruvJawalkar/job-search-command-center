alter table privacy_policy
    add column transient_ingestion_retention_days integer not null default 7;

alter table privacy_policy
    add constraint ck_privacy_policy_transient_retention
        check (transient_ingestion_retention_days in (7, 30));

alter table daily_priority_action
    alter column import_batch_id drop not null;
alter table daily_priority_action
    drop constraint daily_priority_action_import_batch_id_fkey;
alter table daily_priority_action
    add constraint daily_priority_action_import_batch_id_fkey
        foreign key (import_batch_id) references import_batch(id) on delete set null;

alter table linkedin_connection
    alter column import_batch_id drop not null;
alter table linkedin_connection
    drop constraint linkedin_connection_import_batch_id_fkey;
alter table linkedin_connection
    add constraint linkedin_connection_import_batch_id_fkey
        foreign key (import_batch_id) references linkedin_connection_import_batch(id) on delete set null;

alter table privacy_cleanup_receipt
    drop constraint ck_privacy_cleanup_receipt_category;
alter table privacy_cleanup_receipt
    drop constraint ck_privacy_cleanup_receipt_counts;
alter table privacy_cleanup_receipt
    drop constraint ck_privacy_cleanup_receipt_outcome;

alter table privacy_cleanup_receipt
    add column transient_database_record_count integer not null default 0,
    add column transient_file_count integer not null default 0,
    add column skipped_unsafe_file_count integer not null default 0,
    add column transient_cutoff timestamp with time zone;

alter table privacy_cleanup_receipt
    add constraint ck_privacy_cleanup_receipt_category check
        (category in ('DERIVED_ASSISTANCE_CONTEXT', 'RETENTION_ENFORCEMENT')),
    add constraint ck_privacy_cleanup_receipt_counts check
        (assistance_run_count >= 0 and assistance_decision_count >= 0
         and transient_database_record_count >= 0 and transient_file_count >= 0
         and skipped_unsafe_file_count >= 0),
    add constraint ck_privacy_cleanup_receipt_outcome check
        (outcome in ('SUCCESS', 'PARTIAL'));
