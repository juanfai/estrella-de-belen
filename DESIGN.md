# Estrella de Belén — Design Document

## Concept

Guided meditation app with a pulsing glow animation synchronized to audio.
Full refactor of a previous audio-to-video export project, reusing its GPU animation engine.

---

## Tech Stack

| Layer | Technology |
|---|---|
| UI | Jetpack Compose |
| State | ViewModel + StateFlow |
| Auth | Firebase Auth (email/password) |
| Database | Firestore (metadata, favorites, stats) |
| Audio files | Firebase Storage (authenticated access only) |
| Streaming | ExoPlayer (androidx.media3) |
| Offline storage | Private internal storage (`context.filesDir`) |
| Background audio | ForegroundService + MediaSession |
| Animation | GlowShaderRenderer + GlowPreviewRenderer (reused) |
| Navigation | Navigation Compose |
| Local index | Room DB |
| Reminders | WorkManager |
| Images | Coil Compose |

---

## Color Palette

| Role | Hex |
|---|---|
| Background | `#F4EFFF` — washed lavender |
| Surface (cards) | `#FDFBFF` — white with lilac tint |
| Primary | `#9B8EC4` — medium lavender |
| Primary dark | `#7C6BAE` — deep lavender |
| Secondary | `#E8C4D8` — quartz rose (complementary) |
| Accent / chips | `#C4D8E8` — soft sky blue (adjacent) |
| Primary text | `#2D2640` — dark purple-gray |
| Secondary text | `#6B5F8A` — lavender gray |
| Dividers / borders | `#E2DAF5` |

**Note:** PlayerScreen is the exception — pure black background so the glow animation stands out.

---

## Data Model (Firestore)

```
meditations/{id}
  title: String
  description: String
  duration: Int           // seconds
  audioUrl: String        // Firebase Storage URL (requires auth)
  glowColor: String       // glow center color (#hex)
  haloColor: String       // outer halo color (#hex)
  category: String        // "anxiety", "sleep", "focus", etc.
  order: Int              // display order
  createdAt: Timestamp    // for "New" badge (7 days)

users/{uid}
  displayName: String
  email: String
  favorites: [meditationId, ...]
  totalSessions: Int
  totalMinutes: Int
  streak: Int             // consecutive days
  lastSessionDate: String // for streak calculation
  notificationsEnabled: Boolean
  notificationTime: String // "08:00"
```

---

## Navigation Structure

```
SplashScreen
    ↓ (check active session)
LoginScreen  ←→  RegisterScreen
    ↓
Bottom Nav (2 tabs)
├── 🏠 Home  →  HomeScreen
│                   ↓ (tap meditation)
│               PlayerScreen (full-screen)
│
└── 👤 Profile  →  ProfileScreen
                    ├── FavoritesScreen
                    └── DownloadsScreen

[Floating mini-player above nav bar when audio is active]
```

---

## Screens

### SplashScreen
- "Estrella de Belén" logo on lavender background
- Checks for active session
- Redirects to Login or Home

### LoginScreen / RegisterScreen
- Email + password
- Soft dark aesthetic (background `#1A1025`) for visual continuity with the animation
- Smooth transition to Home

### HomeScreen
- Dynamic time-based greeting: "Buenos días / tardes / noches, [name]"
- Large featured card: "Meditación del día" (random or curated)
- Duration filter chips: Todas / 5 min / 10 min / 20+ min
- 2-column grid of meditation cards
- Each card: circular glow color preview, title, duration, "Nuevo" badge (first 7 days), ♡ favorite, ↓ download

### PlayerScreen
- **Pure black background**
- Real-time pulsing glow animation (reuses `GlowPreviewRenderer`)
- Each meditation has its own glow/halo colors
- **Controls only visible on tap** → auto-hide after 3 seconds
- Controls: title, seek bar with timestamps, ⏮ ⏸ ⏭, ♡ favorite, ↓ save offline
- `FLAG_KEEP_SCREEN_ON` active while on this screen
- If the user manually turns off the screen → audio continues (ForegroundService)
- Media notification on lock screen with play/pause

### Mini-player (floating)
- Appears above the nav bar when audio is active and the user navigates away from the Player
- Shows: title + play/pause + button to return to full-screen Player
- Dismissed when the meditation ends or is explicitly paused

### ProfileScreen
- Avatar with user initials
- Name and email
- Stats: total sessions, total time, day streak (🔥)
- Access to: Favorites, Downloaded (offline)
- Settings: daily reminder (on/off + time), language selector (UI ready, disabled until a second language is available), theme (light/dark)
- Log out

---

## Key Features

