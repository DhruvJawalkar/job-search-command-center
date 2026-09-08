create table linkedin_connection_import_batch (
    id uuid primary key,
    source_file varchar(500) not null,
    content_hash varchar(64) not null,
    status varchar(32) not null,
    rows_seen integer not null default 0,
    connections_created integer not null default 0,
    connections_updated integer not null default 0,
    rows_skipped integer not null default 0,
    error_message text,
    started_at timestamp with time zone not null,
    completed_at timestamp with time zone,
    constraint linkedin_connection_import_hash_uidx unique (content_hash)
);

create table linkedin_connection (
    id uuid primary key,
    full_name varchar(200) not null,
    company_name varchar(240),
    normalized_company_name varchar(240),
    role_title varchar(500),
    profile_url varchar(1500),
    normalized_profile_url varchar(1500) not null,
    connected_on date,
    source_row integer not null,
    import_batch_id uuid not null references linkedin_connection_import_batch(id),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null default 0,
    constraint linkedin_connection_profile_uidx unique (normalized_profile_url)
);

create index linkedin_connection_company_idx
    on linkedin_connection (normalized_company_name, connected_on desc, full_name);
