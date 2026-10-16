-- Run by backend-deploy.yml after a deploy: the word content and the wallpapers are there and the tables are locked down.
select
  (select count(*)::int from public.words) as words,
  (select count(*)::int from public.book_entries where book_id = '00000000-0000-4000-8000-000000000001') as kaoyan_entries,
  (select bool_and(relrowsecurity) from pg_class
   where oid in ('public.words'::regclass, 'public.books'::regclass, 'public.book_entries'::regclass,
                 'public.review_logs'::regclass, 'public.favorites'::regclass, 'public.user_settings'::regclass,
                 'public.focus_wallpapers'::regclass)) as rls_on,
  has_table_privilege('anon', 'public.words', 'select') as anon_can_read_words,
  has_table_privilege('authenticated', 'public.review_logs', 'update') as answers_editable,
  (select count(*)::int from public.focus_wallpapers where active) as wallpapers,
  coalesce((select public from storage.buckets where id = 'wallpapers'), false) as wallpapers_bucket_public