### Offline download
- Audio downloaded to `context.filesDir` (private internal storage)
- Not accessible from the file explorer or other apps
- Firebase Storage rules require authentication — no access without login
- Animation always generated in real time from RMS (no video stored)
- Room DB keeps the index of downloaded files

### Daily reminder
- WorkManager schedules a daily notification
- User configures the time from Profile → Settings
- Message: "✨ Es tu momento de meditar"

### "New" badge
- Chip shown on the card if `createdAt` is less than 7 days ago

### Day streak
- Updated in Firestore on session completion
- `lastSessionDate` compared to current date to check continuity

### Content management — admin web panel
- A simple web form hosted on Firebase Hosting (free tier)
- Protected by email/password login (Firebase Auth, separate admin account)
- Fields: title, description, audio file upload, glow color picker, halo color picker, category, display order
- On submit: uploads audio to Firebase Storage, gets the download URL, creates the Firestore document
- Built with plain HTML + Firebase JS SDK — no framework needed
- The content creator (non-technical) opens the URL in any browser and publishes without touching the console

---

## Reused from the previous project

| Class | Use in the new app |
|---|---|
| `GlowShaderRenderer` | GPU animation engine (unchanged) |
| `GlowPreviewRenderer` | Real-time preview in PlayerScreen (unchanged) |
| `EglCore` | OpenGL ES context (unchanged) |
| `VisualConfig` | Animation parameters (adapted to receive per-meditation colors) |
| `AudioDecoder` | Pre-computes RMS from downloaded audio for offline preview |

## Removed

- `RecordScreen`, `AudioRecorder`, `AudioEditor`, `AudioPlayer`
- `VideoExporter`, `VideoEncoder`
- `ImportService`, `ExportService`, `ExportManager`
- `ColorPickerDialog`

---

## New dependencies

```toml
# Firebase
firebase-bom = "33.x.x"
firebase-auth-ktx
firebase-firestore-ktx
firebase-storage-ktx

# Media
androidx-media3-exoplayer = "1.x.x"
androidx-media3-session = "1.x.x"

# Navigation
androidx-navigation-compose = "2.x.x"

# Room
androidx-room-runtime = "2.x.x"
androidx-room-ktx = "2.x.x"

# WorkManager
androidx-work-runtime-ktx = "2.x.x"

# Image loading
coil-compose = "2.x.x"
```

---

## Coding conventions

- **All code in English:** class names, variables, functions, parameters, comments. Industry standard, consistent with the Android/Kotlin ecosystem.
- **User-facing text in Spanish** only, defined in `res/values/strings.xml`. Nothing hardcoded in source files.

---

## Internationalization (i18n)

- The app is structured from the start to support multiple languages using Android's standard resource system.
- **No user-visible text is hardcoded** in source files — everything goes through string resources.
- File structure:
  ```
  res/
    values/strings.xml       ← Spanish (default language)
    values-en/strings.xml    ← English (to be filled when needed)
  ```
- Only Spanish is complete for now. Adding a new language in the future only requires creating a `values-{lang}/` folder with translations.
- The language selector in Settings (Profile) is built into the UI but disabled until a second language is available.

---

## Implementation order

1. Firebase setup (create project, add `google-services.json`)
2. Base refactor — rename package, remove deleted classes, add dependencies
3. Auth — Splash, Login, Register
4. Home — Firestore catalog, duration filter chips, "New" badge
5. Player — ExoPlayer streaming + glow animation + tap-to-show controls + keep screen on
6. Background audio — ForegroundService + MediaSession + lock screen notification
7. Offline — download to internal storage + Room DB + local playback
8. Mini-player — persistent floating bar
9. Profile — stats, favorites, downloads, settings, notifications
10. Streak and stats — streak logic in Firestore

---

## iOS roadmap (phased approach)

**Phase 1 — Android** (current):
Build the app in Kotlin + Compose with clean architecture (Repository pattern, ViewModels decoupled from Android specifics). This makes KMP migration straightforward later.

**Phase 2 — iOS** (when an Apple Developer account is available):
Migrate to **Kotlin Multiplatform + Compose Multiplatform**. Well-structured Phase 1 code converts almost directly to shared KMP modules. The only platform-specific work is porting the glow shader from OpenGL ES (Android) to Metal (iOS) — a single file.

Why KMP and not Flutter: the existing Kotlin/Compose codebase would be almost entirely reusable. Flutter would require a full rewrite in Dart.

Note: an Apple Developer account ($99/year) is required to publish on the App Store and test on real iOS devices. iOS development without one is limited to the Xcode simulator.

---

## Pending before starting

- Create project in Firebase Console
- Add `google-services.json` to the `app/` module
- Define security rules in Firestore and Storage
