# Omni-Recorder (Android/Wear OS): Claude Code Project Brief

## Context for Claude Code
You are starting a new project. Read this entire brief before writing any code or creating files. Propose the repo structure, confirm the tech stack choices below, then execute **phase by phase** — do not attempt to build everything at once. After each phase, stop and let me verify before moving on.

This is the Android/Wear OS counterpart to a parallel iOS/watchOS build. Where reasonable, keep data models, transcript JSON schemas, and the diarization service API identical between the two so the two apps can share the same backend.

---

## 1. What we're building

A personal tool for capturing **business meetings** on a Samsung Galaxy Watch (Wear OS 4+), transcribing them accurately, and producing a transcript that labels **who said what** (speaker diarization). Transcripts are for internal review — not shipped to end users, not monetized.

**Why this doesn't already exist:** Paid options (Otter.ai, Notta) cap free tiers too tightly for real meetings (3–30 min) or cost $14–17/mo. This build eliminates that SaaS tax by leaning on local models + free-tier developer APIs.

**Use case specifics that shape the design:**
- Meetings are typically 30–90 minutes.
- 2–6 speakers is the common case.
- Recording happens on the wrist discreetly.
- Transcripts are consumed later, not in real time — latency is not critical.
- Audio quality will be imperfect (watch mic, ambient noise, varying distance).
- **Target devices:** Galaxy Watch 4 or newer (Wear OS 4+), paired with a modern Galaxy phone (Android 13+). Do not target Tizen — it's EOL.

---

## 2. Architecture: Cloud-First, Local-Optional

> **Key difference from the iOS build:** iOS has WhisperKit, a polished on-device Whisper implementation using CoreML and the Apple Neural Engine. Android has no equivalent of similar quality — `whisper.cpp` with JNI bindings works but is meaningfully more setup, and device-to-device performance varies wildly with Android fragmentation. So we **flip the priority**: cloud-first using generous free tiers, with local Whisper as an optional later phase.

```
[Galaxy Watch]                  [Galaxy Phone]              [Free Cloud Services]
   Compose for Wear OS          Controller app               - Groq (Whisper-large-v3)
   MediaRecorder (m4a)          - Provider Manager ─────────►- Deepgram
   ↓                              (API rotation + retry)     - AssemblyAI
   ChannelClient ─────────────► - Results viewer
   (Wearable Data Layer,        - (Optional) whisper.cpp     [Diarization — shared
    streams .m4a to phone)        local inference             with iOS build]
                                                             - Pyannote.audio on
                                                               Hugging Face Space
```

**Priority logic on the phone controller:**

1. **Transcription — Priority 1 (cloud):** Rotate through Groq → Deepgram → AssemblyAI based on availability and rate limits.
2. **Transcription — Priority 2 (local, optional, later phase):** `whisper.cpp` with JNI bindings as a fallback for offline use or if all cloud free tiers are exhausted.
3. **Diarization:** Pyannote.audio microservice on Hugging Face Space. Run **in parallel** with transcription and merge timestamps afterward. **This is the same endpoint used by the iOS app** — share it.

---

## 3. Tech stack (decided — don't substitute without flagging)

