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

**টোকেন বাঁচানোর অতিরিক্ত নিয়ম (সব উপধাপে প্রযোজ্য):**
- এই নোটের পুরোটা পড়বেন না — শুধু "কাজ ভাগ করার নিয়ম", "Supabase SQL" অংশ আর নিজের উপধাপের সারিটি পড়ুন। শেষ হলে পুরো ফাইল আবার না লিখে `str_replace` দিয়ে শুধু ওই সারির `⬜` বদলে ✅ ও এক লাইনের বিবরণ বসান।
- React উৎস ফাইল পুরো পড়বেন না: সারিতে দেওয়া লাইন-রেঞ্জ ধরে শুধু সেটুকু পড়ুন (`sed -n 'ক,খp'` বা `view_range`)। রেঞ্জ আনুমানিক — ±২০ লাইন ধরে নিন।
- `rizwantask_full_schema.sql` শুধু সেই উপধাপে পড়বেন যেখানে `[স্কিমা]` চিহ্ন আছে; তখনও পুরোটা নয়, শুধু সংশ্লিষ্ট টেবিল/RPC `grep` করে দেখুন।
- বড় Kotlin ফাইল (`BooksTab.kt` ~৩০ KB, `BookDialogs.kt` ~২৬ KB, `AdminUi.kt`, `AdminScreen.kt`) পুরো আবার লিখবেন না — `str_replace` দিয়ে ছোট বদল করুন; নতুন লজিক নতুন ফাইলে রাখুন।
- `task/guideImages.js` (~৩৯ KB) শুধু ছবির base64 ডেটা — কখনো খুলে পড়বেন না (দেখুন ৩-ঙ-১)।
- `view/View.logic.js` (১৬৪ KB) আর ব্যবহৃত হয় না — কখনো পড়বেন না।
- একটি উপধাপে একটির বেশি ফিচার বা ~৮ KB-এর বেশি React উৎস লাগলে থেমে আরও ভাঙুন (যেমন ৩-ক-২-ক, ৩-ক-২-খ) এবং এই তালিকায় নতুন সারি যোগ করুন।
- `[স্কিমা]` = এই উপধাপে Supabase টেবিল/RPC লাগে।

## উপধাপ তালিকা

