alter table job_opportunity add column archive_reason text;
alter table job_opportunity add column archived_at timestamp with time zone;
alter table job_opportunity add column archived_from_status varchar(40);

create index job_opportunity_archived_at_idx on job_opportunity (archived_at desc)
    where status = 'ARCHIVED';
