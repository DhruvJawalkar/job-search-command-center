alter table local_summary_preferences
    add column weekly_application_target integer not null default 10;

alter table local_summary_preferences
    add constraint summary_weekly_application_target_check
        check (weekly_application_target between 1 and 100);
