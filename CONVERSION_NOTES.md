# ⚠️ Supabase SQL — একমাত্র উৎস (নতুন টাস্কের আগে অবশ্যই পড়ুন)

এই repo-র **মূল (root) ফোল্ডারে** `rizwantask_full_schema.sql` নামে একটি ফাইল থাকবে। এটিই RizwanTask-এর
সম্পূর্ণ Supabase স্কিমা (টেবিল, কলাম, ইনডেক্স, RPC ফাংশন, RLS) এবং React ও Compose — দুই অ্যাপই একই ডেটাবেস ব্যবহার করে।

**নিয়ম:**
- নতুন যেকোনো টাস্ক/স্ক্রিন/ফিচার শুরু করার আগে `rizwantask_full_schema.sql` পড়ে নিন এবং **টেবিল, কলামের নাম ও টাইপ, RPC ফাংশনের নাম ও প্যারামিটার ঠিক সেই অনুযায়ী** কোড লিখুন।
- ফাইলে নেই এমন টেবিল/কলাম/ফাংশন নিজে থেকে বানাবেন না বা অনুমান করবেন না।
- স্কিমা বদলাতে হলে আগে `rizwantask_full_schema.sql` আপডেট করুন (idempotent রাখুন: `if not exists` / `create or replace`), তারপর কোড।
- id সবসময় `String` হিসেবে নিন (uuid/bigint যাই হোক)।

---

# ⚠️ কাজ ভাগ করার নিয়ম — বড় ধাপ ছোট ছোট টুকরোয় (টোকেন বাঁচাতে)

কোনো ধাপ (২, ৩, ৪) একসাথে করা যাবে না। প্রতিটি ধাপ **ছোট উপধাপে** ভাগ করে একবারে **শুধু একটি উপধাপ** করতে হবে
(যেমন: ধাপ ২-ক, ধাপ ২-খ, ধাপ ২-গ…), যাতে এ আই-এর টোকেন দ্রুত শেষ না হয় এবং প্রতিটি অংশ আলাদাভাবে যাচাই করা যায়।

**নিয়ম:**
- একবারে একটি উপধাপ: শুধু সেই উপধাপের React ফাইল পড়ুন ও Kotlin ফাইল লিখুন; পুরো প্রজেক্ট বা অপ্রাসঙ্গিক ফাইল পড়বেন না।
- উপধাপ শেষ হলে নিচের তালিকায় ✅ দিন এবং কোন ফাইল তৈরি/বদলানো হলো তা এক লাইনে লিখুন; তারপর থামুন ও ব্যবহারকারীর যাচাইয়ের অপেক্ষা করুন।
- পরের উপধাপ শুরুর আগে শুধু এই নোট, `rizwantask_full_schema.sql` এবং দরকারি `core/` ফাইল দেখুন।
- একটি উপধাপ বড় হয়ে গেলে (যেমন একাধিক বড় স্ক্রিন) আরও ছোট করুন (২-খ-১, ২-খ-২)।
- সম্পন্ন কাজ আবার লিখবেন না; আগের ফাইল দরকার হলে শুধু প্রয়োজনীয় অংশ পড়ুন।

## উপধাপ তালিকা

