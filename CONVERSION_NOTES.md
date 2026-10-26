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
