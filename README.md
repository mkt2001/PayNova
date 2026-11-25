# PayNova – UPI payment & digital finance app

Android app (Kotlin, Jetpack Compose, Material 3, MVVM, Room, Retrofit, CameraX, ML Kit, BiometricPrompt,
WorkManager, TextToSpeech) + NestJS/PostgreSQL backend + web admin dashboard.

> **Honest status.** The Android app builds in **DEMO mode** with a payment simulator (fake money, everything labelled DEMO).
> Real UPI money movement needs an **authorized PSP / bank partner, NPCI approvals and compliance**. Production mode is
> therefore disabled until the app is built with an HTTPS backend URL **and** the backend has PSP credentials.
> PSP and BBPS adapters are skeletons (`backend/src/payment/production.provider.ts`) – you plug your provider in there.

## What works now (demo)
Home dashboard · real CameraX + ML Kit QR scanner (flash, zoom, gallery, duplicate-scan guard, invalid-QR errors) ·
UPI QR/link parsing (pa, pn, am, cu, tn, mc, tr; unknown = "Not available") · real contact sync (READ_CONTACTS on demand,
search, favourites, recents, photos; nothing uploaded) · pay by mobile / UPI ID / bank account / QR · resolve → amount →
confirm → (DEMO PIN) → receipt · idempotency keys + duplicate-payment lock · PENDING/SUCCESS/FAILED/REVERSED handling with
auto status refresh · receipt PDF share/download · history with search, tabs, date/amount filters, CSV + PDF statement,
offline cache · Recharge & Bills (demo biller) · Explore/Insurance (clearly "partner not connected") · EMI calculator ·
spending analytics · My QR / request money / split bills · app-lock PIN (PBKDF2, encrypted prefs) + biometrics + auto-lock +
"Forgot PIN" via device credential · Hindi/English TTS after verified success only (once per transaction) ·
dark mode · notifications · network banner.

**Demo test amounts:** ₹13 (or any amount ending in 13) → FAILED · ₹17 → PENDING 30 s then SUCCESS · others → SUCCESS after 2 s.

## Security rules baked in
Never collects/stores/logs the UPI PIN · no fake PIN capture (demo PIN screen is labelled DEMO, nothing stored) ·
success only from provider status · idempotency everywhere · no secrets in the app · demo and production fully separate ·
admin API never returns passwords/PINs and logs an audit trail.

## Build the APK
**GitHub (easiest):**
1. Create a repo. Add `.github/workflows/build-apk.yml` (file `build-apk.yml` from this delivery).
2. Upload `PayNova.zip` to the repo root. The workflow unzips it, builds, and publishes `PayNova.apk` in **Releases**.

**Local:** JDK 17 + Android SDK 34, then `gradle :app:assembleDebug` (Gradle 8.9) →
`app/build/outputs/apk/debug/app-debug.apk`. Connect a backend: `gradle :app:assembleDebug -PbackendUrl=https://api.yourdomain.com/`.

## Backend
```
cd backend && cp .env.example .env     # set JWT_SECRET etc.
docker compose up -d                    # postgres + redis
npm install && npx prisma migrate dev && npm run start:dev
npx ts-node src/scripts/create-admin.ts admin@you.com 'LongPassword123!'
```
Open `admin-dashboard/index.html` (serve it behind the same host as `/api`). API: `docs/API.md`.

## Tests
`gradle :app:testDebugUnitTest` – QR parsing, amounts, phone/contact normalisation, PIN lockout, demo provider
(no success at initiation, failed, pending→success). Backend: `cd backend && npm test` (provider + webhook signature).
Not automated yet: instrumented tests for camera/contacts permissions, Room duplicate-payment, TTS, biometrics, API e2e.

## Going to production (checklist)
PSP/TPAP + bank sponsor agreement · NPCI onboarding · RBI/PA-PG compliance · BBPS BOU/COU onboarding ·
implement `ProductionProvider` + PSP SDK auth hand-off in `BackendPaymentProvider` · Firebase (add `google-services.json`, FCM)
· Redis-backed rate limiting · release signing key · Play Store policy review · security audit/pen-test.
