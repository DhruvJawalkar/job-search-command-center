create table calendar_event (
    id uuid primary key,
    title varchar(240) not null,
    event_type varchar(40) not null,
    description text,
    location varchar(500),
    meeting_url varchar(2000),
    starts_at timestamp with time zone not null,
    ends_at timestamp with time zone not null,
    reminder_minutes_before integer,
    reminder_at timestamp with time zone,
    reminder_dismissed_at timestamp with time zone,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null default 0,
    constraint calendar_event_time_order check (ends_at > starts_at),
    constraint calendar_event_reminder_positive check (reminder_minutes_before is null or reminder_minutes_before > 0),
    constraint calendar_event_reminder_consistent check (
        (reminder_minutes_before is null and reminder_at is null)
        or (reminder_minutes_before is not null and reminder_at is not null)
    )
);

create index calendar_event_time_idx on calendar_event (starts_at, ends_at);
create index calendar_event_reminder_idx on calendar_event (reminder_at)
    where reminder_at is not null and reminder_dismissed_at is null;
