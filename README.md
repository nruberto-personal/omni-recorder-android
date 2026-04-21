# Omni-Recorder (Android + Wear OS)

Personal meeting recorder. Samsung Galaxy Watch records audio → Samsung phone orchestrates cloud transcription + speaker diarization. Cloud-first by design (Android lacks a WhisperKit-equivalent, so local inference is deferred to an optional later phase).

Full architecture, rationale, and phased build plan: see `omni-recorder-android-claude-code-brief.md`.

## Modules

| Module | Purpose | Target |
|---|---|---|
| `:app` | Phone controller — receives audio, orchestrates transcription + diarization, merges results | Android 13+ (API 33) |
| `:wear` | Watch recorder — captures audio, streams to phone via `ChannelClient` | Wear OS 4+ (API 33) |
| `:shared` | Plain-Kotlin DTOs for transcript/diarization JSON, kept field-identical with the iOS sibling build | JVM 17 |

## First-time setup

1. **Generate the Gradle wrapper.** This repo ships without the wrapper binary. Either:
   - Open the project in Android Studio (Koala Feature Drop or newer) — first sync auto-generates the wrapper, **or**
   - If Gradle is on your PATH: `gradle wrapper --gradle-version 8.11.1` from the project root.
2. **Create `local.properties`.** Copy `local.properties.template` to `local.properties`. Leave API-key fields blank for now — they get filled in per phase as each provider is wired up. Android Studio populates `sdk.dir` automatically on first sync.
3. **Sync the project** in Android Studio (File → Sync Project with Gradle Files).

At this point both `:app` and `:wear` should build — they are empty shells pending Phase 1.

## Current status

**Pre-Phase-1 scaffold.** Next milestone: Phase 1 — Watch→phone audio pipeline. See brief §5.

## Related

- **iOS/watchOS sibling:** separate repo, parallel build using WhisperKit for on-device transcription.
- **Diarization service:** FastAPI + `pyannote/speaker-diarization-3.1` on a Hugging Face Space, deployed in Phase 3. Shared endpoint between Android and iOS clients.
# omni-recorder-android
