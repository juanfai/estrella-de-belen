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
| Background audio | MediaSessionService (media3) |
| Animation | GlowShaderRenderer + GlowPreviewRenderer (reused) |
| Navigation | Navigation Compose |
| Local index | Room DB |
| Reminders | WorkManager |
| Images | Coil Compose |
| DI | AppContainer (service locator — Hilt if needed later) |

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
  durationSeconds: Int        // seconds (auto-detected from audio in admin panel)
  audioUrl: String            // Firebase Storage URL (requires auth)
  imageUrl: String            // thumbnail (optional)
  haloColor: String           // glow/halo color (#hex) — single color per meditation
  category: String            // "paz", "sueño", "concentración", etc.
  order: Int                  // display order
  createdAt: Timestamp        // for "New" badge (7 days)

users/{uid}
  displayName: String
  email: String
  favorites: [meditationId, ...]
  totalSessions: Int
  totalMinutes: Int
  streak: Int                 // consecutive days
  lastSessionDate: String     // for streak calculation
  notificationsEnabled: Boolean
  notificationTime: String    // "08:00"
```

---

## Navigation Structure

```
SplashScreen
    ↓ (check active session via Firebase.auth.currentUser)
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
```

---

## Screens

### SplashScreen
- Logo (`logo.xml` VectorDrawable, Moonbeam color `#FF8750`) on dark background, fade-in animation
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
- 2-column `LazyVerticalGrid` of meditation cards
- Each card: thumbnail or halo color preview, title, duration, "Nuevo" badge (first 7 days), ♡ favorite, ↓ download
- Meditations loaded from Firestore via `HomeViewModel`

### PlayerScreen
- **Pure black background**
- Real-time pulsing glow animation via `GlowPreviewRenderer` (TextureView)
- Breathing animation starts immediately (before audio loads)
- **Controls only visible on tap** → auto-hide after 3 seconds
- Controls: title, seek bar with timestamps, ⏮ ⏸ ⏭, ♡ favorite, ↓ save offline
- `FLAG_KEEP_SCREEN_ON` active while on this screen
- If the user manually turns off the screen → audio continues (MediaSessionService)
- Media notification on lock screen with play/pause

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
- Animation always generated in real time (no video stored)
- Room DB (`DownloadedMeditation`) keeps the index of downloaded files

### Daily reminder
- WorkManager `PeriodicWorkRequest` (1-day interval) schedules a local notification
- User toggles on/off and picks the time from Profile → Settings (Material3 `TimePicker` dialog)
- On Android 13+: requests `POST_NOTIFICATIONS` permission at runtime before enabling
- Tapping the notification opens the app via `PendingIntent` → `MainActivity`
- Notification body: "Tu momento de meditación te espera ✦"
- `ReminderScheduler` uses `ExistingPeriodicWorkPolicy.UPDATE` — cambiar la hora cancela y reprograma

### Light / Dark mode
- `AppThemeViewModel` (AndroidViewModel) holds `isDark: StateFlow<Boolean>`, persisted in SharedPreferences
- Default: dark
- `MainActivity` observes it and passes `darkTheme` to `EstrellaDeBelénTheme`; all screens react instantly
- Toggle en Profile → Settings (icono luna/sol); usa `viewModel(LocalContext.current as ComponentActivity)` para compartir la misma instancia que `MainActivity`

### "New" badge
- `Meditation.isNew` computed: `createdAt < 7 days ago`

### Day streak
- Updated in Firestore on session completion
- `lastSessionDate` compared to current date to check continuity

---

## Admin web panel

Hosted on Firebase Hosting (`estrella-de-belen-85a2b` site) — plain HTML + Firebase JS SDK v10.

**Features implemented:**
- Email/password login (Firebase Auth) — only the admin account can access
- List view of all meditations ordered by `order` field
- Create new meditation: title, description, category, duration, order, halo color, image, audio
- Edit existing meditation (pre-fills all fields)
- Delete with confirmation modal
- Drag & drop file upload for audio and image, with progress bars
- Auto-detects audio duration from the file (fills the duration field automatically)
- Image preview before save
- 9 preset color swatches + free color picker for `haloColor`
- Saves `createdAt` server timestamp on new docs
- Displays friendly auth error messages in Spanish

