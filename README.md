# Hordecraft — Native Android Client for AI Horde

> **AI Attribution Notice ("Vibeslopped")**: All coding, architecture, and documentation in this project are 100% **vibeslopped** by AI (`models/gemini-4-hydrogen`).

Native Kotlin & Jetpack Compose Android client (`minSdk 23`) for [aihorde.net](https://aihorde.net), focusing on battery-efficient, older-device-friendly image generation, multi-activity navigation, and full respect for official AI Horde v2 API semantics.

## Roadmap & Project Board

All MVP milestones and difficulty-ordered Future Plans (Tiers 1–5) are tracked on the [Hordecraft Android Roadmap Project](https://github.com/users/horndogcop/projects/1).

### MVP Scope (Pure txt2img First)
- Exported multi-activity Android architecture (`ImageGenActivity`, `GalleryActivity`, `UserActivity`, `SettingsActivity`) sharing Compose UI chrome
- Three live-switchable navigation layouts: **Bottom Bar (default)**, **Menu Drawer**, and **Material 3 Adaptive Suite**
- Switchable Launcher Icons (`Robot Placeholder` default vs `AI Horde / Haidra Logo` with non-affiliation notice)
- First-launch API key / Shared Key GUID (`/v2/sharedkeys/{id}`) / Anonymous (`0000000000`) onboarding, with AI Horde & Civitai API Keys managed in Settings and `UserActivity` dedicated to user stats & future management
- Global `1.1s` rate-limited request queue + HTTP response header diagnostics + switchable API endpoint
- Pure txt2img using `sampler_name`, `clip_skip`, all 11 official `scheduler` values (omitting deprecated `karras` boolean) in a persistent collapsible Advanced Settings section alongside `nsfw` / `censor_nsfw`
- Model Picker modal with toggleable showcase previews, customizable per-model defaults, and non-blocking model-reference advisories
- Multi-model combined & per-model fan-out generation modes, plus Styles API / `AI-Horde-Styles` parameter locking & minimal-diff payloads
- Civitai LoRAs (`modelVersionId`, up to 5) & Textual Inversions / Embeddings (up to 5) with 9-group multi-select `baseModels` dropdown filtering (`SDXL`, `Pony`, `Illustrious`, `NoobAI`, `SD 1.x`, `Moss`, `Krea2`, `z-image`, `Z-Image-Turbo`)
- Pausable queue, check-before-status polling (including post-cancel partial result recovery), Recents-resilient background job service (auto-stops when empty), and 4 Android notification channels
- SAF document storage + `Downloads/Hordecraft` fallback with WebP EXIF/XMP metadata embedding
- Searchable Gallery with Request ID filtering (`See Request Images`), Favorites, swipeable unpadded modal & tap-to-toggle Fullscreen HUD, immediate Reroll (no seed), and file-deletion prompt
- Searchable prompt history & favorites above Positive Prompt, inline prompt actions + fullscreen editor, OSS credits, and in-app AI Horde Privacy/TOS viewer

## License

Licensed under **GNU AGPLv3 or later** (`AGPL-3.0-or-later`) — chosen as the broadest copyleft license compatible with all Apache-2.0 Android/Kotlin dependencies.
