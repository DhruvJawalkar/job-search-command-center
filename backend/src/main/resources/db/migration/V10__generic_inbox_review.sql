create table generic_inbox_item (
    id uuid primary key,
    source_type varchar(32) not null,
    source_label varchar(160),
    source_filename varchar(500),
    media_type varchar(120),
    content_hash varchar(64) not null,
    raw_payload text not null,
    parser_version varchar(40) not null,
    status varchar(32) not null,
    candidate_count integer not null default 0,
    error_message text,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null default 0,
    constraint generic_inbox_item_content_hash_uidx unique (content_hash),
    constraint generic_inbox_item_candidate_count_check check (candidate_count >= 0)
);

create index generic_inbox_item_status_created_idx
    on generic_inbox_item (status, created_at desc);

create table generic_inbox_candidate (
    id uuid primary key,
    inbox_item_id uuid not null references generic_inbox_item(id) on delete cascade,
    row_number integer not null,
    company_name varchar(240),
    role_title varchar(240),
    location varchar(240),
    work_mode varchar(32) not null,
    source_name varchar(120),
    source_url varchar(1500),
    description text,
    parse_warnings text,
    raw_payload text not null,
    status varchar(32) not null,
    opportunity_id uuid references job_opportunity(id) on delete set null,
    reviewed_at timestamp with time zone,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null default 0,
    constraint generic_inbox_candidate_row_uidx unique (inbox_item_id, row_number),
    constraint generic_inbox_candidate_row_check check (row_number > 0)
);

create index generic_inbox_candidate_item_status_idx
    on generic_inbox_candidate (inbox_item_id, status, row_number);
create index generic_inbox_candidate_opportunity_idx
    on generic_inbox_candidate (opportunity_id);