**Deploy:**
```bash
# Requires Node 18 or 22 (not 25 — incompatible with firebase-tools@13)
cd web-admin
nvm use 22 && firebase deploy
```

**Admin access setup (one-time):**
```bash
cd web-admin
node set-admin.js <email>   # sets custom claim admin:true via Admin SDK
```
Requires `service-account.json` (Firebase Console → Project settings → Service accounts → Generate new private key). Never commitear.

**Pending:**
- Replace `REPLACE_WITH_WEB_APP_ID` in `app.js` with the actual web app ID from Firebase Console
  (Project settings → Your apps → Web app → appId)

---

## Reused from the previous project

| Class | Use in the new app |
|---|---|
| `GlowShaderRenderer` | GPU animation engine (unchanged) |
| `GlowPreviewRenderer` | Real-time preview in PlayerScreen (unchanged) |
| `EglCore` | OpenGL ES context (unchanged) |
| `VisualConfig` | Animation parameters (adapted to per-meditation colors) |
| `AudioDecoder` | Pre-computes RMS from downloaded audio for offline preview |

## Removed

- `RecordScreen`, `AudioRecorder`, `AudioEditor`, `AudioPlayer`
- `VideoExporter`, `VideoEncoder`
- `ImportService`, `ExportService`, `ExportManager`
- `ColorPickerDialog`

---

## Dependencies

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

- **All code in English:** class names, variables, functions, parameters, comments.
- **User-facing text in Spanish** only, defined in `res/values/strings.xml`. Nothing hardcoded in source files.

---

## Internationalization (i18n)

- Structured from the start to support multiple languages via Android's resource system.
- No user-visible text hardcoded in source files.
- File structure:
  ```
  res/
    values/strings.xml       ← Spanish (default)
    values-en/strings.xml    ← English (to be filled when needed)
  ```
- Language selector in Profile Settings is built into the UI but disabled until a second language is available.

---

## Subscription model

### Stack: RevenueCat + Google Play Billing

RevenueCat actúa como capa de abstracción sobre Google Play Billing. Maneja validación server-side, renewals, expirations y entitlements. Gratis hasta $2.5k MRR. Elegido porque simplifica la migración a iOS en el futuro (mismo SDK).

### Productos (crear en Google Play Console)

| Product ID | Precio | Período |
|---|---|---|
| `estrella_monthly` | $10 | Mensual |
| `estrella_quarterly` | $25 | Trimestral |
| `estrella_annual` | $99 | Anual |

En RevenueCat se agrupan bajo un **Entitlement** llamado `premium`.

### Contenido libre vs. premium

- Estrategia: campo `isFree: Boolean` en cada documento de `meditations/` en Firestore
- El admin puede marcarlo desde el web panel (checkbox en el form)
- Al lanzar: **1 sola meditación gratuita**
- Las demás muestran un overlay con badge "✦ Premium" en la card

### Cambios al data model

```
meditations/{id}
  + isFree: Boolean     // true = accesible sin suscripción

users/{uid}
  + subscriptionStatus: String   // "free" | "active"
```

`subscriptionStatus` es un caché local sincronizado desde RevenueCat al abrir la app. La fuente de verdad es RevenueCat.

### Flujo de pantallas

```
HomeScreen
  ├── Card libre (isFree=true) → PlayerScreen (igual que hoy)
  └── Card premium (usuario free)
        → overlay "✦ Premium" + tap bloqueado
        → tap → PaywallScreen

SettingsScreen
  └── fila "Suscripción" → PaywallScreen (o gestión si ya tiene)

PaywallScreen
  ├── Logo + tagline
  ├── 3 opciones: Mensual / Trimestral / Anual (anual destacado)
  ├── Botón "Suscribirse" → Google Play Billing flow via RevenueCat
  └── "Restaurar compras" + links Términos / Privacidad
```

