create table preparation_track_resource (
    id uuid primary key,
    track_id uuid not null references preparation_track(id) on delete cascade,
    title varchar(180) not null,
    url varchar(2000) not null,
    notes text,
    created_at timestamp with time zone not null
);

create index preparation_track_resource_track_idx on preparation_track_resource (track_id, created_at);
