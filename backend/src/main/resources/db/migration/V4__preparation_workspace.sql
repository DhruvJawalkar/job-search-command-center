create table preparation_track (
    id uuid primary key,
    name varchar(180) not null,
    description text,
    category varchar(48) not null,
    status varchar(40) not null,
    target_date date,
    display_order integer not null default 0,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null default 0
);

create table preparation_milestone (
    id uuid primary key,
    track_id uuid not null references preparation_track(id) on delete cascade,
    title varchar(220) not null,
    description text,
    status varchar(40) not null,
    target_date date,
    display_order integer not null default 0,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null default 0
);

create table preparation_item (
    id uuid primary key,
    milestone_id uuid not null references preparation_milestone(id) on delete cascade,
    opportunity_id uuid references job_opportunity(id) on delete set null,
    title varchar(240) not null,
    description text,
    status varchar(40) not null,
    priority integer not null check (priority between 1 and 5),
    estimated_minutes integer not null check (estimated_minutes > 0),
    scheduled_for date,
    due_date date,
    skill_focus varchar(500),
    next_review_on date,
    display_order integer not null default 0,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null default 0
);

create table practice_session (
    id uuid primary key,
    prep_item_id uuid not null references preparation_item(id) on delete cascade,
    session_type varchar(48) not null,
    practiced_at timestamp with time zone not null,
    duration_minutes integer not null check (duration_minutes > 0),
    result_summary text,
    mistakes text,
    next_steps text,
    confidence_before integer check (confidence_before between 1 and 5),
    confidence_after integer check (confidence_after between 1 and 5),
    next_review_on date,
    created_at timestamp with time zone not null
);

create table daily_prep_commitment (
    id uuid primary key,
    commitment_date date not null unique,
    prep_item_id uuid references preparation_item(id) on delete set null,
    planned_minutes integer not null check (planned_minutes > 0),
    status varchar(40) not null,
    intention text,
    reflection text,
    completed_at timestamp with time zone,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null default 0
);

create index preparation_milestone_track_idx on preparation_milestone(track_id, display_order, created_at);
create index preparation_item_milestone_idx on preparation_item(milestone_id, status, priority, display_order);
create index preparation_item_schedule_idx on preparation_item(scheduled_for, due_date, next_review_on);
create index practice_session_item_idx on practice_session(prep_item_id, practiced_at desc);
create index practice_session_practiced_at_idx on practice_session(practiced_at desc);
