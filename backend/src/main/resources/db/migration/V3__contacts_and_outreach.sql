create table network_contact (
    id uuid primary key,
    full_name varchar(200) not null,
    company_name varchar(240),
    role_title varchar(240),
    profile_url varchar(1500),
    email varchar(320),
    relationship_strength varchar(40) not null,
    notes text,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null default 0
);

create index network_contact_company_name_idx
    on network_contact (lower(company_name), lower(full_name));

create table outreach_activity (
    id uuid primary key,
    contact_id uuid not null references network_contact(id),
    opportunity_id uuid not null references job_opportunity(id) on delete cascade,
    application_id uuid references job_application(id) on delete set null,
    outreach_type varchar(48) not null,
    status varchar(40) not null,
    channel varchar(80),
    message_summary text,
    requested_at timestamp with time zone,
    follow_up_at timestamp with time zone,
    responded_at timestamp with time zone,
    outcome varchar(500),
    notes text,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null default 0
);

create index outreach_activity_follow_up_idx
    on outreach_activity (status, follow_up_at);
create index outreach_activity_opportunity_idx
    on outreach_activity (opportunity_id, created_at desc);
create index outreach_activity_contact_idx
    on outreach_activity (contact_id, created_at desc);