### Firestore security rules — cambios necesarios

```
match /meditations/{id} {
  allow read: if request.auth != null;
  allow update: if request.auth.token.admin == true;
  // isFree solo escribible por admin (ya cubierto por la regla de arriba)
}
```

### Checklist de implementación

| Tarea | Estado |
|---|---|
| Cuenta RevenueCat creada | ✅ Creada |
| App en Google Play Console | ⏳ Verificando |
| Productos de suscripción en Play Console | ⏳ Espera verificación |
| RevenueCat vinculado a Play Console | ⏳ Espera verificación |
| `isFree` en modelo `Meditation` + Firestore | ✅ Done |
| `subscriptionStatus` en `UserProfile` | ✅ Done |
| Checkbox `isFree` en web admin panel | ✅ Done |
| Overlay premium en `MeditationCard` | ✅ Done |
| `PaywallScreen` (UI + RevenueCat purchase flow) | ✅ Done (stub — muestra "Próximamente") |
| Fila "Suscripción" en SettingsScreen | ✅ Done |
| `SubscriptionRepository` (RevenueCat SDK) | ✅ Done (interfaz + StubSubscriptionRepository) |
| Sincronización de entitlement al login | ⏳ Pendiente (requiere RevenueCat + Play Console) |

---

## Implementation status

| Area | Status |
|---|---|
| Android app skeleton (package rename, DI, build) | ✅ Done |
| Color palette + Theme (light + dark) | ✅ Done |
| Light/Dark mode toggle (Profile → Settings, persisted) | ✅ Done |
| Navigation graph (Splash → Login/Register → Home/Player/Profile) | ✅ Done |
| Data model (`Meditation`, `UserProfile`) | ✅ Done |
| Firestore repositories (`FirebaseMeditationRepository`, `FirebaseUserRepository`) | ✅ Done |
| Room DB (`DownloadedMeditation`, `MeditationDao`, `AppDatabase`) | ✅ Done |
| Auth screens (Login, Register, AuthViewModel) | ✅ Done |
| SplashScreen (logo VectorDrawable Moonbeam) | ✅ Done |
| HomeScreen + HomeViewModel | ✅ Done |
| PlayerScreen + PlayerViewModel (glow, controls, keep-screen-on) | ✅ Done |
| MediaPlaybackService (MediaSessionService + ExoPlayer) | ✅ Done |
| ProfileScreen + ProfileViewModel (stats, library, settings) | ✅ Done |
| FavoritesScreen + DownloadsScreen | ✅ Done |
| WorkManager daily reminders (time picker, runtime permission, tap-to-open) | ✅ Done |
| Firestore + Storage security rules (custom claims for admin) | ✅ Done |
| Admin web panel (create / edit / delete / upload) | ✅ Done |
| Admin custom claim setup (`set-admin.js`) | ✅ Done |
| Firebase Hosting deploy | ✅ Done |
| MiniPlayer | ❌ Removed |
| `google-services.json` in `app/` | ⚠️ Pending (needs Firebase Console) |
| Web app ID in `web-admin/public/app.js` | ⚠️ Pending |
| Streak + stats write-back on session complete | ✅ Done |
| Subscription model (RevenueCat + PaywallScreen + content gating) | ✅ UI done / stub — espera Play Console |

---

## iOS roadmap (phased approach)

**Phase 1 — Android** (current):
Build the app in Kotlin + Compose with clean architecture (Repository pattern, ViewModels decoupled from Android specifics). This makes KMP migration straightforward later.

**Phase 2 — iOS** (when an Apple Developer account is available):
Migrate to **Kotlin Multiplatform + Compose Multiplatform**. Well-structured Phase 1 code converts almost directly to shared KMP modules. The only platform-specific work is porting the glow shader from OpenGL ES (Android) to Metal (iOS) — a single file.

Why KMP and not Flutter: the existing Kotlin/Compose codebase would be almost entirely reusable. Flutter would require a full rewrite in Dart.

Note: an Apple Developer account ($99/year) is required to publish on the App Store and test on real iOS devices.
