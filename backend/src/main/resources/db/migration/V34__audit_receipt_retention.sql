alter table privacy_cleanup_receipt
    drop constraint ck_privacy_cleanup_receipt_counts;

alter table privacy_cleanup_receipt
    add column audit_metadata_record_count integer not null default 0;

alter table privacy_cleanup_receipt
    add constraint ck_privacy_cleanup_receipt_counts check
        (assistance_run_count >= 0 and assistance_decision_count >= 0
         and transient_database_record_count >= 0 and audit_metadata_record_count >= 0
         and transient_file_count >= 0 and skipped_unsafe_file_count >= 0);
