-- Vocabulary learning: shared word content and each learner's own data (docs/vocabulary-spec.md §5).
-- Every table has row-level security, and privileges are granted explicitly: nothing is reachable
-- through the API unless a grant and a policy below allow it.

-- Word keys, as VocabKit's Headword.key makes them: lower-case, trimmed, single spaces.
create domain public.headword as text
  check (value <> '' and value = lower(value) and value = btrim(value) and value !~ '\s\s' and char_length(value) <= 60);

-- ---------------------------------------------------------------------------------------------
-- Shared content: readable by every signed-in user, written only by migrations.

create table public.words (
  id bigint generated always as identity primary key,
  headword public.headword not null unique,
  display text not null check (display <> '' and char_length(display) <= 60),
  phonetic text not null default '',
  translation_zh text not null check (translation_zh <> ''),
  definition_en text not null default '',
  pos text not null default '',
  collins smallint not null default 0 check (collins between 0 and 5),
  oxford boolean not null default false,
  tags text[] not null default '{}',
  bnc integer check (bnc > 0),
  frq integer check (frq > 0),
  exchange text not null default '',
  source text not null default 'ECDICT'
);
comment on table public.words is 'Dictionary entries (ECDICT, MIT licence). One row per word key.';

-- A book is a system book (owner_id null, from a migration) or a learner's own imported list.
create table public.books (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid references auth.users (id) on delete cascade,
  name text not null check (char_length(name) between 1 and 100),
  description text not null default '' check (char_length(description) <= 1000),
  source text not null default '',
  licence text not null default '',
  word_count integer not null default 0 check (word_count >= 0),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);
create index books_owner_id_idx on public.books (owner_id);

-- The words of a book in study order. `word_id` links dictionary words; imported words the dictionary
-- doesn't know have none and carry their own meaning.
create table public.book_entries (
  id uuid primary key default gen_random_uuid(),
  book_id uuid not null references public.books (id) on delete cascade,
  position integer not null check (position > 0),
  headword public.headword not null,
  word_id bigint references public.words (id),
  display text check (char_length(display) <= 60),
  meaning text check (char_length(meaning) <= 2000),
  phonetic text check (char_length(phonetic) <= 100),
  pos text check (char_length(pos) <= 50),
  example text check (char_length(example) <= 2000),
  example_translation text check (char_length(example_translation) <= 2000),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  unique (book_id, headword)
);
create index book_entries_book_position_idx on public.book_entries (book_id, position);
create index book_entries_word_id_idx on public.book_entries (word_id);

-- ---------------------------------------------------------------------------------------------
-- Each learner's own data.

-- Every answer ever given. Append-only: the source of truth that devices replay to rebuild cards.
create table public.review_logs (
  id uuid primary key,
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  headword public.headword not null,
  rating smallint not null check (rating between 1 and 4),
  mode text not null check (mode in ('recognition', 'choice', 'spelling')),
  reviewed_at timestamptz not null,
  study_day integer not null,
  duration_ms integer not null default 0 check (duration_ms between 0 and 86400000),
  device_id uuid,
  received_at timestamptz not null default now()
);
comment on column public.review_logs.received_at is 'Server time of upload (set by a trigger); the sync pull cursor.';
create index review_logs_user_received_idx on public.review_logs (user_id, received_at);

create table public.favorites (
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  headword public.headword not null,
  is_favorite boolean not null,
  updated_at timestamptz not null,
  primary key (user_id, headword)
);

create table public.user_settings (
  user_id uuid primary key default auth.uid() references auth.users (id) on delete cascade,
  book_id uuid references public.books (id) on delete set null,
  daily_new_words integer not null default 20 check (daily_new_words between 0 and 200),
  review_mode text not null default 'recognition' check (review_mode in ('recognition', 'choice', 'spelling', 'mixed')),
  exam_date date,
  day_start_minutes integer not null default 240 check (day_start_minutes between 0 and 1439),
  desired_retention numeric(3, 2) not null default 0.90 check (desired_retention between 0.70 and 0.97),
  updated_at timestamptz not null
);
create index user_settings_book_id_idx on public.user_settings (book_id);

-- ---------------------------------------------------------------------------------------------
-- Triggers.

-- Uploaded answers get the server's clock, whatever the device sent: it drives the pull cursor.
create function public.stamp_received_at() returns trigger
language plpgsql set search_path = '' as $$
begin
  new.received_at := now();
  return new;
end
$$;

create trigger review_logs_stamp_received_at
  before insert on public.review_logs
  for each row execute function public.stamp_received_at();

