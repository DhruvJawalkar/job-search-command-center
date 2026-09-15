create table interview_round (
 id uuid primary key,
 application_id uuid not null references job_application(id),
 calendar_event_id uuid unique references calendar_event(id),
 title varchar(240) not null, round_type varchar(40) not null,
 status varchar(40) not null, outcome varchar(40) not null,
 preparation_notes text, debrief text,
 created_at timestamp with time zone not null, updated_at timestamp with time zone not null,
 version bigint not null default 0,
 constraint interview_status_check check (status in ('AWAITING_SCHEDULING','SCHEDULED','COMPLETED','CANCELLED')),
 constraint interview_outcome_check check (outcome in ('NOT_RECORDED','AWAITING_FEEDBACK','ADVANCED','REJECTED')),
 constraint interview_schedule_check check (status <> 'SCHEDULED' or calendar_event_id is not null)
);
create index interview_application_idx on interview_round(application_id, created_at);