| উপধাপ | কাজ | React উৎস | অবস্থা |
|---|---|---|---|
| ২-ক | Support — WebView (`http://rizwantask.vercel.app`) | `Support.jsx` | ✅ |
| ২-খ | Admin কাঠামো: গার্ড, ট্যাব বার, ডেটা লোডার | `Admin.jsx`, `admin/useAdminData.js`, `admin/ui.jsx`, `admin/helpers.js` | ✅ |
| ২-গ | Admin → Users ট্যাব (ব্লক, PIN/ডিভাইস রিসেট, মুছা) | `admin/UsersTab.jsx` | ✅ |
| ২-ঘ | Admin → Settings ট্যাব (রেট, ন্যূনতম পেমেন্ট, এপি আই সুইচ) | `admin/SettingsTab.jsx` | ✅ |
| ২-ঙ | Admin → Notice ও Log ট্যাব | `admin/NoticeTab.jsx`, `admin/LogTab.jsx` | ✅ |
| ২-চ | Admin → Pay ট্যাব (পেমেন্ট পরিশোধ/বাতিল) | `admin/PayTab.jsx` | ✅ `screens/admin/PayTab.kt` (নতুন); `AdminUi.kt` (SmBtn-এ `enabled`/`compact`, AskDialog-এ লেবেল ফিল্ডের উপরে), `AdminViewModel.kt` (`payFilter`), `AdminScreen.kt` (Pay রুট), `core/Intents.kt` (`copyText`) |
| ২-ছ | Admin → Review ট্যাব (অনুমোদন/সংশোধন) | `admin/ReviewTab.jsx` | ✅ `screens/admin/ReviewTab.kt` (নতুন); `AdminUi.kt` (SmBtn-এ "ok" টোন), `AdminScreen.kt` (Review রুট), `PayTab.kt` (বোতামে "ok" টোন) |
| ২-জ-১ | Admin → Books: তালিকা, সার্চ/ফিল্টার/সাজানো, অগ্রগতি, লুকান/দেখান, ডিলিট, সিলেক্ট মোড (বাল্ক লুকান/দেখান/রেট/ডিলিট) | `admin/BooksTab.jsx` | ✅ `screens/admin/BooksTab.kt` (নতুন); `AdminUi.kt` (SmBtn "primary" টোন), `AdminViewModel.kt` (`booksFilter`), `AdminScreen.kt` (Books রুট) |
| ২-জ-২ | Admin → Books: নতুন/এডিট ডায়ালগ (নাম, লেখক, কভার, পিডিএফ, রেট, মোট পেইজ, GitHub ফিল্ড, ফোল্ডার প্রিফিক্স) + JSON ইমপোর্ট (`parseImport`, ব্যাচ সংরক্ষণ) | `admin/BooksTab.jsx` (`blank/fromBook/save`, `parseImport/postBatch/doImport`) | ✅ `screens/admin/BookDialogs.kt` (নতুন: `BookDialog`, `ImportDialog`, `parseImport`, `postBatch`); `BooksTab.kt` ("নতুন কিতাব" / "JSON ইমপোর্ট" / "এডিট" বোতাম) |
| ২-ঝ-১ | Admin → Books: GitHub সহায়ক (`gh`, `ghErr`, `countPages`, `dispatchBook`, `fetchRunState`) + পেইজ গণনা (কার্ড "পেইজ গণনা", "সব গণনা", বাল্ক গণনা, ডায়ালগের "গুনুন", সংরক্ষণ/ইমপোর্টের পর অটো গণনা) | `admin/helpers.js`, `admin/BooksTab.jsx` | ✅ `screens/admin/GitHubApi.kt` (নতুন); `BooksTab.kt`, `BookDialogs.kt` |
| ২-ঝ-২-ক | Admin → Books: রান-অবস্থার মডেল — `runs` ম্যাপ, `runsLS` পড়া/লেখা, `isActiveRun`; ছোট `RunBox` কম্পোজেবল (এখনো কার্ডে বসবে না) → নতুন `RunStore.kt`, `RunBox.kt` | `admin/BooksTab.jsx` (১৩–৩৭, ৮৫–৯২), `admin/helpers.js` (৪৬–৪৯, ১২৮) | ✅ `screens/admin/RunStore.kt` (নতুন: `readRunsLS/writeRunsLS`, `RunStore.runs/getRun/setRun/saveLocal/clearLocal`), `screens/admin/RunBox.kt` (নতুন: `RunBox`, ✔/✖ এর বদলে ভেক্টর আইকন; কার্ডে এখনো বসেনি; `isActiveRun` আগে থেকেই `GitHubApi.kt`-এ) |
| ২-ঝ-২-খ | Admin → Books: `startRun` + `saveRun` (ViewModel-এ) ও কার্ডের "ছবি তৈরি"/"আবার চেষ্টা" বোতাম; কার্ডে `RunBox` বসানো | `admin/BooksTab.jsx` (১১২–১১৭, ১৬৩–১৭৪, ৫২৪–৫৩০) | ✅ `AdminViewModel.kt` (`saveRun`, `startRun`, `schedulePoll` হুক — আসল পোলিং ২-ঝ-২-গ-তে), `AdminHelpers.kt` (`getSplitPref/setSplitPref`), `BooksTab.kt` (কার্ডে `RunBox`, "ছবি তৈরি" ও "আবার চেষ্টা" বোতাম, `runOne`; `running/done/failed` এখন `RunStore` থেকে; "লুকানো"/"Task পেইজে দেখাচ্ছে না" লেবেলের ইমোজি বদলে ভেক্টর আইকন), `RunBox.kt` (মন্তব্য) |
| ২-ঝ-২-গ | Admin → Books: স্ট্যাটাস পোলিং (`schedulePoll`, `pollRuns`, ট্যাবে ফিরলে `visibilitychange`) + রান সফল হলে অটো গণনা (`autoCount` জোড়া) | `admin/BooksTab.jsx` (১০০–১৬০, ১১৮–১২৯) | ✅ `screens/admin/RunPoller.kt` (নতুন: `schedule`, `pollNow`, `poll`, `autoDone` জোড়া, রান সফল হলে অটো গণনা); `AdminViewModel.kt` (`poller`, `reloadBooks`, `start()`-এ ৮ সেকেন্ডে পোলিং শুরু, `onCleared`-এ বন্ধ); `BooksTab.kt` (ট্যাব খুললে/অ্যাপ ফিরলে সাথে সাথে পোল — `visibilitychange`-এর বদলে Lifecycle; রিফ্রেশ/ডিলিটের পর `reloadBooks`); `BookDialogs.kt` (`loadBooks` → `reloadBooks`) |
| ২-ঝ-২-ঘ | Admin → Books: ফিল্টার running/done/failed (`FLT`, `fcount`, `matchF`) | `admin/BooksTab.jsx` (৩৫, ৪৪০–৪৫০) | ✅ `BooksTab.kt` — `FLT`, `matchF`, `fcount`, `st()`-এর running/done/failed আগের ধাপগুলোতেই React-এর সাথে হুবহু মিলিয়ে বসানো ছিল; যাচাই করে মিলেছে, ২-ঝ-২-গ-র `RunPoller` থেকে `RunStore.runs` ভরলে চিপের সংখ্যা ও তালিকা নিজে থেকে বদলায়; শুধু মন্তব্য হালনাগাদ |
| ২-ঝ-২-ঙ | Admin → Books: "ব্যর্থ সব আবার চেষ্টা" (`bRetryAll`) | `admin/BooksTab.jsx` (৪১৫–৪৩৫, ৪৮৪) | ✅ `BooksTab.kt` — "ব্যর্থ / বাতিল" ফিল্টারে "সব ব্যর্থ আবার চেষ্টা" বোতাম (পিডিএফ লিংকওয়ালা দেখানো কিতাব), `openRunDlg(list)` ও `runOpen/runList/runSplit/runErr` স্টেট; ডায়ালগ UI ও `doBulkRun` ২-ঝ-২-চ-তে (তার আগে বোতাম চাপলে ডায়ালগ খুলবে না) |
| ২-ঝ-২-চ | Admin → Books: বাল্ক ছবি তৈরি ডায়ালগ (`openRunDlg`, `doBulkRun`, `bRunDlg`) ও সিলেক্ট বারের "ছবি তৈরি" বোতাম | `admin/BooksTab.jsx` (৩৮০–৪০০, ৪৯৫, ৬১৫–৬২৫) | ✅ `BooksTab.kt` — `doBulkRun`, বাল্ক ছবি তৈরি ডায়ালগ (কিতাবের তালিকা, split চেকবক্স, ত্রুটি বার্তা, `setSplitPref`), সিলেক্ট বারে "ছবি তৈরি" বোতাম; ২-ঝ-২-ঙ-র "সব ব্যর্থ আবার চেষ্টা" এখন এই ডায়ালগ খোলে |
| ২-ঝ-২-ছ | Admin → Books: নতুন/এডিট ডায়ালগের run/split চেকবক্স + `save`-এর পর রান চালু (`BookDialogs.kt`-এ ছোট বদল) | `admin/BooksTab.jsx` (২৭৫–৩০৫) | ✅ `BookDialogs.kt` — নতুন/এডিট ডায়ালগে run/split চেকবক্স ও আউটপুট ফোল্ডারের হিন্ট, `save`-এ আগে যাচাই (পিডিএফ লিংক/একটাই লিংক/টোকেন), নতুন কিতাবে run হলে `total_pages` ফাঁকা, `setSplitPref`, সংরক্ষণের পর `vm.startRun`, run না হলে আগের মতো অটো গণনা |
| ২-ঝ-২-জ | Admin → Books: ইমপোর্টের run/split অপশন (`doImport`-এ `startRun`) + ডিলিটে `runsLS` সাফ (একক ও বাল্ক) | `admin/BooksTab.jsx` (২১৭–২৫৮, ৫৯১–৫৯২, ৩১০–৩২৭) | ⬜ |
| ২-ঞ-১ | Admin → Dash: ডেটা লোড ও হিসাব (`load`, `useMemo`) — শুধু ViewModel/লজিক, UI নয় [স্কিমা] | `admin/DashTab.jsx` (১–৯৯) | ⬜ |
| ২-ঞ-২ | Admin → Dash: প্রথম দুটি কার্ডের UI | `admin/DashTab.jsx` (১০০–১৩৪) | ⬜ |
| ২-ঞ-৩ | Admin → Dash: শেষ কার্ড + `onReviewUser` সংযোগ; `AdminScreen.kt`-এ Dash রুট | `admin/DashTab.jsx` (১৩৫–১৫৮) | ⬜ |
| ২-ট | Task — ফাঁকা স্ক্রিন কাঠামো | `Task.jsx` (কাঠামো মাত্র, ভেতরের লজিক নয়) | ⬜ |
| ৩-ক-১ | Task: নিচের নেভ বার (টাস্ক/পেমেন্ট/র‍্যাংক), উপরের হেডার, ট্যাব বদল (`switchTab`, `repin`) | `Task.jsx` (২০০–২০৫, ৩১১–৩২৫, ৩৪৭–৩৫২), `task/helpers.js` | ⬜ |
| ৩-ক-২ | Task: ডেটা লোডার — নিজের প্রুফ, কিতাব, সেটিংস (`fetchMine`, `loadCount/loadMoney/loadCounts/load`) [স্কিমা] | `Task.jsx` (২৫–৬৫, ৭৬–১৩৯) | ⬜ |
| ৩-খ-১ | Task → টাস্ক ট্যাব: ড্যাশবোর্ড + আয়ের লক্ষ্য (`openGoal/saveGoal`, `GoalDialog`) | `task/Dashboard.jsx`, `task/Dialogs.jsx` (GoalDialog), `Task.jsx` (২৪০–২৪৭) | ⬜ |
| ৩-খ-২ | Task → টাস্ক ট্যাব: কিতাবের গ্রিড/সার্চ + কিতাব খোলা ও "কাজ চলছে" লক ডায়ালগ (`book_lock_of`) [স্কিমা] | `task/BookGrid.jsx`, `Task.jsx` (২৪৮–২৭৭) | ⬜ |
| ৩-খ-৩ | Task → নোটিশ লোড ও দেখানো (`loadNotices`, `NoticeDialog`, `onNoticeClosed`) + লগআউট [স্কিমা] | `task/Dialogs.jsx` (NoticeDialog), `Task.jsx` (১৭৫–১৯৯, ৩০৫) | ⬜ |
| ৩-গ-১ | Task → পেমেন্ট ট্যাব: আয়ের হিসাব (`Skeleton`, `SegBar`, `Forecast`) | `task/PayPane.jsx` (১–৬৩) | ⬜ |
| ৩-গ-২ | Task → পেমেন্ট ট্যাব: পেমেন্ট তালিকা ও রিকোয়েস্ট (`onRequest`, `onSend`, `RequestDialog`) [স্কিমা] | `task/PayPane.jsx` (৬৪–১৪৪), `task/Dialogs.jsx` (RequestDialog), `Task.jsx` (২৭৮–৩০৪) | ⬜ |
| ৩-ঘ | Task → র‍্যাংক ট্যাব (`leaderboard` RPC, সময়-পর্ব বাছাই) [স্কিমা] | `task/RankPane.jsx`, `Task.jsx` (১৪০–১৭৪, ২০৫) | ⬜ |
| ৩-ঙ-১ | Task → গাইড: ছবি-ডেটা — `guideImages.js` **পড়বেন না**; শুধু `IMG` কী-নাম তালিকা (`grep -o`) নিয়ে প্রতিটি base64 WebP ছবি আলাদা `res/drawable` ফাইলে নামিয়ে বসান (স্ক্রিপ্ট দিয়ে) | `task/guideImages.js` (শুধু ডেটা) | ⬜ |
| ৩-ঙ-২ | Task → গাইড: অ্যানিমেশন ইঞ্জিন (`interpolate`, `spring`, `E`, `playScene`, `stopAnim`) ও `GUIDE` তালিকা + `GuideDialog` | `task/guideScenes.js` (৯–৩১, ২৫৭–৩০০), `task/GuideDialog.jsx` | ⬜ |
| ৩-ঙ-৩ | Task → গাইড: `shotScene`/`touch` সহায়ক + দৃশ্য ১ `sceneWelcome` | `task/guideScenes.js` (৩২–১০৯) | ⬜ |
| ৩-ঙ-৪ | Task → গাইড: দৃশ্য ২–৩ — `sceneBook`, `sceneEarn` | `task/guideScenes.js` (১১০–১৮৮) | ⬜ |
| ৩-ঙ-৫ | Task → গাইড: দৃশ্য ৪–৫ — `sceneGoal`, `sceneCommunity` | `task/guideScenes.js` (১৮৯–২৫৬) | ⬜ |
| ৩-চ-১ | Profile: কাঠামো, ইউজার তথ্য কার্ড, থিম/লগআউট/হোয়াটসঅ্যাপ বোতাম (PWA `onInstall` বাদ) [স্কিমা] | `Profile.jsx` (১–১৫০, ১৮৭–১৯৩) | ⬜ |
| ৩-চ-২ | Profile: পেমেন্ট নাম্বার এডিট (`openEdit`, `saveEdit`) [স্কিমা] | `Profile.jsx` (১৯৪–২০৯) | ⬜ |
| ৩-ছ-১ | Profile: নোটিশ তালিকা — "দেখা" মনে রাখা (`seenSet`, `unseenCount`), `openNotices`, `onNoticesBtn`, `onNoticeClose` [স্কিমা] | `Profile.jsx` (৫২–৫৫, ২১০–২২০) | ⬜ |
| ৩-ছ-২ | Profile: আয়ের সারসংক্ষেপ (`useEffect` লোড ও সংশ্লিষ্ট UI) [স্কিমা] | `Profile.jsx` (১৬১–১৮৬ ও আয়ের JSX অংশ) | ⬜ |
| ৩-ছ-৩ | Profile: কিতাব-অগ্রগতি `ProgLine` | `Profile.jsx` (১৫–৫০) | ⬜ |
| ৪-ক-১ | View: কনফিগ — `loadCfg`, `saveCfgTo`, `keysFor`, `draftKey`, `repoPaths`, `API_DEF/API_NAME` → নতুন `core/ViewConfig.kt` | `view/config.js` (৯.৭ KB) | ⬜ |
| ৪-ক-২ | View: লোকাল সংরক্ষণ (`storage.js` → SharedPreferences/DataStore; `mirror`, `restoreFromExt`, `requestPersist` ব্রাউজার-নির্ভর, বাদ/সরল) + `utils.js` (`markHeadings` বাদে — ওটা ৪-চ-২-তে) | `view/storage.js`, `view/utils.js` | ⬜ |
| ৪-ক-৩ | View: টোস্ট (`toaster.js`, `Toasts.jsx`) — অ্যাপের `core/Toast.kt` দিয়ে মেলান; `hooks.js` (`useEvent` ইত্যাদি) Compose-এ লাগে না, শুধু নোট | `view/toaster.js`, `view/Toasts.jsx`, `view/hooks.js` | ⬜ |
| ৪-ক-৪ | View: প্রবেশদ্বার — কিতাব ও সেটিংস লোড, `setCfg`, রুট/আর্গুমেন্ট (`wantPage`) | `View.jsx` (১১৬–১৩০, ১৬৩–১৭৪) | ⬜ |
| ৪-খ-১ | View → "কাজ চলছে" সংকেত (`book_lock_beat/release`) [স্কিমা] | `View.jsx` (১২৮–১৫৮) | ⬜ |
| ৪-খ-২ | View → এপি আই চালু/বন্ধ চেক (`api_enabled`, `refreshApiFlag`) [স্কিমা] | `View.jsx` (২০৭, ৯১২–৯২৪) | ⬜ |
| ৪-গ-১ | View → ছবি: GitHub থেকে লোড + ছবির ক্যাশ (`createGithub`, `createImageCache`, `imgProg`) | `view/github.js`, `View.jsx` (২৩১–২৩৮) | ⬜ |
| ৪-গ-২ | View → ছবি: লোডিং শতাংশ (`ImageLoader`) ও আইকন-স্প্রাইট (`Sprite` — `ui/IconData.kt`-এর সাথে মেলান) | `view/ImageLoader.jsx`, `view/Sprite.jsx` | ⬜ |
| ৪-গ-৩ | View → ছবি: লাইটবক্স ও মূল ছবি ডাউনলোড (`closeLightbox`, `downloadCurrent`) | `View.jsx` (৩৩০–৩৬১) | ⬜ |
| ৪-ঘ-১ | View → পেইজ লোড ও বদল (`loadDone`, `loadPage`, `goPage`) [স্কিমা] | `View.jsx` (৩০১–৩২৯, ৬৩৬–৭০৮) | ⬜ |
| ৪-ঘ-২ | View → পেইজ সেভ (`saveText`) | `View.jsx` (৫৭৬–৬০৬) | ⬜ |
| ৪-ঘ-৩ | View → খসড়া (`persistDrafts`, `stashDraft`) | `View.jsx` (১৭৫–২৩০, ২৭৮–২৮৯) | ⬜ |
| ৪-ঘ-৪ | View → শেষ অবস্থান (স্ক্রল/কার্সর): `saveView`, `lazyView`, `flush` + পেইজ ছাড়ার সময় সংরক্ষণ | `View.jsx` (২৯০–২৯৯, ১০৯৪–১০৯৯) | ⬜ |
| ৪-ঘ-৫ | View → `init` (পেইজ খোলার ধাপ, ~১২৫ লাইন) — ৪-ঘ-১…৪ শেষ হওয়ার পর জোড়া | `View.jsx` (৭১৫–৮৪০) | ⬜ |
| ৪-ঙ-১ | View → প্রুফড মালিকানা: লোড/রিফ্রেশ (`fetchProofRows`, `refreshProofs`, `ownerOk`, `my_proof_count`) [স্কিমা] | `View.jsx` (৩০৭–৩১২, ৪৩৪–৪৮০) | ⬜ |
| ৪-ঙ-২ | View → প্রুফড মালিকানা: প্রয়োগ ও "টেবিল নেই" বার্তা (`showNone`, `applyProofs`) [স্কিমা] | `View.jsx` (৪৮১–৫০০, ৭৭৭) | ⬜ |
| ৪-ঙ-৩ | View → প্রুফড মালিকানা: `syncProofJson`, `setProofedFlag`, `toggleProofClick` [স্কিমা] | `View.jsx` (৫০১–৫৭৫, ১০৫৩–১০৮০) | ⬜ |
| ৪-ঙ-৪ | View → "প্রুফ করে পরেরটায়" ও সংশোধিত পেইজ পুনরায় জমা (`proofAndNext`) [স্কিমা] | `View.jsx` (১০১৭–১০৫২) | ⬜ |
| ৪-চ-১ | View → এডিটর: কপি/পেস্ট (`pasteClick`, `copyClick`, `aiCopy`) | `View.jsx` (৩৯৮–৪০৭, ৮৪১–৮৬৯) | ⬜ |
| ৪-চ-২ | View → এডিটর: অটো হেডিং (`markHeadings` + `autoHead` টগল ও বাটন) | `view/utils.js` (`markHeadings`), `View.jsx` (৮২২, ৯০০, ১০১২, ১১৭৬–১১৭৮) | ⬜ |
| ৪-চ-৩ | View → এডিটর: কিবোর্ড শর্টকাট (Ctrl+S ইত্যাদি `onKey`) | `View.jsx` (১০৮১–১০৮৮, ১১০০–১১৩৭) | ⬜ |
| ৪-চ-৪ | View → এডিটর: ড্রপডাউন (`Dropdown`, `pageOptions`) | `view/Dropdown.jsx`, `View.jsx` (১১৩৮–১১৬০) | ⬜ |
| ৪-চ-৫ | View → রিপল (`useRipple`) — Compose-এর Material ripple-ই যথেষ্ট কি না যাচাই; প্রায় কোনো কোড নয় | `view/useRipple.js` | ⬜ |
| ৪-ছ-১ | View → Gemini: `callGemini` ক্লায়েন্ট + `imageB64` (ছবি→বেস৬৪) | `view/gemini.js`, `View.jsx` (৮৭০–৯০৬) | ⬜ |
| ৪-ছ-২ | View → Gemini: `runApi` — এপি আই-১ (ছবি→টেক্সট) শাখা | `View.jsx` (৯২৫–১০১৬, শুধু এপি আই-১ অংশ) | ⬜ |
| ৪-ছ-৩ | View → Gemini: `runApi` — এপি আই-২ (প্রুফ) শাখা + `ApiDialogs`, `openApiCfg` | `View.jsx` (৯০৭–৯১১, ৯২৫–১০১৬ বাকি অংশ), `view/ApiDialogs.jsx` | ⬜ |
| ৪-ছ-৪ | View → অটো OCR টগল ও `maybeAutoOcr` | `View.jsx` (৬০৭–৬৩৫) | ⬜ |
| ৪-জ-১ | View → TTS ১: `TtsController` কাঠামো/অবস্থা (`_set`, `pick`, `rate`, `stop`, `finish`, `pauseHere`) + View-এ `tts` তৈরি, `refreshVoices` | `view/TtsController.js` (১–৯০), `View.jsx` (২৩৯–২৪১, ৪০৯–৪৩৫) | ⬜ |
| ৪-জ-২ | View → TTS ১: ফোনের নিজস্ব ইঞ্জিন — `hasTTS`, `TTS_LANG`, `pickVoice`, `voicesFor`, `voiceOptions`, `speak` | `view/ttsEngines.js` (৬–১১০, XENG বাদে), `view/TtsController.js` (১৩৮–১৯৭) | ⬜ |
| ৪-জ-৩ | View → TTS ১: পড়ার সময় হাইলাইট (`hlShow`, `follow`, `markStale`, `Highlight`) | `view/Highlight.jsx`, `view/TtsController.js` (৩৫–৫১) | ⬜ |
| ৪-জ-৪ | View → TTS ১: শুরু/ক্লিক (`startFrom`, `click`) | `view/TtsController.js` (২৬১–৩০২) | ⬜ |
| ৪-ঝ-১ | View → TTS ২: Google অনলাইন অডিও — `gAudio`, `playG`, `G_RETRY`, `fetchAudio`, `tplUrl` | `view/TtsController.js` (১৪–১৬, ৮৬–১৩৭), `view/ttsEngines.js` (১১৩–১২৪) | ⬜ |
| ৪-ঝ-২ | View → TTS ২: অতিরিক্ত সার্ভিস — `XENG`, `edgeGec`, `edgeAudio`, `xSrc`, `dropX`, `xFetch`, `playX` | `view/ttsEngines.js` (১৬–৭৫, ১২৫–১৯০), `view/TtsController.js` (১৯৮–২৬০) | ⬜ |
| ৪-ঝ-৩ | View → TTS ২: সার্ভিস বাছাই ডায়ালগ (`TtsDialog`, `TS_BASE`, `TS_ORDER`, `tsMeta`, `tsValidate`, `askTts`) | `view/TtsDialog.jsx`, `view/ttsEngines.js` (৭৬–৯৫), `View.jsx` (৪০৯) | ⬜ |
| ৪-ঞ-১ | View → সেটিংস ডায়ালগ (`SettingsDialog`, `normalizedPatch`, `openSettings`) | `view/SettingsDialog.jsx`, `View.jsx` (৭০৯–৭১৪) | ⬜ |
| ৪-ঞ-২ | View → Google Lens (`openLens`) | `View.jsx` (৩৬২–৩৯৭) | ⬜ |
| ৪-ঞ-৩ | View → শেষ জোড়া লাগানো: মূল JSX/লেআউট ও ৪-ঘ-৫ `init`-এর সংযোগ | `View.jsx` (১১৩৮–১২৭৪) | ⬜ |
| ৪-ঞ-৪ | View → পুরো পেইজ যাচাই — **ব্যবহারকারীর কাজ**; এআই কোড পুরো পড়বে না, ব্যবহারকারী যে সমস্যা জানাবে শুধু সেটুকু ঠিক করবে | — | ⬜ |

> **View-এর নোট:** ব্যবহৃত ফাইল `View.jsx` (১২৭৪ লাইন, ৯৩ KB) — এর ২ নম্বর লাইনের মন্তব্যেই আছে "`View.logic.js` আর লাগে না"। তাই `View.logic.js` (২২৮৪ লাইন, ১৬৪ KB) কখনো পড়বেন না, `runLegacy`ও বাদ। একই লজিক দুইবার লিখবেন না।
> উপধাপের লাইন-রেঞ্জ আনুমানিক (±২০ লাইন) — কাজ শুরুর সময় ফাইলে সেই ফাংশনের নাম খুঁজে নিন। কোনো উপধাপ তবুও বড় হলে আরও ভাঙুন (যেমন ৪-ঘ-৫-ক, ৪-ঘ-৫-খ)।

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
