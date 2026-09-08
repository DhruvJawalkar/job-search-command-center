create table preparation_sprint (
    id uuid primary key,
    status varchar(32) not null check (status in ('ACTIVE', 'CLOSED')),
    start_date date not null,
    end_date date not null,
    closed_at timestamp with time zone,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null default 0,
    check (end_date >= start_date),
    check ((status = 'ACTIVE' and closed_at is null) or status = 'CLOSED')
);

create unique index preparation_sprint_single_active_idx
    on preparation_sprint (status)
    where status = 'ACTIVE';

create index preparation_sprint_dates_idx
    on preparation_sprint (start_date desc, end_date desc);

create table preparation_sprint_item (
    id uuid primary key,
    sprint_id uuid not null references preparation_sprint(id) on delete cascade,
    prep_item_id uuid not null references preparation_item(id) on delete cascade,
    final_status varchar(40),
    added_at timestamp with time zone not null,
    unique (sprint_id, prep_item_id)
);

create index preparation_sprint_item_sprint_idx
    on preparation_sprint_item (sprint_id, added_at);

with active_window as (
    select date_trunc('week', coalesce(min(scheduled_for), min(updated_at)::date)::timestamp)::date as start_date
    from preparation_item
    where status in ('READY', 'IN_PROGRESS', 'IN_REVIEW')
)
insert into preparation_sprint (id, status, start_date, end_date, created_at, updated_at, version)
select 'e4dddab6-1f1f-4ce7-935f-8f8239dd9ce4', 'ACTIVE', start_date, start_date + 13,
       current_timestamp, current_timestamp, 0
from active_window
where start_date is not null;

insert into preparation_sprint_item (id, sprint_id, prep_item_id, added_at)
select gen_random_uuid(), sprint.id, item.id, current_timestamp
from preparation_sprint sprint
join preparation_item item on item.status in ('READY', 'IN_PROGRESS', 'IN_REVIEW', 'COMPLETED')
where sprint.status = 'ACTIVE'
  and (item.status in ('IN_PROGRESS', 'IN_REVIEW')
       or item.scheduled_for between sprint.start_date and sprint.end_date);
