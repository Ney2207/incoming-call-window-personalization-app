# `:core:media` Integration Specification (`v1.0.0`)

## 1. Module Overview, Targets & Dependency Boundaries

`:core:media` implements the hardware-accelerated looping video background engine and background media ingestion/transcoding pipeline on top of `:core:model` (`v1.0.0`).

* **Supported Targets:** Android (`minSdk = 26`, `compileSdk = 35`, Java 17) and iOS (`iosX64`, `iosArm64`, `iosSimulatorArm64`).
* **Strict Module Isolation:** `:core:media` depends **only** on `projects.core.model`. It has zero dependencies on `:core:database` or `:feature:*`.
* **Manifest & Permissions:** `:core:media` (`namespace = "com.customcall.core.media"`) requires **no** additional `AndroidManifest.xml` permissions (`READ_MEDIA_VIDEO` is not required when ingesting URIs returned by Android Photo Picker `PickVisualMedia`).

Downstream feature and application modules (`:feature:call-screen`, `:feature:customizer`, `:composeApp`) import `:core:media` via Gradle type-safe project accessors:

```kotlin
// Inside commonMain.dependencies { ... }
implementation(projects.core.media)
```

---

## 2. Koin Dependency Injection (`com.customcall.core.media.di`)

Register `mediaModule` in the root Koin graph (`:composeApp` in Block 7). `mediaModule` automatically includes the internal platform module (`platformMediaModule` for `androidMain` and `iosMain`):

```kotlin
startKoin {
    androidContext(this@CustomCallApplication) // Required on Android
    modules(
        databaseModule,
        mediaModule,
        // ... downstream feature modules
    )
}
```

### Exposed Koin Bindings
* `single<VideoAssetProcessor>` — Concrete platform implementation (`AndroidVideoAssetProcessor` backed by Media3 `Transformer` on Android; `IosVideoAssetProcessor` backed by `AVAssetExportSession` on iOS) bound to `com.customcall.core.model.contract.VideoAssetProcessor`.
* `single<ThemeAssetStorage>` *(internal)* — Manages private theme storage directories and atomic `.tmp.mp4` commits.

---

## 3. `VideoBackgroundPlayer` Composable (`com.customcall.core.media.player`)

Used by **Block 4 (`:feature:call-screen`)** for the incoming call background layer and **Block 5 (`:feature:customizer`)** for live theme previews:

```kotlin
@Composable
fun VideoBackgroundPlayer(
    videoUri: String,
    isPlaying: Boolean,
    isMuted: Boolean,
    modifier: Modifier = Modifier,
)
```

### Critical Integration & Layout Rules for Downstream UI Teams

1. **Explicit Sizing Required (`Modifier.fillMaxSize()`):**
   * `VideoBackgroundPlayer` forwards `modifier` directly to `AndroidView` / `UIKitView` (or an empty `Box(modifier)` when `videoUri` is blank) without applying `Modifier.fillMaxSize()` internally.
   * Callers **must** pass `modifier = Modifier.fillMaxSize()` (or explicit bounds); otherwise, the video surface collapses to `0×0 dp`.

2. **Hardware `SurfaceView` Hole-Punching & Fallback Background Canvas (Block 4):**
   * On Android, `VideoBackgroundPlayer` renders onto a hardware `SurfaceView` (`keepScreenOn = true`, `zOrderOnTop = false`), which punches a transparent hole through the Compose window to display the video surface behind it.
   * When `videoUri` is blank, invalid (`"null"`), encounters a `PlaybackException`, or while the host lifecycle is below `Lifecycle.State.STARTED`, `VideoBackgroundPlayer` renders a transparent `Box(modifier)` without instantiating `ExoPlayer`.
   * **Action for Block 4:** Always paint an opaque dark or gradient fallback background on the root container **behind** `VideoBackgroundPlayer` (so `DefaultThemeProvider.BUILTIN_FALLBACK_THEME` with `videoUri = ""` renders cleanly), and ensure all upper z-index layers (gradient scrim, caller identity, interactive buttons) remain translucent over the video viewport rather than drawing opaque full-screen backgrounds over the `SurfaceView`.

3. **Compose `@Preview` Safety (Blocks 4 & 5):**
   * Whenever `videoUri` is non-blank and the lifecycle is `STARTED`, `VideoBackgroundPlayer` instantiates a real `ExoPlayer` and hardware `SurfaceView`.
   * In `@Preview` functions, always supply `videoUri = ""` so Android Studio's layoutlib preview renderer takes the `Box(modifier)` fast-path without attempting to initialize hardware `MediaCodec` decoders.

4. **Sub-200ms Startup, Scaling & Gapless Looping:**
   * **Android:** `ExoPlayer` configured with a custom `DefaultLoadControl` (`bufferForPlaybackMs = 50`, `bufferForPlaybackAfterRebufferMs = 100`, `minBufferMs = 500`, `maxBufferMs = 2000`, `prioritizeTimeOverSizeThresholds = true`), `Player.REPEAT_MODE_ONE`, and `C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING`.
   * **iOS:** `AVQueuePlayer` (`automaticallyWaitsToMinimizeStalling = false`) + `AVPlayerLooper` inside `AVPlayerLayer` (`AVLayerVideoGravityResizeAspectFill`) wrapped in `UIKitView`.

