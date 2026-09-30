-- Tables, row-level security and privileges (docs/vocabulary-spec.md §5).
begin;
select plan(21);

select has_table('public', 'words', 'words exists');
select has_table('public', 'books', 'books exists');
select has_table('public', 'book_entries', 'book_entries exists');
select has_table('public', 'review_logs', 'review_logs exists');
select has_table('public', 'favorites', 'favorites exists');
select has_table('public', 'user_settings', 'user_settings exists');

select ok(
  (select bool_and(c.relrowsecurity) from pg_class c
   where c.oid in ('public.words'::regclass, 'public.books'::regclass, 'public.book_entries'::regclass,
                   'public.review_logs'::regclass, 'public.favorites'::regclass, 'public.user_settings'::regclass)),
  'row-level security is on for every table'
);

-- Anonymous clients (the publishable key without a signed-in user) get nothing.
select table_privs_are('public', 'words', 'anon', array[]::text[], 'anon: no access to words');
select table_privs_are('public', 'books', 'anon', array[]::text[], 'anon: no access to books');
select table_privs_are('public', 'book_entries', 'anon', array[]::text[], 'anon: no access to book_entries');
select table_privs_are('public', 'review_logs', 'anon', array[]::text[], 'anon: no access to review_logs');
select table_privs_are('public', 'favorites', 'anon', array[]::text[], 'anon: no access to favorites');
select table_privs_are('public', 'user_settings', 'anon', array[]::text[], 'anon: no access to user_settings');

-- Signed-in users: read words, never delete, never change answers.
select table_privs_are('public', 'words', 'authenticated', array['SELECT'], 'signed in: words are read-only');
select table_privs_are('public', 'books', 'authenticated', array['SELECT', 'INSERT', 'UPDATE'], 'signed in: books');
select table_privs_are('public', 'book_entries', 'authenticated', array['SELECT', 'INSERT', 'UPDATE'], 'signed in: book_entries');
select table_privs_are('public', 'review_logs', 'authenticated', array['SELECT', 'INSERT'], 'signed in: answers are append-only');
select table_privs_are('public', 'favorites', 'authenticated', array['SELECT', 'INSERT', 'UPDATE'], 'signed in: favorites');
select table_privs_are('public', 'user_settings', 'authenticated', array['SELECT', 'INSERT', 'UPDATE'], 'signed in: user_settings');

select function_privs_are('public', 'delete_my_account', array[]::text[], 'anon', array[]::text[],
  'anon cannot call delete_my_account');
select function_privs_are('public', 'delete_my_account', array[]::text[], 'authenticated', array['EXECUTE'],
  'signed-in users can call delete_my_account');

select * from finish();
rollback;
