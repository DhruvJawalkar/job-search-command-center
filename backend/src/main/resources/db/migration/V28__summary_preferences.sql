create table local_summary_preferences (
    id uuid primary key,
    schedule_json text not null,
    priorities_json text not null,
    specialization_mode varchar(30) not null,
    fixed_specialization_day integer,
    recommendation_focus varchar(30) not null,
    show_daily_perspective boolean not null default true,
    show_market_lens boolean not null default true,
    show_technology_watch boolean not null default true,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    version bigint not null default 0,
    constraint summary_fixed_specialization_day_check check (
        fixed_specialization_day is null or (fixed_specialization_day between 0 and 6)
    )
);