| উপধাপ | কাজ | React উৎস | অবস্থা |
|---|---|---|---|
| ২-ক | Support — WebView (`http://rizwantask.vercel.app`) | `Support.jsx` | ✅ |
| ২-খ | Admin কাঠামো: গার্ড, ট্যাব বার, ডেটা লোডার | `Admin.jsx`, `admin/useAdminData.js`, `admin/ui.jsx`, `admin/helpers.js` | ✅ |
| ২-গ | Admin → Users ট্যাব (ব্লক, PIN/ডিভাইস রিসেট, মুছা) | `admin/UsersTab.jsx` | ✅ |
| ২-ঘ | Admin → Settings ট্যাব (রেট, ন্যূনতম পেমেন্ট, এপি আই সুইচ) | `admin/SettingsTab.jsx` | ✅ |
| ২-ঙ | Admin → Notice ও Log ট্যাব | `admin/NoticeTab.jsx`, `admin/LogTab.jsx` | ✅ |
| ২-চ | Admin → Pay ট্যাব (পেমেন্ট পরিশোধ/বাতিল) | `admin/PayTab.jsx` | ⬜ |
| ২-ছ | Admin → Review ট্যাব (অনুমোদন/সংশোধন) | `admin/ReviewTab.jsx` | ⬜ |
| ২-জ | Admin → Books ট্যাব (কিতাব CRUD, রেট) | `admin/BooksTab.jsx` | ⬜ |
| ২-ঝ | Admin → Books: GitHub Actions / ছবি তৈরি | `admin/BooksTab.jsx`, `admin/helpers.js` | ⬜ |
| ২-ঞ | Admin → Dash ট্যাব | `admin/DashTab.jsx` | ⬜ |
| ২-ট | Task — ফাঁকা স্ক্রিন কাঠামো | `Task.jsx` (কাঠামো মাত্র) | ⬜ |
| ৩-ক | Task কাঠামো: নিচের নেভ বার (টাস্ক/পেমেন্ট/র‍্যাংক), ডেটা লোডার (নিজের প্রুফ, কিতাব, সেটিংস) | `Task.jsx`, `task/helpers.js` | ⬜ |
| ৩-খ | Task → টাস্ক ট্যাব: ড্যাশবোর্ড, কিতাবের গ্রিড, "কাজ চলছে" লক ডায়ালগ (`book_lock_of`) | `task/Dashboard.jsx`, `task/BookGrid.jsx`, `task/Dialogs.jsx` | ⬜ |
| ৩-গ | Task → পেমেন্ট ট্যাব: আয়ের হিসাব, পেমেন্ট রিকোয়েস্ট (`payments`) | `task/PayPane.jsx` | ⬜ |
| ৩-ঘ | Task → র‍্যাংক ট্যাব (`leaderboard` RPC) | `task/RankPane.jsx` | ⬜ |
| ৩-ঙ | Task → গাইড ডায়ালগ ও দৃশ্য | `task/GuideDialog.jsx`, `task/guideScenes.js`, `task/guideImages.js` | ⬜ |
| ৩-চ | Profile → কাঠামো, ইউজার তথ্য, পেমেন্ট নাম্বার এডিট | `Profile.jsx` (উপরের অংশ) | ⬜ |
| ৩-ছ | Profile → নোটিশ তালিকা, আয়ের সারসংক্ষেপ, কিতাব-অগ্রগতি (`ProgLine`) | `Profile.jsx` (বাকি অংশ) | ⬜ |
| ৪-ক | View কাঠামো: প্রবেশদ্বার (কিতাব ও সেটিংস লোড), লোকাল সংরক্ষণ, কনফিগ, টোস্ট | `View.jsx` (১–১২৭), `view/config.js`, `view/storage.js`, `view/utils.js`, `view/toaster.js`, `view/hooks.js` | ⬜ |
| ৪-খ | View → "কাজ চলছে" সংকেত (`book_lock_beat/release`) ও এপি আই চালু/বন্ধ চেক (`api_enabled`) | `View.jsx` (১২৮–১৫৮, ৯১৪ লাইনের আশপাশ) | ⬜ |
| ৪-গ | View → ছবি: GitHub থেকে লোড, ক্যাশ, লোডিং শতাংশ, লাইটবক্স, মূল ছবি ডাউনলোড | `view/github.js`, `view/ImageLoader.jsx`, `view/Sprite.jsx` | ⬜ |
| ৪-ঘ | View → পেইজ লোড/সেভ, খসড়া, শেষ অবস্থান (স্ক্রল/কার্সর) | `View.jsx` (পেইজ লোড, সেভ, খসড়া অংশ) | ⬜ |
| ৪-ঙ | View → প্রুফড মালিকানা ও রিভিউ (`page_proofs`, `my_proof_count`, সংশোধিত পেইজ পুনরায় জমা) | `View.jsx` (প্রুফড মালিকানা অংশ) | ⬜ |
| ৪-চ | View → এডিটর UI: কপি/পেস্ট, অটো হেডিং, কিবোর্ড শর্টকাট, হাইলাইট, ড্রপডাউন, রিপল | `view/Highlight.jsx`, `view/Dropdown.jsx`, `view/useRipple.js`, `View.jsx` (বাটন/শর্টকাট) | ⬜ |
| ৪-ছ | View → Gemini API (এপি আই-১ ছবি→টেক্সট, এপি আই-২ প্রুফ, অটো OCR টগল) | `view/gemini.js`, `view/ApiDialogs.jsx` | ⬜ |
| ৪-জ | View → TTS ১: ফোনের নিজস্ব TTS ইঞ্জিন, পড়ার সময় হাইলাইট | `view/TtsController.js`, `view/ttsEngines.js` (ফোনের অংশ) | ⬜ |
| ৪-ঝ | View → TTS ২: Google অনলাইন অডিও, অতিরিক্ত সার্ভিস, সার্ভিস বাছাই ডায়ালগ | `view/ttsEngines.js`, `view/TtsDialog.jsx` | ⬜ |
| ৪-ঞ | View → সেটিংস ডায়ালগ, Google Lens, শেষ জোড়া লাগানো ও পুরো পেইজ যাচাই | `view/SettingsDialog.jsx`, `View.jsx` (Lens অংশ) | ⬜ |

