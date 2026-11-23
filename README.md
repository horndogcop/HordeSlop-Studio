# Hordecraft — Native Android Client for AI Horde

> **AI Attribution Notice ("Vibeslopped")**: All coding, architecture, and documentation in this project are 100% **vibeslopped** by AI (`models/gemini-4-hydrogen`).

Native Kotlin & Jetpack Compose Android client (`minSdk 23`) for [aihorde.net](https://aihorde.net), focusing on battery-efficient, older-device-friendly image generation, multi-activity navigation, and full respect for official AI Horde v2 API semantics.

## Roadmap & Project Board

All MVP milestones and difficulty-ordered Future Plans (Tiers 1–5) are tracked on the [Hordecraft Android Roadmap Project](https://github.com/users/horndogcop/projects/1).

### MVP Scope (Pure txt2img First)
- Multi-activity Android architecture (`ImageGenActivity`, `ModelsActivity`, `GalleryActivity`, `AccountActivity`, `SettingsActivity`) sharing Compose UI chrome
- Three live-switchable navigation layouts: **Bottom Bar (default)**, **Menu Drawer**, and **Material 3 Adaptive Suite**
- First-launch API key / Shared Key GUID (`/v2/sharedkeys/{id}`) / Anonymous (`0000000000`) onboarding
- Global `1.1s` rate-limited request queue + HTTP response header diagnostics + switchable API endpoint
- Pure txt2img using `sampler_name`, all 11 official `scheduler` values (omitting deprecated `karras` boolean), `/v2/status/sampler_constraints`, non-blocking model-reference advisories, and customizable per-model step/sampler defaults
- Multi-model combined & per-model fan-out generation modes
- Official Styles API + `Haidra-Org/AI-Horde-Styles` + `amiantos/AI-Horde-Styles-Previews`
- Check-before-status polling (including post-cancel partial result recovery), pausable queue, Recents-resilient background job service (auto-stops when empty), and 4 Android notification channels
- SAF document storage + `Downloads/Hordecraft` fallback with pre-save PNG/WebP/JPEG metadata embedding
- Room-backed prompt history & favorite prompts, OSS credits, and in-app AI Horde Privacy/TOS viewer

## License

Licensed under **GNU AGPLv3 or later** (`AGPL-3.0-or-later`) — chosen as the broadest copyleft license compatible with all Apache-2.0 Android/Kotlin dependencies.
