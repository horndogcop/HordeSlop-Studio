# HordeSlop Studio — Native Android Client for AI Horde

> **AI Attribution Notice ("Vibeslopped")**: All coding, architecture, and documentation in this project are 100% **vibeslopped** by AI (`models/gemini-4-hydrogen`, `Gemini 3.5 flash lite`, `Gemini flash latest`).

Native Kotlin & Jetpack Compose Android client (`minSdk 23`) for [aihorde.net](https://aihorde.net), focusing on battery-efficient, older-device-friendly image generation, multi-activity navigation, and full respect for official AI Horde v2 API semantics.

## Implemented MVP Architecture

- **Exported Multi-Activity Entry Points** (`ui/activities/ExportedActivities.kt`, `AndroidManifest.xml`): `ImageGenActivity`, `GalleryActivity`, `UserActivity`, and `SettingsActivity` (plus Tier 2 stubs `ModelsActivity`, `TextGenActivity`, `AlchemistActivity`) sharing reactive UI state and switchable **Bottom Bar (default)**, **Menu Drawer**, and **Adaptive Rail** chrome (`HordeScaffold.kt`).
- **Switchable Launcher Icon** (`LauncherIconManager.kt`): `<activity-alias>` switching between **Robot Placeholder (default)** and **Haidra Logo** (`https://haidra.net/content/images/2024/11/haidra-transparent.png`), with explicit non-affiliation notice.
- **Studio (`StudioScreen.kt`, `StudioModals.kt`, `StyleContractEngine.kt`)**:
  - 10 starter template prompts (`TemplatePrompts.kt`) picked at random on startup and Trash reset.
  - Collapsible sections (Prompt, Models & Style, Sampling, persisted Advanced, LoRAs & Embeddings).
  - Generate bar below Sampling (or pinned Top / Bottom) with responsive Phone vs Tablet/Landscape layout and automatic `>20` request batch splitting (`n <= 20`).
  - Logarithmic Steps (`10–25` mid-half, `1..50` slider / `100` manual), logarithmic CFG (`1–6` first `2/3`, fading negative prompt at `CFG = 1`), Clip Skip slider + input (`1..12`), yellow/red zone color indicators, and wide-layout side-by-side Seed + Scheduler.
  - Model Picker Modal with live `/v2/status/models`, `AI-Horde-image-model-reference` showcases, customizable per-model Room defaults, and conditional **Multiple models** mode (`Any selected model` vs `One request per model` when `>1` model selected).
  - Styles API + `AI-Horde-Styles` contract engine locking style-governed fields and omitting unchanged parameters.
  - Size Modal enforcing `64px` multiples with Width/Height swap and Aspect Ratio + Megapixel presets.
  - Ordered Civitai LoRAs (up to 5, UI order preserved, `modelVersionId`, collapsible to name only, inline weight sliders on Tablet, italicized *all*/*random* triggers sent as plain `"all"`/`"random"` to `inject_trigger` or comma-joined triggers, `API` vs `+ Prompt`/`+ Neg` targeting, omitting `(trigger:1.0)` on Anima/Krea/turbo models) & Embeddings / TIs (up to 5, `(embedding:modelId:weight)`, `sd15`/`sd21`/`SDXL` filter, `-5..+5` log weight slider, `API` toggle).
- **Network, Background Service & WebP Storage**:
  - `HordeRateLimiterInterceptor` spacing Horde API calls at `1.1s` with switchable `aihorde.net` vs `Other` URL.
  - `HordeJobService` foreground service with `/v2/generate/check/{id}` -> `/v2/generate/status/{id}` polling, pausable queue, post-cancel partial recovery, 4 Android notification channels (`NotificationChannels.kt`), and automatic shutdown when queue empties.
  - `WebpStorageManager` saving genuine **WebP** images with EXIF metadata (`ExifInterface`) to SAF folders or `Downloads/HordeSlop Studio`.
- **Gallery (`GalleryScreen.kt`)**: Clean grid, Request ID & Favorites filters, `HorizontalPager` swipeable viewer, tap-to-toggle fullscreen HUD, immediate Reroll (seed cleared), Use settings, WebP/JSON export & share, and optional file deletion prompt.

## Build & Run

Open the repository root (`hordeslop-studio/`) in **Android Studio Ladybug+** (AGPL-3.0-or-later, AGP `8.7.3`, Kotlin `2.0.21`, JDK `17`, Gradle `8.9`, `minSdk 23`, `targetSdk 35`).

Run unit tests locally with Gradle once Android SDK / JDK 17 is configured:

```bash
./gradlew testDebugUnitTest assembleDebug
```

## Roadmap & Project Board

All MVP milestones and difficulty-ordered Future Plans (Tiers 1–5) are tracked on the [HordeSlop Studio Roadmap Project](https://github.com/users/horndogcop/projects/1).

Upcoming Tier 2 wallpaper plans include **Muzei Live Wallpaper integration** and an **Android wallpaper provider**.

## License

Licensed under **GNU AGPLv3 or later** (`AGPL-3.0-or-later`) — chosen as the broadest copyleft license compatible with all Apache-2.0 Android/Kotlin dependencies.