> **View-এর নোট:** React repo-তে `View.jsx` (১২৭৪ লাইন) ও `View.logic.js` (২২৮৪ লাইন) — দুটোতেই একই ধরনের অংশ আছে। কাজ শুরুর আগে নিশ্চিত হোন কোনটি বর্তমানে ব্যবহৃত (`View.jsx` কোনটি import করে) এবং শুধু সেটি থেকে অনুবাদ করুন; একই লজিক দুইবার লিখবেন না।
> উপধাপের "লাইন/অংশ" উল্লেখ আনুমানিক — কাজ শুরুর সময় ফাইলে সেই অংশ খুঁজে নিন। কোনো উপধাপ বড় হলে আরও ভাঙুন (যেমন ৪-ঘ-১, ৪-ঘ-২)।

---

# React → Jetpack Compose conversion (RizwanTask)

| React (`rizwantask_react/src`) | Compose (`app/src/main/kotlin/com/srizwan/task`) | Step |
|---|---|---|
| `lib/common.js` Supabase `req/sb`, `getAll` | `core/Sb.kt` (OkHttp, same URLs/headers/errors) | 1 ✅ |
| `Net` (offline / slow bar) | `core/Net.kt` + `ui/Overlays.kt → NetBar` | 1 ✅ |
| `Phone`, PIN hash, device id, OTP pool | `core/Phone.kt`, `Pin.kt`, `Device.kt`, `Otp.kt` | 1 ✅ |
| users / session / `requireAuth` / blocked screen / `log` | `core/Users.kt`, `core/Session.kt` (`Session`, `Auth`, `ActivityLog`), `ui/Overlays.kt → BlockedScreen` | 1 ✅ |
| `bn/money/fmtD/fmtDT`, `rateOf/summarize` | `core/Fmt.kt`, `core/Earnings.kt` | 1 ✅ |
| `theme` (auto/light/dark) + ThemeToggle | `core/ThemeStore.kt`, `Theme.kt`, `ui/Components.kt → ThemeToggleButton` | 1 ✅ |
| `ICONS` + `LOGO` | `ui/IconData.kt` (generated from common.js), `ui/AppIcon.kt` | 1 ✅ |
| `nav.js`, `App.jsx` routes | `nav/Nav.kt` (`Routes`, `Nav.go("task#guide")`, `AppNav`) | 1 ✅ |
| `Splash.jsx` | `screens/SplashScreen.kt` (existing design kept; tap = skip) | 1 ✅ |
| `Login.jsx` | `screens/LoginViewModel.kt` + `LoginScreen.kt` | 1 ✅ |
| `Signup.jsx` | `screens/SignupScreen.kt` | 1 ✅ |
| `Support.jsx` | WebView → `http://rizwantask.vercel.app` | 2 |
| `Admin.jsx` + `admin/*` | next | 2 |
| `Task.jsx` (blank for now) → full | `screens/Placeholders.kt → TaskScreen` | 2 blank / 3 full |
| `Profile.jsx` | next | 3 |
| `View.jsx` + `view/*` (proofing, Gemini, TTS) | next | 4 |

## Configuration (React `.env` → BuildConfig)
Set `SUPABASE_URL`, `SUPABASE_KEY`, `ADMIN_PHONE`, `ADMIN_WA` as environment variables,
in `local.properties` (see `local.properties.example`), or as GitHub **Secrets** with the same names
(the two build workflows already pass them to Gradle).

## Not converted on purpose
PWA install prompt (`Install`), service worker, `runLegacy`, `PageShell` (browser `<head>` meta) — they have no meaning in a native app.
