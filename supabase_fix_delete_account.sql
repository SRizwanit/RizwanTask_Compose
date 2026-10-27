-- অ্যাকাউন্ট মুছুন: "42501 permission denied for table app_users" ঠিক করার SQL
-- Supabase Dashboard → SQL Editor-এ একবার চালান।
alter table public.app_users enable row level security;

grant select, insert, update, delete on public.app_users to anon, authenticated;

drop policy if exists "rt_all_access" on public.app_users;
create policy "rt_all_access" on public.app_users
  for all to anon, authenticated using (true) with check (true);

notify pgrst, 'reload schema';
