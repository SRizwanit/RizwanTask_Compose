-- =====================================================================
-- একটি ডিভাইস = একটি একাউন্ট (সার্ভার-সাইড নিয়ম)
-- Supabase Dashboard → SQL Editor-এ একবার চালান। বারবার চালালেও সমস্যা নেই।
--
-- অ্যাপ এখন ডিভাইস চেনে ANDROID_ID থেকে বানানো আইডি দিয়ে (ডেটা ক্লিয়ার / রিইনস্টলেও বদলায় না)।
-- কিন্তু অ্যাপের চেক এড়িয়ে যাওয়া যায়, তাই ডাটাবেসেও "একই device_id দুই একাউন্টে নয়" নিয়ম বসানো হলো।
-- =====================================================================

-- ১) ফাঁকা / "null" লেখা device_id সত্যিকারের NULL করুন
update public.app_users
   set device_id = null
 where device_id is not null
   and (btrim(device_id) = '' or lower(btrim(device_id)) = 'null');

-- ২) এখন কোন ডিভাইসে একাধিক একাউন্ট আছে দেখুন (শুধু দেখার জন্য — ফল ফাঁকা হলে সব ঠিক)
select device_id, count(*) as accounts, array_agg(phone order by created_at) as phones
  from public.app_users
 where device_id is not null
 group by device_id
having count(*) > 1;

-- ৩) ডুপ্লিকেট থাকলে সবচেয়ে পুরনো একাউন্টটি ডিভাইসে বাঁধা থাকবে, নতুনগুলোর device_id খালি হবে
--    (ওই ইউজাররা তখন অন্য ডিভাইসে লগইন করলে বাঁধা পড়বে; এই ডিভাইসে নতুন একাউন্টগুলো আর চলবে না)
update public.app_users u
   set device_id = null
 where u.device_id is not null
   and exists (
         select 1 from public.app_users o
          where o.device_id = u.device_id
            and (o.created_at, o.id::text) < (u.created_at, u.id::text)
       );

-- ৪) ডাটাবেস-স্তরের নিয়ম: একই device_id দুটি সারিতে থাকতে পারবে না (NULL যত খুশি থাকতে পারে)
create unique index if not exists app_users_device_id_uniq
    on public.app_users (device_id)
 where device_id is not null;

-- ৫) ফাঁকা স্ট্রিং আর বসতে দেবে না
alter table public.app_users drop constraint if exists app_users_device_id_not_blank;
alter table public.app_users
  add constraint app_users_device_id_not_blank
  check (device_id is null or btrim(device_id) <> '');

notify pgrst, 'reload schema';
