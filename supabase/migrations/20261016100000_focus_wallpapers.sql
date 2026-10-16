-- Focus wallpapers: each focus session shows one of these photos, picked at random, behind its countdown.
-- The picture files are in the public Storage bucket `wallpapers` (declared in config.toml, uploaded by
-- `supabase seed buckets`); a row can also point anywhere else on https. The Focus tab works without an
-- account, so anonymous clients may read the active rows. Rows are added by migrations or in the dashboard.

create table public.focus_wallpapers (
  id text primary key check (id ~ '^[a-z0-9]+(-[a-z0-9]+)*$' and char_length(id) <= 60),
  url text not null check (url ~ '^https://' and char_length(url) <= 500),
  width integer not null check (width between 1 and 20000),
  height integer not null check (height between 1 and 20000),
  -- The picture's average colour, shown while it loads.
  color text not null default '#808080' check (color ~ '^#[0-9a-f]{6}$'),
  -- How far down the picture its subject is (0 = top, 1 = bottom): a card showing part of it keeps that in view.
  focus_y real not null default 0.5 check (focus_y between 0 and 1),
  author text not null default '' check (char_length(author) <= 100),
  author_url text not null default '' check (author_url = '' or author_url ~ '^https://'),
  source_url text not null default '' check (source_url = '' or source_url ~ '^https://'),
  licence text not null default '' check (char_length(licence) <= 100),
  active boolean not null default true,
  created_at timestamptz not null default now()
);
comment on table public.focus_wallpapers is 'Photos for the Focus countdown. The app picks one at random from the active rows.';

alter table public.focus_wallpapers enable row level security;

create policy "Anyone reads the active wallpapers" on public.focus_wallpapers
  for select to anon, authenticated using (active);

revoke all on public.focus_wallpapers from anon, authenticated;
grant select on public.focus_wallpapers to anon, authenticated;
