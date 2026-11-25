# PayNova API (all under `/api`, JSON, `Authorization: Bearer <jwt>` unless noted)

| Method | Path | Notes |
|---|---|---|
| POST | /auth/register | `{email,password(8+),name?}` → `{accessToken}` (role always USER; rate-limited) |
| POST | /auth/login | `{email,password}` → `{accessToken}` |
| GET/PUT | /user/profile | name, upiId, contactsConsent |
| GET | /user/banks | masked bank accounts from the PSP (TODO in production adapter) |
| POST | /user/devices | register FCM token |
| POST | /qr/resolve | `{kind:VPA\|MOBILE\|BANK,value,ifsc?,name?}` → `{name?,vpa,verified,merchantInfo?}` via the PSP |
| POST | /payment/initiate | header **Idempotency-Key** (8+ chars) required. Body `{vpa,name?,amountPaise(1..10,000,000),note?,method,category?}`. Same key → same transaction |
| GET | /payment/status/:id | id or providerRef; re-checks PENDING with the PSP |
| POST | /payment/request | payment request (collect) – stub until PSP collect API is wired |
| POST | /payment/webhook | **no JWT**; header `x-signature` = HMAC-SHA256(rawBody, PSP_WEBHOOK_SECRET); deduped by `eventId` |
| POST | /bbps/fetch | `{category,customerId}` → bill via BBPS operator |
| GET | /transactions, /transactions/:id | `?status=&take=&cursor=` |
| GET/POST | /beneficiaries | saved / favourite beneficiaries |
| GET | /rewards | `{coins}` from the ledger |
| GET/POST | /notifications, /notifications/read | |
| POST/GET | /support/tickets | |
| GET | /analytics/monthly?month=YYYY-MM | totals by category (SUCCESS only) |
| GET | /offers | public active offers |
| GET/POST/PUT/DELETE | /admin/... | ADMIN/SUPERADMIN only: users, transactions, failed, provider, analytics, offers, tickets, notify, audit |

Webhook body: `{"eventId":"...","providerRef":"...","status":"SUCCESS|FAILED|REVERSED|PENDING","upiTxnId":"..."}`.
Only the PSP (webhook or status poll) can set SUCCESS. SUCCESS never downgrades except to REVERSED.
