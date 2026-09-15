alter table preparation_item add column completed_at timestamp with time zone;

update preparation_item
set completed_at = updated_at
where status = 'COMPLETED';

create index preparation_item_completed_at_idx on preparation_item (completed_at);

alter table weekly_metric_snapshot
    add column preparation_tasks_planned integer not null default 0,
    add column preparation_tasks_completed integer not null default 0;

alter table weekly_metric_snapshot
    add constraint weekly_metric_snapshot_preparation_tasks_check check (
        preparation_tasks_planned >= 0 and preparation_tasks_completed >= 0
    );
