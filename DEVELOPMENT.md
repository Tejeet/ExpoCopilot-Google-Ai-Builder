# Development Notes

## Origin

CardDrop started as a same-night build: business cards to collect at an expo the
next day, then personal follow-up emails to send afterward — normally a manual
slog of retyping each contact from a stack of photographed cards. The ask was
simple: could Android capture a card photo, recognize the card and pull out the
useful fields, and hand the result to a backend that sends the email?

## Key decisions

Three choices shaped the build, each picked over a faster/lower-effort alternative:

- **A real native app, not a no-code shortcut.** The alternative was scanning
  cards with an existing app (Microsoft Lens / Google Keep) into a spreadsheet
  and mail-merging from there — viable for one night, but not reusable or
  extensible the way a dedicated app is.
- **On-device OCR (Google ML Kit Text Recognition), not a cloud vision API.**
  Works fully offline once its model is cached on first run — important on an
  expo floor where wifi/data can't be relied on. Free, no API key, no per-scan
  cost.
- **Gmail via a Google Apps Script Web App, not a hosted backend.** No server to
  stand up, no billing — deploys straight from script.google.com in about two
  minutes and sends from the user's own Gmail account via `GmailApp`. A
  shared-secret check in the POST payload stands in for auth, since the Web App
  has to be reachable without a Google login from the Android side.

## How it works

1. **Scan** — an in-app CameraX scanner shows a white card-shaped frame
   (QR-scanner style, horizontal or vertical, with tap-to-focus and flash).
   Only the region inside the frame is kept (`ImageUtils.cropToFrame`), so OCR
   isn't distracted by the table or neighbouring cards. Front, then back (or
   skip); the card is saved immediately and the scanner stays open for the next
   one.
2. **Recognize (in the background)** — `CardScanProcessor` runs ML Kit's
   on-device text recognizer on both sides in a process-wide coroutine scope;
   a small heuristic layer (`CardOcrParser`) guesses name / title / company /
   email / phone / website from regex matches and relative text height (a
   stand-in for font size, i.e. visual hierarchy). Front-side guesses win, the
   back fills gaps. Cards left mid-scan by the app being killed are re-read on
   next launch.
