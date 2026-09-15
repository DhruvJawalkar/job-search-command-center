alter table outreach_activity
    add column follow_up_count integer not null default 0;

alter table outreach_activity
    add constraint outreach_activity_follow_up_count_non_negative
        check (follow_up_count >= 0);
