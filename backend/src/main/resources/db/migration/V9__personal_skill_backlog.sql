create table personal_skill_backlog (
    id uuid primary key,
    skill_id uuid not null references canonical_skill(id),
    current_level varchar(40) not null,
    target_level varchar(40) not null,
    priority integer not null check (priority between 1 and 5),
    rationale text,
    status varchar(32) not null,
    next_review_on date,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null default 0,
    constraint personal_skill_backlog_skill_uidx unique (skill_id)
);

create table personal_skill_preparation_link (
    id uuid primary key,
    backlog_id uuid not null references personal_skill_backlog(id) on delete cascade,
    prep_item_id uuid not null references preparation_item(id) on delete cascade,
    link_note text,
    created_at timestamp with time zone not null,
    constraint personal_skill_preparation_link_uidx unique (backlog_id, prep_item_id)
);

create table skill_learning_resource (
    id uuid primary key,
    backlog_id uuid not null references personal_skill_backlog(id) on delete cascade,
    title varchar(240) not null,
    url varchar(1000),
    resource_type varchar(40) not null,
    status varchar(32) not null,
    notes text,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null default 0
);

create table skill_project_evidence (
    id uuid primary key,
    backlog_id uuid not null references personal_skill_backlog(id) on delete cascade,
    title varchar(240) not null,
    url varchar(1000),
    evidence_type varchar(40) not null,
    description text,
    outcome text,
    completed_on date,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null default 0
);

create index personal_skill_backlog_priority_idx on personal_skill_backlog (status, priority, next_review_on);
create index personal_skill_preparation_link_item_idx on personal_skill_preparation_link (prep_item_id, backlog_id);
create index skill_learning_resource_backlog_idx on skill_learning_resource (backlog_id, status, created_at);
create index skill_project_evidence_backlog_idx on skill_project_evidence (backlog_id, completed_on, created_at);