5. **Dynamic State Updates vs. Lifecycle Teardown:**
   * Toggling `isPlaying` or `isMuted` updates `player.playWhenReady` and `player.volume` (`0f` when muted, `1f` when unmuted) in-place without recreating `ExoPlayer`. Changing `videoUri` disposes the prior player and prepares a new one.
   * `VideoBackgroundPlayer` observes `LocalLifecycleOwner.current`. Upon `Lifecycle.Event.ON_STOP` or composable disposal (`DisposableEffect` / `UIKitView.onRelease`), it synchronously clears the video surface, stops playback, and calls `player.release()` (or `playerLooper.disableLooping()` on iOS) via an atomic single-release guard.
   * **Behavior for Block 6 (`:feature:interceptor`):** When `IncomingCallActivity` or the overlay window is stopped/dismissed, all `MediaCodec` decoders are released immediately. If the same screen returns to `ON_START`, a fresh `ExoPlayer` instance is automatically recreated and prepared.

---

## 4. `VideoAssetProcessor` & Storage Workflows (`com.customcall.core.model.contract`)

Used by **Block 5 (`:feature:customizer`)** to ingest user videos and manage theme files, and **Block 7 (`:composeApp`)** for housekeeping. Inject `com.customcall.core.model.contract.VideoAssetProcessor`:

### 4.1 Transcoding Pipeline (`processVideo`)

```kotlin
fun processVideo(request: VideoProcessingRequest): Flow<VideoProcessingState>
```

* **Threading:** `processVideo` manages its own dispatchers internally (`flowOn(Dispatchers.Main)` on Android for Media3 `Transformer` Looper affinity; `Dispatchers.IO` for `MediaMetadataRetriever` and file commits). ViewModels in Block 5 can collect the returned `Flow` directly on `viewModelScope`.
* **Processing Rules:**
  * **Container & Codecs:** Exports `.mp4` (`MimeTypes.VIDEO_H264` + `MimeTypes.AUDIO_AAC` on Android; `AVFileTypeMPEG4` on iOS).
  * **Trimming:** Clips to `[trimStartMs, trimEndMs]` when `trimStartMs > 0L` or `trimEndMs != null`.
  * **Resolution Cap (No Upscaling):** Downscales via `Presentation.createForWidthAndHeight(..., LAYOUT_SCALE_TO_FIT)` with even pixel alignment (`and 1.inv()`) **only** when `minOf(width, height) > request.maxShortSidePx` (default `1080`). Videos already `<= 1080p` keep their native resolution.
  * **Frame Rate & Audio:** Applies `FrameDropEffect.createDefaultFrameDropEffect(targetFps.toFloat())` when `targetFps > 0` (default `30`), and strips audio tracks when `request.stripAudio == true`.
* **State Emissions & Cancellation:**
  * Emits `VideoProcessingState.InProgress(0f)`, polls export progress every `100 ms` (`0.01f..0.99f`), and terminates with `VideoProcessingState.Completed(ProcessedVideoAsset)` or `VideoProcessingState.Failed(message)`.
  * Cancelling the collecting coroutine cancels the active `Transformer` / `AVAssetExportSession` and deletes any partial `.tmp.mp4` file.
  * Note: `VideoProcessingRequest` validates non-blank `themeId`, non-blank `sourceUri`, and `trimEndMs > trimStartMs` via `require(...)` at construction time. Unreadable URIs or audio-only files emit `VideoProcessingState.Failed` cleanly without throwing across the `Flow`.

### 4.2 Private Internal Storage & Coordination with `:core:database` (Block 5)

Because `:core:media` and `:core:database` are sibling `:core` modules that do not call each other, **Block 5 (`:feature:customizer`)** must coordinate persistence and file operations in the following order:

1. **Private Storage Location:**
   * Android: `context.filesDir/themes/<sanitizedThemeId>.mp4`
   * iOS: `Application Support/themes/<sanitizedThemeId>.mp4`
   * `themeId` characters outside `[a-zA-Z0-9._-]` and `..` sequences are sanitized to `_` to prevent path traversal.
2. **Creating or Updating a Theme (Transcode First, Persist Second):**
   * Always collect `VideoAssetProcessor.processVideo(request)` until `VideoProcessingState.Completed(asset)` **before** calling `CallRepository.upsertTheme(theme.copy(videoUri = asset.outputUri))`.
   * Never store raw gallery `content://` URIs in `CallRepository`; SAF URI permissions expire across device reboots and lock-screen wakeups.
   * **Safe In-Place Re-Editing:** Transcoding writes to `<sanitizedThemeId>.tmp.mp4` and atomically replaces `<sanitizedThemeId>.mp4` only upon `onCompleted`. Re-processing an existing `themeId` (including trimming an already-saved theme) leaves the existing committed `.mp4` intact if the user cancels or if export fails.
3. **Deleting a Theme (Persist First, Delete File Second):**
   * Call `CallRepository.deleteTheme(themeId)` first so `ThemeRuleResolver` immediately stops resolving the deleted theme on incoming calls, then call `suspend fun deleteProcessedAsset(themeId: String): Boolean` to remove the `.mp4` file from disk.
4. **Orphaned Asset Cleanup:**
   * Call `suspend fun clearUnusedAssets(activeThemeIds: Set<String>): Int` with the active theme IDs from `CallRepository.observeThemes()` (e.g., on settings exit or app startup in Block 7) to prune any unreferenced `.mp4` or leftover `.tmp.mp4` files.