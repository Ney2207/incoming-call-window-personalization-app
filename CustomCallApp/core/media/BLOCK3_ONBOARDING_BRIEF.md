# Block 3 (`:core:media`) — Team Onboarding & Execution Brief

**Assigned Module:** `:core:media` (already registered in `settings.gradle.kts` with `TYPESAFE_PROJECT_ACCESSORS` enabled)  
**Required Upstream Input:** `:core:model` (`v1.0.0` — Frozen Contract)  
**Downstream Consumers:** Block 4 (`:feature:call-screen`), Block 5 (`:feature:customizer`), Block 7 (`:composeApp`)  

---

## 1. Gradle Setup & Pre-Configured Catalog Dependencies

In `core/media/build.gradle.kts`, import **only** `projects.core.model` from internal modules. All required Media3, Compose Multiplatform, Coroutines, and Koin artifacts are already defined in `gradle/libs.versions.toml`:

```kotlin
// commonMain.dependencies
implementation(projects.core.model)
implementation(compose.runtime)
implementation(compose.foundation)
implementation(compose.ui)
implementation(libs.kotlinx.coroutines.core)
implementation(libs.koin.core)

// androidMain.dependencies
implementation(libs.kotlinx.coroutines.android)
implementation(libs.koin.android)
implementation(libs.androidx.media3.exoplayer)
implementation(libs.androidx.media3.ui)
implementation(libs.androidx.media3.transformer)
implementation(libs.androidx.media3.common)
```

---

## 2. Frozen Contracts from `:core:model` (`v1.0.0`)

Implement `com.customcall.core.model.contract.VideoAssetProcessor` using the immutable domain models already compiled in `:core:model`:

* **`VideoProcessingRequest`**: `themeId: String`, `sourceUri: String`, `trimStartMs: Long = 0L`, `trimEndMs: Long? = null`, `maxShortSidePx: Int = 1080`, `targetFps: Int = 30`, `stripAudio: Boolean = false`
* **`ProcessedVideoAsset`**: `themeId: String`, `outputUri: String`, `durationMs: Long`, `width: Int`, `height: Int`, `fileSizeBytes: Long`
* **`VideoProcessingState`** *(sealed interface)*: `InProgress(progress: Float)`, `Completed(asset: ProcessedVideoAsset)`, `Failed(message: String)`

---

## 3. Implementation Scope & Performance Guardrails

1. **`VideoBackgroundPlayer` Composable (`commonMain` `expect` / `actual`):**
   * Signature:
     ```kotlin
     @Composable
     expect fun VideoBackgroundPlayer(
         videoUri: String,
         isPlaying: Boolean,
         isMuted: Boolean,
         modifier: Modifier = Modifier
     )
     ```
   * **Android (`androidMain`):** Media3 `ExoPlayer` configured with `Player.REPEAT_MODE_ONE`, hardware-backed `SurfaceView` (strictly `SurfaceView` over `TextureView`), pre-buffering for **< 200 ms** first-frame display, graceful handling of blank `videoUri`, and deterministic player/decoder release upon `ON_STOP` or composable disposal.
   * **iOS (`iosMain`):** `actual` composable structure ready for `AVQueuePlayer` + `AVPlayerLooper` inside `UIKitView`.
2. **`VideoAssetProcessor` Implementation (`androidMain` + `iosMain` stub/bridge):**
   * Transcodes external gallery URIs via Media3 `Transformer` (capping short side at `maxShortSidePx` / 1080p, `targetFps` / 30 fps, H.264/HEVC, honoring `trimStartMs`/`trimEndMs` and `stripAudio`) into internal private storage (`context.filesDir/themes/<themeId>.mp4`).
   * Emits progress and terminal results via `Flow<VideoProcessingState>` and cleans up orphaned/deleted theme files.
3. **Koin DI Module (`com.customcall.core.media.di`):**
   * Expose `val mediaModule: Module` binding `single<VideoAssetProcessor> { ... }`.

---

## 4. Block 3 Exit Criteria (Handoff to Blocks 4 & 5)

1. Zero changes to `:core:model` (`v1.0.0`) and zero imports from `:core:database` or `:feature:*`.
2. Clean compilation (`:core:media:assemble`) with zero warnings and passing automated tests (`:core:media:testDebugUnitTest` / instrumented benchmarks verifying gapless looping without black frames, deterministic decoder release, and internal file transcoding).
3. Deliver `core/media/INTEGRATION.md` documenting `VideoBackgroundPlayer`, `VideoAssetProcessor`, and `mediaModule` for Blocks 4, 5, and 7.