-- Newest edit wins: an update carrying an older `updated_at` than the stored row is dropped, so a
-- device that syncs late can't overwrite a newer change.
create function public.keep_newest_edit() returns trigger
language plpgsql set search_path = '' as $$
begin
  if new.updated_at < old.updated_at then
    return null;
  end if;
  return new;
end
$$;

create trigger books_keep_newest_edit before update on public.books
  for each row execute function public.keep_newest_edit();
create trigger book_entries_keep_newest_edit before update on public.book_entries
  for each row execute function public.keep_newest_edit();
create trigger favorites_keep_newest_edit before update on public.favorites
  for each row execute function public.keep_newest_edit();
create trigger user_settings_keep_newest_edit before update on public.user_settings
  for each row execute function public.keep_newest_edit();

revoke all on function public.stamp_received_at() from public, anon, authenticated;
revoke all on function public.keep_newest_edit() from public, anon, authenticated;

-- ---------------------------------------------------------------------------------------------
-- Row-level security.

alter table public.words enable row level security;
alter table public.books enable row level security;
alter table public.book_entries enable row level security;
alter table public.review_logs enable row level security;
alter table public.favorites enable row level security;
alter table public.user_settings enable row level security;

create policy "Signed-in users read words" on public.words
  for select to authenticated using (true);

create policy "Read system books and your own" on public.books
  for select to authenticated using (owner_id is null or owner_id = (select auth.uid()));
create policy "Create your own books" on public.books
  for insert to authenticated with check (owner_id = (select auth.uid()));
create policy "Edit your own books" on public.books
  for update to authenticated
  using (owner_id = (select auth.uid())) with check (owner_id = (select auth.uid()));

create policy "Read the entries of readable books" on public.book_entries
  for select to authenticated using (
    exists (select 1 from public.books b where b.id = book_id and (b.owner_id is null or b.owner_id = (select auth.uid())))
  );
create policy "Add entries to your own books" on public.book_entries
  for insert to authenticated with check (
    exists (select 1 from public.books b where b.id = book_id and b.owner_id = (select auth.uid()))
  );
create policy "Edit the entries of your own books" on public.book_entries
  for update to authenticated
  using (exists (select 1 from public.books b where b.id = book_id and b.owner_id = (select auth.uid())))
  with check (exists (select 1 from public.books b where b.id = book_id and b.owner_id = (select auth.uid())));

create policy "Read your own answers" on public.review_logs
  for select to authenticated using (user_id = (select auth.uid()));
create policy "Add your own answers" on public.review_logs
  for insert to authenticated with check (user_id = (select auth.uid()));

create policy "Read your own favourites" on public.favorites
  for select to authenticated using (user_id = (select auth.uid()));
create policy "Add your own favourites" on public.favorites
  for insert to authenticated with check (user_id = (select auth.uid()));
create policy "Edit your own favourites" on public.favorites
  for update to authenticated
  using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

-- The chosen book must be one the learner can read (the subquery sees only readable books).
create policy "Read your own settings" on public.user_settings
  for select to authenticated using (user_id = (select auth.uid()));
create policy "Add your own settings" on public.user_settings
  for insert to authenticated with check (
    user_id = (select auth.uid()) and (book_id is null or exists (select 1 from public.books b where b.id = book_id))
  );
create policy "Edit your own settings" on public.user_settings
  for update to authenticated
  using (user_id = (select auth.uid()))
  with check (user_id = (select auth.uid()) and (book_id is null or exists (select 1 from public.books b where b.id = book_id)));

-- ---------------------------------------------------------------------------------------------
-- Privileges. Anonymous clients get nothing; signed-in users get only what the app needs. Answers
-- can't be changed or deleted, and nothing is deleted through the API (books use `deleted_at`).

revoke all on public.words, public.books, public.book_entries, public.review_logs, public.favorites, public.user_settings
  from anon, authenticated;
grant select on public.words to authenticated;
grant select, insert, update on public.books, public.book_entries to authenticated;
grant select, insert on public.review_logs to authenticated;
grant select, insert, update on public.favorites, public.user_settings to authenticated;

-- ---------------------------------------------------------------------------------------------
-- Account deletion (App Store rule 5.1.1(v)): removes the signed-in user; foreign keys delete all of
-- their rows with it.

create function public.delete_my_account() returns void
language plpgsql security definer set search_path = '' as $$
declare
  uid uuid := auth.uid();
begin
  if uid is null then
    raise exception 'not signed in' using errcode = '42501';
  end if;
  delete from auth.users where id = uid;
end
$$;

revoke all on function public.delete_my_account() from public, anon, authenticated;
grant execute on function public.delete_my_account() to authenticated;
