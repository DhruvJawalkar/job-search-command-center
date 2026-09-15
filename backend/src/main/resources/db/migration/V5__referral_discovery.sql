create table referral_candidate (
    id uuid primary key,
    opportunity_id uuid not null references job_opportunity(id) on delete cascade,
    contact_id uuid references network_contact(id) on delete set null,
    outreach_id uuid references outreach_activity(id) on delete set null,
    full_name varchar(200) not null,
    company_name varchar(240),
    role_title varchar(240),
    profile_url varchar(1500),
    connection_degree varchar(40) not null,
    discovery_channel varchar(48) not null,
    relationship_strength varchar(40) not null,
    mutual_connection_name varchar(200),
    mutual_connection_url varchar(1500),
    current_company_match boolean not null default false,
    former_company_match boolean not null default false,
    role_relevance integer not null check (role_relevance between 1 and 5),
    responsiveness integer not null check (responsiveness between 1 and 5),
    last_interaction_on date,
    status varchar(40) not null,
    path_score integer not null check (path_score between 0 and 100),
    score_explanation varchar(1000) not null,
    notes text,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null default 0
);

create index referral_candidate_opportunity_idx on referral_candidate(opportunity_id, status, path_score desc);
create index referral_candidate_company_idx on referral_candidate(lower(company_name), path_score desc);
create index referral_candidate_channel_idx on referral_candidate(discovery_channel, status);
