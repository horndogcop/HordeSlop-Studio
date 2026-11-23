# Hordecraft — Native Android Client for AI Horde

> **AI Attribution Notice ("Vibeslopped")**: All coding, architecture, and documentation in this project are 100% **vibeslopped** by AI (`models/gemini-4-hydrogen`).

Native Kotlin & Jetpack Compose Android client (`minSdk 23`) for [aihorde.net](https://aihorde.net), focusing on battery-efficient, older-device-friendly image generation, multi-activity navigation, and full respect for official AI Horde v2 API semantics.

## Roadmap & Project Board

All MVP milestones and difficulty-ordered Future Plans (Tiers 1–5) are tracked on the [Hordecraft Android Roadmap Project](https://github.com/users/horndogcop/projects/1).

### MVP Scope (Pure txt2img First)
- Exported multi-activity Android architecture (`ImageGenActivity`, `GalleryActivity`, `UserActivity`, `SettingsActivity`) sharing Compose UI chrome with terse, nontechnical copy
- Three live-switchable navigation layouts: **Bottom Bar (default)**, **Menu Drawer**, and **Material 3 Adaptive Suite**
- Configurable **Generate** bar placed **below** Sampling (or pinned Top/Bottom), paired with a trash reset button (picks a random prompt from 10 built-in templates on startup and reset) and `< count >` quantity selector (automatically splitting `>20` image requests into `<= 20` API batches; responsive side-by-side layout on Portrait vs Tablet/Landscape)
- Switchable Launcher Icons (`Robot Placeholder` default vs official transparent **Haidra Logo** with non-affiliation notice)
- First-launch API key / Shared Key / Anonymous onboarding, with AI Horde & Civitai API Keys managed in Settings and `UserActivity` dedicated to user stats & future management
- Global `1.1s` rate-limited request queue + switchable API endpoint (`aihorde.net` vs `Other`)
- Pure txt2img using logarithmic **Steps** (`10–25` mid-half, max `50` slider / `100` manual) and **CFG** (`1–6` first `2/3`, fading negative prompt at `CFG = 1`), `sampler_name`, **Clip Skip** slider + numeric entry (`1..12`), **yellow/red color-coded sliders** when outside suggested/slider limits (no text advisories), and persistent collapsible Advanced Settings (`Seed` and `scheduler` side-by-side on Tablet/Landscape, `nsfw`, `censor_nsfw`)
- **Model Picker Modal** containing toggleable showcase previews, customizable per-model defaults, and the **Multiple models** dispatch setting (`Any selected model` vs `One request per model`, shown when multiple models are selected)
- Styles API / `AI-Horde-Styles` parameter locking & minimal-diff payloads
- Ordered Civitai LoRAs (`modelVersionId`, up to 5, collapsible to name only, sent in UI order, inline weight sliders on Tablet, plain trigger display with `*all*`, `*random*`, multi-trigger comma joining, `API` vs `+ Prompt`/`+ Neg` targeting, and omitting `(trigger:1.0)` weight notation on Anima/Krea/turbo models) & Embeddings / TIs (up to 5, collapsible to name only, restricted to `sd15`, `sd21`, `SDXL`, `(embedding:modelId:weight)` snippet with matching log `-5..+5` weight slider and `API` vs `Prompt`/`Neg` targeting), plus `"All models"` filter bypass and numeric ID pinning
- Pausable queue, background job polling with partial cancel recovery, Recents-resilient service (auto-stops when empty), and 4 Android notification channels
- SAF document storage + `Downloads/Hordecraft` fallback with WebP EXIF/XMP metadata embedding
- Searchable Gallery with Request filter (`Same request`), Favorites, swipeable unpadded modal & tap-to-toggle Fullscreen HUD, immediate Reroll (no seed), and storage-safe deletion prompt
- Searchable prompt history & favorites above Prompt, inline prompt actions + fullscreen editor, OSS Licenses modal, and unified Privacy & Legal modal

## License

Licensed under **GNU AGPLv3 or later** (`AGPL-3.0-or-later`) — chosen as the broadest copyleft license compatible with all Apache-2.0 Android/Kotlin dependencies.
