alter table job_application
    add column follow_up_active boolean not null default true;

create index job_application_follow_up_queue_idx
    on job_application (follow_up_active, next_action_at)
    where follow_up_active = true;