3. **AI check (optional, on-device)** — Settings offers a choice of open-weight models from
   Hugging Face's `litert-community` (Apache-2.0, no account needed), run through LiteRT-LM:
   **Qwen3 0.6B** (345 MB, default), **SmolLM2 360M** (374 MB) and **Gemma 4 E2B** (2.6 GB).
   The small two see only the OCR text — which is what the check is mostly about — while
   Gemma 4 also looks at the card photo. `CardAiVerifier` asks for corrected fields as
   constrained JSON, sanity-checks every value (email pattern, phone digit count, website has
   a dot), and only applies corrections to fields the user hasn't touched. Cards are checked
   one at a time (GPU, falling back to CPU); "Check again with AI" is on the review screen.
   (gemma-3-270m is gated on Hugging Face, so the app can't download it.)
4. **Conversation** — a mic toggle in the scanner records the visit and attaches it to the card
   you scan next; the review screen can also record, play back and delete. With Gemma 4 selected,
   `CardAiVerifier.summariseConversation()` turns the recording into bullet-point meeting notes on
   the phone. The audio and the summary upload with the card, so Gemini gets the conversation too.
5. **Review** — every guess lands on an editable form alongside a "show raw
   scanned text" toggle, because layout-based heuristics on business cards are
   inherently approximate. Here the user also adds expo notes (with quick-note
   suggestions), a lead priority (hot / warm / cold), the event name, and
   stall / product photos (camera or gallery). Nothing can be sent until a card
   has been reviewed.
6. **Store** — contacts live in an on-device SQLite database (`carddrop.db`,
   exposed to Compose as a `SnapshotStateList`) so nothing is lost if the app
   closes mid-event. Existing `contacts.json` queues are migrated once on first
   launch (the file is renamed to `contacts.json.migrated`).
7. **Send** — each card is POSTed individually as `multipart/form-data` (fields,
   expo notes, front/back photos) to the backend API, which sends the email
   (SMTP) and the WhatsApp message. The app sets `email_sent` / `whatsapp_sent`
   only when the backend confirms that channel; a retry asks only for the
   channels still missing, so one failure never blocks the rest of the batch
   and nobody gets a duplicate message.

The home screen shows counters (scanned, emails sent, WhatsApp sent, still
reading) and tabs: All / To review / Pending / Sent / Failed.

## Backend API contract

One `POST` per card to the hardcoded endpoint in
[`network/ApiConfig.kt`](app/src/main/java/com/tejeet/carddrop/network/ApiConfig.kt)
(`HOST` + `SEND_CARD_PATH` = `https://apihardware.com/carddrop/send/`, plus
`SHARED_SECRET`). Change them there and rebuild. `Content-Type: multipart/form-data`.

Every text field is always sent, using an empty string when blank. Only `company`,
`email` and `phone` are guaranteed non-empty; the review screen won't save a card
without them. Photo parts are omitted when there is no photo. The app uploads
JPEGs of at most 1600px, well under the backend's 10 MB limit.

| Field | Notes |
|---|---|
| `secret` | `ApiConfig.SHARED_SECRET` |
| `id` | Card UUID, the idempotency key; stays the same across retries |
| `senderName` | Your name from Settings |
| `channels` | `email`, `whatsapp` or `email,whatsapp`; a retry asks only for what failed |
| `syncOnly` | `1` = store the card and photos but send nothing (`channels` is empty); `0` for a normal send |
| `company`, `email`, `phone` | **Required** (phone includes a country code, e.g. `+919876543210`) |
| `name`, `title`, `website`, `linkedin`, `department` | Optional |
| `country`, `city` | From the GPS fix (reverse-geocoded) or typed by hand; optional |
| `eventName` | The card's expo, or the one set in Settings |
| `stallNumber` | Optional |
| `hallName` | Pre-filled with the last hall used; optional |
| `geoLat`, `geoLng` | Decimal degrees, or empty when location is off/unavailable |
| `expoNotes`, `productInterest`, `nextAction` | Optional text (`productInterest` comma-separated) |
| `useCase` | Optional; what they want to use apiHW for (also woven into the messages below) |
| `voiceSummary` | On-device summary of the recorded conversation, or empty |
| `whatsappMessage` | Ready-to-send WhatsApp text, generated by the app (see `messaging/FollowUpMessages.kt`) |
| `emailSubject` | Ready-to-send email subject |
| `emailMessage` | Ready-to-send branded HTML email body (tables + inline styles; user text is HTML-escaped) |
| `followUpDate` | `YYYY-MM-DD` or empty |
| `assignedTo` | Card's rep, or the Settings default |
| `priority` | `NONE`, `LOW`, `MEDIUM` or `HOT` (app's Cold/Warm/Hot); `HIGH` is never sent |
| `tags` | Comma-separated, e.g. `distributor,VIP` |
| `consentEmail`, `consentWhatsapp` | `1` / `0` |
| `deviceId` | Per-install Android id |
| `appVersion` | `BuildConfig.VERSION_NAME`, e.g. `1.0` |
| `language` | Language code, e.g. `en` |
| `ocrConfidence` | 0-100 heuristic (field completeness, raised after an on-device AI check) |
| `manuallyEdited` | `1` when a human changed an OCR/AI-filled field |
| `scannedAt` | Milliseconds since epoch |
| `cardImage`, `cardImageBack` | Optional `image/jpeg` file parts |
| `stallImage` | Optional `image/jpeg` file part, repeated once per stall/product photo |
| `voiceNote` | Optional `audio/mp4` file part: the recorded conversation (16 kHz mono AAC) |

```bash
curl -X POST "https://apihardware.com/carddrop/send/" \
  -F "secret=YOUR-SHARED-SECRET" \
  -F "id=1f1e6f0a-6a4e-4b20-9a0e-2c7d5f0b9a11" \
  -F "senderName=Tejeet" \
  -F "channels=email,whatsapp" \
  -F "name=Priya Sharma" \
  -F "title=Sales Head" \
  -F "company=Acme Technologies Pvt Ltd" \
  -F "email=priya@acme.com" \
  -F "phone=+919876543210" \
  -F "website=https://acme.com" \
  -F "linkedin=https://linkedin.com/in/priya-sharma" \
  -F "country=India" \
  -F "city=Pune" \
  -F "department=Procurement" \
  -F "eventName=India IoT Expo 2026" \
  -F "stallNumber=B-14" \
  -F "hallName=Hall 3 — IoT Pavilion" \
  -F "geoLat=18.5204" \
  -F "geoLng=73.8567" \
  -F "expoNotes=Wants v2 flap demo next week. Send pricing." \
  -F "productInterest=v2flap,ai-vision-board" \
  -F "followUpDate=2026-09-23" \
  -F "assignedTo=tejeet" \
  -F "nextAction=Send v2 pricing + book demo" \
  -F "useCase=smart parking sensors" \
  -F "priority=HOT" \
  -F "tags=distributor,VIP" \
  -F "consentEmail=1" \
  -F "consentWhatsapp=1" \
  -F "deviceId=pixel-8-tejeet" \
  -F "appVersion=1.4.2" \
  -F "language=en" \
  -F "ocrConfidence=88" \
  -F "manuallyEdited=1" \
  -F "scannedAt=1789459200000" \
  -F "whatsappMessage=Hi Priya,

Great meeting you at India IoT Expo 2026!

It was nice connecting with you and hearing about smart parking sensors. We'd love to stay in touch and explore how we can help you with it.

Regards,
Team apiHW
🌐 www.apihardware.com
✉️ info@apihardware.com" \
  -F "emailSubject=Great meeting you at India IoT Expo 2026" \
  -F "emailMessage=<!DOCTYPE html><html>...branded HTML...</html>" \
  -F "cardImage=@card_front.jpg;type=image/jpeg" \
  -F "cardImageBack=@card_back.jpg;type=image/jpeg" \
  -F "stallImage=@stall_1.jpg;type=image/jpeg"
```

**Sync (`syncOnly=1`).** Every saved card is queued for upload straight away, and the
Cards tab's cloud button re-uploads all of them (e.g. after the backend loses rows).
Syncing runs as WorkManager work (`sync/CardSyncWorker.kt`): it waits for a connection,
retries with exponential backoff, and continues if the app is closed. Each request carries the same fields and photos with
`channels=` empty and `syncOnly=1`: upsert by `id` and do **not** email or message anyone.
Reply `{"status":"ok"}`; the app only checks the top-level status for these.

Response: HTTP 2xx with JSON, reporting each channel separately so the app can
flag them independently:

```json
{
  "status": "ok",
  "email": { "status": "sent" },
  "whatsapp": { "status": "failed", "message": "Invalid WhatsApp number" }
}
```

Each channel value may be an object with `status` (+ optional `message`), a
string (`"sent"`, `"ok"`, `"success"`, `"delivered"`) or a boolean. If a channel
key is absent, the top-level `status` (`"ok"` / `"success"`) applies to it. A
non-2xx response or non-JSON body marks every requested channel as failed, using
`message` from the body if present.

## Architecture

```
app/src/main/java/com/tejeet/carddrop/
  MainActivity.kt             screen state, front/back camera capture, permission
                              flow, send loop
  model/Contact.kt            immutable data model (edits go through .copy(),
                              which is what makes Compose recomposition reliable)
  data/ContactDatabase.kt     SQLite schema + queries (send flags, OCR state)
  data/ContactStore.kt        process-wide SnapshotStateList mirror of the table,
                              plus one-time contacts.json migration
  data/SettingsStore.kt       SharedPreferences wrapper (backend URL, secret,
                              sender name, default event name)
  ocr/CardOcrParser.kt        ML Kit call + field-guessing heuristics
  ocr/CardScanProcessor.kt    background OCR of front + back, merge, queue AI check
  ai/AiModel.kt               the selectable models (size, revision, reads-photo)
  ai/AiModelFiles.kt          model file locations + DownloadManager downloader
  ai/CardAiVerifier.kt        LiteRT-LM engine, prompt, JSON parsing, sanity checks
  network/BackendClient.kt    OkHttp multipart POST + per-channel response parsing
  util/ImageUtils.kt          EXIF-aware downsampling, crop-to-frame, upload JPEG
  util/CardFrame.kt           scanner frame geometry (shared by overlay and crop)
  ui/MainScreen.kt            bottom navigation: Home, Cards, Search, Settings
  ui/HomeScreen.kt            dashboard: stats, quick actions, status overview, recent cards
  ui/CardsScreen.kt           full list with search box and status filters
  ui/SearchScreen.kt          search across all fields and tags, Hot/Warm/Cold filters
  ui/CardDetailScreen.kt      saved card at a glance: photos, copyable details, status
  sync/CardSyncWorker.kt      background upload of cards + photos (WorkManager)
  ui/ContactActions.kt        dialler / WhatsApp / email intents for a card
  ui/CardScannerScreen.kt     framed front/back scanner, opens the form when done
  ui/ReviewCardScreen.kt      review form (live-updates as OCR/AI finish), tags, notes, photos
  ui/SettingsScreen.kt        expo name, backend, AI model download
  ui/components/              shared cards, pills, section cards, search field
backend/AppsScript.gs         original Gmail-only backend (JSON body; does not
                              implement the multipart contract above)
```

**Stack:** Kotlin 2.4, Jetpack Compose (Material 3), CameraX, Google ML Kit Text
Recognition, LiteRT-LM + Gemma 4 E2B (on-device AI check), OkHttp, Android
SQLite. LiteRT-LM is compiled with Kotlin 2.4, which is why the project needs
Kotlin ≥ 2.4 and AGP 8.13. Email (SMTP) and WhatsApp sending live in your own
backend API.

## Known limitations / open items

- **Field-guessing is heuristic, not exact.** Business cards have no standard
  layout; the review screen exists specifically because of this.
- **No pre-built APK.** The app was first written (and reviewed line-by-line) in
  an environment without access to the Android SDK or Maven package hosts, so it
  ships as source — build it via Android Studio or `./gradlew installDebug`.
- **Shared-secret auth is minimal.** Fine for a personal tool; a
  public/multi-user deployment would want something stronger than a static
  string compared inside Apps Script.
- **Sent flags are only as honest as the backend.** The app marks a channel sent
  when the backend says so; the backend should only report `sent` after SMTP and
  the WhatsApp API have actually accepted the message.
- **The AI check is a second opinion, not ground truth.** A 2B model can still
  misread a card; the app validates its output and the user still reviews every
  card. It needs ~8 GB+ RAM phones to run comfortably and takes roughly tens of
  seconds per card on mid-range hardware.
- **Background OCR isn't WorkManager.** It survives the camera covering the app
  and resumes on next launch, but won't keep running if the process is killed
  with the app closed.