| Layer | Choice | Notes |
|---|---|---|
| Watch app | Kotlin + Compose for Wear OS | Target Wear OS 4+ (API 33+) |
| Phone app | Kotlin + Jetpack Compose | Target Android 13+ (API 33+) |
| Audio capture (watch) | `MediaRecorder` | Output: `.m4a` (AAC), mono, 16kHz |
| Watch-to-phone transfer | `ChannelClient` (Wearable Data Layer) | **Not** `DataClient` or `MessageClient` — they have ~100KB limits |
| Cloud STT | Groq, Deepgram, AssemblyAI | Generous free tiers / dev credits |
| Local STT (optional, later) | [whisper.cpp](https://github.com/ggerganov/whisper.cpp) via JNI | Use `ggml-base` or `ggml-small` models |
| Diarization | [pyannote.audio](https://github.com/pyannote/pyannote-audio) 3.x on Hugging Face Space | **Shared with iOS build** |
| Async/concurrency | Kotlin coroutines + Flow | Standard |
| HTTP | OkHttp + Retrofit (or Ktor) | Pick one and stick with it |
| Background work | WorkManager | For long-running transcription jobs |

---

## 4. Proposed repo structure

```
omni-recorder-android/
├── app/                           # Phone controller (:app module)
│   ├── src/main/kotlin/
│   │   ├── pipeline/              # Orchestrator + merge logic
│   │   ├── providers/             # One file per cloud STT provider
│   │   ├── diarization/           # HTTP client for shared HF Space
│   │   ├── local/                 # whisper.cpp JNI wrapper (Phase 6)
│   │   ├── session/               # WearableListenerService (phone side)
│   │   └── ui/                    # Recording list, transcript viewer
├── wear/                          # Watch app (:wear module)
│   ├── src/main/kotlin/
│   │   ├── recording/             # MediaRecorder wrapper
│   │   ├── session/               # ChannelClient (watch side)
│   │   └── ui/                    # Compose for Wear OS screens
├── shared/                        # :shared KMP-lite module (plain Kotlin)
│   └── src/main/kotlin/           # Transcript models, constants
├── build.gradle.kts
└── README.md
```

If a Python diarization service already exists in the iOS repo, **do not duplicate it** — reference it and point this build at the same HF Space URL.

Confirm this structure (or propose a better one) before creating files.

---

## 5. Phased build plan

**Stop after each phase. Verify before continuing.**

### Phase 1 — Watch-to-Phone audio pipeline
**Goal:** Record on watch, get `.m4a` onto phone reliably, even when the watch screen dims.

- Minimal watch UI (Compose for Wear OS): big record button, elapsed timer, stop button.
- `MediaRecorder` configured for AAC, mono, 16kHz, ~32kbps.
- Use `ChannelClient.openChannel()` to stream the file to the phone once recording stops. **Do not use** `DataClient` (100KB limit) or `MessageClient` (also size-limited) for the audio itself.
- Phone-side `WearableListenerService` with `onChannelOpened` to receive the stream and write to the app's internal storage.
- Simple phone UI listing received recordings with timestamp + duration.
- Handle `RECORD_AUDIO` permission on the watch, `POST_NOTIFICATIONS` and foreground service permissions on the phone.

**Gotcha to get right:** Wear OS aggressively kills apps when the screen is off. The recording must run inside a **foreground service on the watch** with an ongoing notification, or audio will cut off as soon as the user drops their wrist.

**Definition of done:** I can record a 10+ minute audio on the watch, lower my wrist the whole time, and reliably see the `.m4a` on the phone.

### Phase 2 — Cloud transcription (Groq first)
**Goal:** Transcribe a received recording using Groq's Whisper-large-v3 endpoint.

- `TranscriptionProvider` interface with `suspend fun transcribe(audio: File): Transcript`.
- `GroqProvider` implementation. API key stored in `local.properties` or BuildConfig — not committed.
- Tap a recording → show transcript on screen.
- Store transcripts as JSON alongside the audio in app storage.

**Definition of done:** Tap any recording → transcript appears within a few seconds for a 10-min clip.

### Phase 3 — Pyannote diarization (shared service)
**Goal:** Hit the existing HF Space and get back speaker-labeled segments.

- If the HF Space already exists from the iOS build, just wire up the HTTP client.
- If not, deploy it: FastAPI service that accepts a file at `POST /diarize` and returns `[{"start": 0.5, "end": 2.0, "speaker": "SPEAKER_00"}, ...]`.
- Store the diarization JSON alongside the transcript.

**Definition of done:** For a given recording, I can retrieve both a transcript and a diarization result from their respective services.

### Phase 4 — Merge: "zip" transcription + diarization
**Goal:** Combine Whisper's segment timestamps with Pyannote's speaker timestamps.

- The "double-pass" trick: for each Whisper segment, find the Pyannote speaker whose time range overlaps most, assign that speaker label.
- Handle overlapping speakers, gaps, and single-word misattribution.
- Run transcription and diarization **in parallel** using coroutines (`async { ... }`) to minimize wall time.
- Final format:
  ```
  [00:12] Speaker A: Thanks for joining today.
  [00:15] Speaker B: No problem, happy to be here.
  ```

**Definition of done:** A real meeting recording produces a readable, speaker-labeled transcript.

### Phase 5 — Provider Manager (cloud rotation)
**Goal:** Gracefully fall back when a provider rate-limits or errors out.

- Implement `DeepgramProvider` and `AssemblyAIProvider` against the same interface.
- `ProviderManager` iterates the priority list; on `429`, network failure, or timeout, rolls to the next. Logs which provider served each request.
- Config UI to paste API keys — plain SharedPreferences is fine for a personal tool (no Keystore ceremony needed).

**Note:** Deepgram has diarization built into its transcription API. If its free-credit allowance is enough for our usage, we could skip the Pyannote path entirely when using Deepgram. Worth A/B-testing accuracy once Phase 5 is done before committing to that simplification.

**Definition of done:** Simulate a 429 from Groq → watch it automatically fall through to Deepgram with no user intervention.

### Phase 6 — Local whisper.cpp (optional)
**Goal:** Add on-device transcription for offline use or if cloud credits run dry.

- Add `whisper.cpp` as a git submodule or Gradle dependency.
- Build JNI bindings (there are community wrappers to crib from).
- Ship the `ggml-base.bin` model (~150MB) — lazy-download on first use rather than bundling in the APK.
- Add `LocalWhisperProvider` to the priority list ahead of cloud providers (if Nathan wants $0 + privacy) or behind them (if he wants speed).

**Definition of done:** Airplane mode on → a recording still transcribes locally.

---

## 6. Key technical gotchas to get right

- **Wear OS foreground service is non-negotiable for recording.** Without it, audio dies when the screen dims. Use `ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE` (required on Android 14+).
- **`ChannelClient`, not `DataClient`.** DataClient's 100KB-per-item cap will silently fail on any real meeting recording. ChannelClient is the streaming API designed for exactly this use case.
- **Audio format: mono, 16kHz, AAC @ ~32kbps.** Plenty for speech, small files, works with both Whisper and Pyannote. Stereo and higher sample rates waste bytes without improving transcription.
- **Pyannote requires a Hugging Face token** with access to the gated `pyannote/speaker-diarization-3.1` model. Document this in the HF Space README.
- **Merge logic is where accuracy lives.** Pyannote speaker boundaries and Whisper segment boundaries will never align perfectly. Use "max-overlap wins" at the segment granularity, not the word granularity.
- **WorkManager for transcription jobs.** Long cloud calls should run in a `CoroutineWorker` so they survive app backgrounding and phone lock.
- **API keys in source = game over.** Use `local.properties` + BuildConfig injection. `.gitignore` day one.
- **Don't assume the watch has internet.** Always route network calls through the phone. The watch's job ends when the `.m4a` lands on the phone.

---

## 7. Alternatives considered

- **Otter.ai / Notta:** Same SaaS-tax reasons as the iOS build. Notta's 3-min session cap makes it useless for meetings.
- **Deepgram's built-in diarization:** A real option. Trade-off is accuracy (Pyannote is considered the open-source gold standard) vs. simplicity (one API call instead of two). Benchmark both after Phase 5.
- **Samsung Voice Recorder / Bixby:** No speaker diarization, no programmatic access to recordings suitable for this pipeline.
- **MLKit on-device STT:** Limited language support, lower accuracy than Whisper, no diarization.
- **Tizen Galaxy Watches (Watch 3 and older):** EOL, no viable dev toolchain in 2026. Not supported.

---

## 8. Free-tier reference (as of early 2026 — verify before hardcoding)

| Service | Free allowance | Notes |
|---|---|---|
| Groq | High rate-limit free tier on Whisper-large-v3 | Fastest Whisper available |
| Deepgram | $200 in developer credits | Built-in diarization option |
| AssemblyAI | ~$50 credit | `speaker_labels=true` flag available |
| Hugging Face Spaces | CPU Basic free tier | Enough for Pyannote on short clips; may need upgrade for long meetings |

> **Note to Claude Code:** verify current free-tier details on each provider's pricing page before hardcoding limits. These shift.

---

## 9. Coordination with the iOS build

If the iOS version of this tool already exists in a sibling repo:

- **Share the Pyannote HF Space** — one deployment, two clients.
- **Match the transcript JSON schema** so a viewer built for one platform can read output from the other.
- **Match the diarization JSON schema** returned by the HF Space.
- Keep the `Transcript`, `Segment`, and `Speaker` data models field-for-field identical between Swift and Kotlin.

---

## 10. Deliverables (final)

- Kotlin code: Wear OS recorder + phone controller (provider rotation + merge logic).
- `local.properties` template with API key placeholders.
- Root README with Android Studio setup, HF Space URL config, and API key setup steps.
- **Not** duplicating the Python diarization service if it already exists in the iOS repo.

---

## 11. Start here

1. Confirm the repo structure in §4 or propose a better one.
2. Check whether a Pyannote HF Space already exists (I will tell you the URL if so).
3. Scaffold the Android Studio project (phone + wear modules, shared module).
4. Begin Phase 1. Do not start Phase 2 until Phase 1 is verified.

Ask clarifying questions before writing code if anything is ambiguous.
