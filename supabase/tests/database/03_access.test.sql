-- What each kind of client can do, acting as the API would: anonymous, learner A, learner B.
begin;
select plan(29);

insert into auth.users (id, email) values
  ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'a@example.test'),
  ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 'b@example.test');

-- Anonymous --------------------------------------------------------------------------------------
set local role anon;
set local request.jwt.claims to '{"role": "anon"}';

select throws_ok('select count(*) from public.words', '42501', null, 'anonymous clients cannot read words');
select throws_ok('select public.delete_my_account()', '42501', null, 'anonymous clients cannot delete accounts');

reset role;

-- Learner A --------------------------------------------------------------------------------------
set local role authenticated;
set local request.jwt.claims to '{"sub": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", "role": "authenticated"}';

select is((select count(*)::int from public.words), 4801, 'signed-in learners read the words');
select is((select count(*)::int from public.books where owner_id is null), 1, 'signed-in learners see the system book');

select lives_ok($$
  insert into public.review_logs (id, headword, rating, mode, reviewed_at, study_day, duration_ms, received_at)
  values ('10000000-0000-4000-8000-000000000001', 'abandon', 3, 'choice', '2026-09-30 10:00+00', 20361, 4000, '2000-01-01+00')
$$, 'a learner uploads an answer');
select ok((select received_at > '2026-01-01' from public.review_logs where id = '10000000-0000-4000-8000-000000000001'),
  'the server stamps the upload time, whatever the device sent');
select throws_ok($$
  insert into public.review_logs (id, user_id, headword, rating, mode, reviewed_at, study_day)
  values ('10000000-0000-4000-8000-000000000002', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 'abandon', 3, 'choice', now(), 20361)
$$, '42501', null, 'nobody uploads answers for someone else');
select throws_ok('update public.review_logs set rating = 4', '42501', null, 'answers cannot be changed');
select throws_ok('delete from public.review_logs', '42501', null, 'answers cannot be deleted');
select throws_ok($$ insert into public.words (headword, display, translation_zh) values ('zzz', 'zzz', 'n. z') $$,
  '42501', null, 'learners cannot add dictionary words');

update public.books set name = 'changed' where owner_id is null;
select is((select name from public.books where id = '00000000-0000-4000-8000-000000000001'), '考研词汇 (ECDICT)',
  'learners cannot rename the system book');
select throws_ok($$
  insert into public.book_entries (book_id, position, headword) values ('00000000-0000-4000-8000-000000000001', 9999, 'zzz')
$$, '42501', null, 'learners cannot add words to the system book');

select lives_ok($$
  insert into public.books (id, owner_id, name) values ('20000000-0000-4000-8000-000000000001', 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'My list')
$$, 'a learner creates a book');
select lives_ok($$
  insert into public.book_entries (book_id, position, headword, meaning) values ('20000000-0000-4000-8000-000000000001', 1, 'tenacious', 'adj. 顽强的')
$$, 'and adds a word to it');

select lives_ok($$ insert into public.favorites (headword, is_favorite, updated_at) values ('abandon', true, '2026-09-30 12:00+00') $$,
  'a learner marks a favourite');
update public.favorites set is_favorite = false, updated_at = '2026-09-30 11:00+00' where headword = 'abandon';
select is((select is_favorite from public.favorites where headword = 'abandon'), true,
  'an older edit does not overwrite a newer one');
select lives_ok($$
  insert into public.user_settings (book_id, daily_new_words, updated_at) values ('00000000-0000-4000-8000-000000000001', 30, now())
$$, 'a learner saves settings');

reset role;

-- Learner B --------------------------------------------------------------------------------------
set local role authenticated;
set local request.jwt.claims to '{"sub": "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb", "role": "authenticated"}';

select is((select count(*)::int from public.review_logs), 0, 'another learner''s answers are invisible');
select is((select count(*)::int from public.books where owner_id is not null), 0, 'another learner''s books are invisible');
select is((select count(*)::int from public.book_entries where book_id = '20000000-0000-4000-8000-000000000001'), 0,
  'and so are the words in them');
update public.favorites set is_favorite = false, updated_at = now();
select throws_ok($$
  insert into public.book_entries (book_id, position, headword) values ('20000000-0000-4000-8000-000000000001', 2, 'zzz')
$$, '42501', null, 'nobody adds words to someone else''s book');
select throws_ok($$
  insert into public.user_settings (book_id, updated_at) values ('20000000-0000-4000-8000-000000000001', now())
$$, '42501', null, 'settings cannot point at someone else''s book');

reset role;
select is((select is_favorite from public.favorites where user_id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'), true,
  'another learner cannot change a favourite');

-- Account deletion -------------------------------------------------------------------------------
set local role authenticated;
set local request.jwt.claims to '{"sub": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", "role": "authenticated"}';

select lives_ok('select public.delete_my_account()', 'a learner deletes their account');

reset role;
select is((select count(*)::int from auth.users where id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'), 0, 'the account is gone');
select is((select count(*)::int from public.review_logs where user_id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'), 0,
  'with all of its answers');
select is(
  (select count(*)::int from public.books where owner_id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa')
  + (select count(*)::int from public.book_entries where book_id = '20000000-0000-4000-8000-000000000001')
  + (select count(*)::int from public.favorites where user_id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa')
  + (select count(*)::int from public.user_settings where user_id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'),
  0, 'and its books, words, favourites and settings');
select is((select count(*)::int from auth.users where id = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'), 1, 'other accounts stay');
select is((select count(*)::int from public.words), 4801, 'shared words stay');

select * from finish();
rollback;
