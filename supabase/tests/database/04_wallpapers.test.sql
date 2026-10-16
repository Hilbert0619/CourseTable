-- Focus wallpapers: anyone reads the active rows, nobody writes through the API.
begin;
select plan(11);

select has_table('public', 'focus_wallpapers', 'focus_wallpapers exists');
select ok((select relrowsecurity from pg_class where oid = 'public.focus_wallpapers'::regclass),
  'row-level security is on for focus_wallpapers');
select table_privs_are('public', 'focus_wallpapers', 'anon', array['SELECT'], 'anon: wallpapers are read-only');
select table_privs_are('public', 'focus_wallpapers', 'authenticated', array['SELECT'], 'signed in: wallpapers are read-only');

select is((select count(*)::int from public.focus_wallpapers where active), 30, 'the 30 Unsplash wallpapers are there');
select ok((select bool_and(url like 'https://%/storage/v1/object/public/wallpapers/' || id || '.jpg') from public.focus_wallpapers),
  'each points at its file in the wallpapers bucket');

update public.focus_wallpapers set active = false where id = 'moon-sea';

set local role anon;
set local request.jwt.claims to '{"role": "anon"}';
select is((select count(*)::int from public.focus_wallpapers), 29, 'anonymous clients read the active wallpapers only');
select throws_ok($$ insert into public.focus_wallpapers (id, url, width, height) values ('x', 'https://example.com/x.jpg', 1, 1) $$,
  '42501', null, 'anonymous clients cannot add wallpapers');
reset role;

set local role authenticated;
set local request.jwt.claims to '{"sub": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", "role": "authenticated"}';
select is((select count(*)::int from public.focus_wallpapers), 29, 'signed-in users read the active wallpapers');
select throws_ok($$ update public.focus_wallpapers set url = 'https://example.com/x.jpg' $$,
  '42501', null, 'signed-in users cannot change wallpapers');
select throws_ok($$ delete from public.focus_wallpapers $$, '42501', null, 'signed-in users cannot delete wallpapers');
reset role;

select * from finish();
rollback;
