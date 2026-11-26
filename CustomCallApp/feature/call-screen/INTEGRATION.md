# `:feature:call-screen` Integration Specification (`v1.0.0`)

## 1. Module Overview, Targets & Dependency Boundaries

`:feature:call-screen` implements the edge-to-edge, hardware-accelerated Compose Multiplatform incoming call screen UI on top of `:core:model` (`v1.0.0`) and `:core:media` (`v1.0.0`).

* **Supported Targets:** Android (`minSdk = 26`, `compileSdk = 35`, Java 17) and iOS (`iosX64`, `iosArm64`, `iosSimulatorArm64`).
* **Strict Module Isolation:** `:feature:call-screen` depends **only** on `projects.core.model` and `projects.core.media`. It has zero dependencies on `:core:database` or sibling `:feature:*` modules.
* **Dependency Injection & Manifest:** Pure stateless UI module—no Koin modules or `AndroidManifest.xml` permissions are declared or required by `:feature:call-screen`.

Downstream feature and application modules (`:feature:customizer`, `:feature:interceptor`, `:composeApp`) import `:feature:call-screen` via Gradle type-safe project accessors:

```kotlin
// Inside commonMain.dependencies { ... }
implementation(projects.feature.callScreen)
```

---

## 2. Public Entry Point (`com.customcall.feature.callscreen`)

```kotlin
@Composable
fun IncomingCallScreen(
    event: IncomingCallEvent,
    theme: CallThemeConfig,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
    callState: CallState = CallState.RINGING,
    isVideoPlaying: Boolean = true,
    avatarBitmap: ImageBitmap? = null,
    applySystemBarInsets: Boolean = true,
)
```

### Four-Layer Z-Index Stack
1. **Base Layer (`CallVideoLayer`):** Paints an opaque theme-tinted obsidian backdrop behind `:core:media`'s `VideoBackgroundPlayer(modifier = Modifier.fillMaxSize())`, preventing black or transparent window flashes when `videoUri` is blank (`DefaultThemeProvider.BUILTIN_FALLBACK_THEME`) or before the first video frame renders.
2. **Contrast Layer (`CallContrastScrimLayer`):** Renders translucent top (`42%` height) and bottom (`46%` height) vertical gradient scrims scaled by `theme.overlayGradientAlpha` (clamped safely to `0f..1f`).
3. **Caller Identity Layer (`CallerIdentityHeader`):** Displays `CallSourceBadge` (`PHONE_GSM`, `WHATSAPP`, `WHATSAPP_BUSINESS`, `TELEGRAM`, `OTHER_VOIP`), a circular contact portrait (or deterministic 2-character monogram fallback), caller display name, secondary `callerIdentifier`, and a GPU-composited pulsing `CallState` indicator.
4. **Interactive Controls Layer (`CallControlsDispatcher`):** Switches between `ButtonStyleType.CLASSIC_DUAL`, `ButtonStyleType.SWIPE_PILL`, and `ButtonStyleType.MINIMAL_GESTURE` styled with `theme.accentColorHex` (automatically selecting dark or light foreground contrast by WCAG luminance), gated by `event.hasAnswerAction` / `event.hasDeclineAction`, and guarded by `SingleShotActionGuard` (keyed on `event.callId`).

---

## 3. Integration Rules for Block 5 (`:feature:customizer`)

* **Embedded Preview Card Insets & Aspect Ratio:** When embedding `IncomingCallScreen` inside a compact Theme Studio preview card, pass `applySystemBarInsets = false` and constrain the container bounds via `modifier` (e.g., `Modifier.aspectRatio(9f / 19.5f)`). Vertical padding scales proportionally via `BoxWithConstraints`.
* **Compose `@Preview` Safety:** Always supply `theme.copy(videoUri = "")` and `isVideoPlaying = false` inside `@Preview` functions so Android Studio's layoutlib renderer uses the fallback backdrop without attempting to instantiate hardware `MediaCodec` decoders.
* **Live On-Device Theme Preview:** When previewing a processed internal video (`file://.../themes/<themeId>.mp4`), pass `isVideoPlaying = true` with no-op `onAnswer = {}` and `onDecline = {}` lambdas.

---

## 4. Integration Rules for Block 6 (`:feature:interceptor`) & Block 7 (`:composeApp`)

* **Window & Hardware `SurfaceView` Transparency (Block 6):**
  * `:core:media`'s `VideoBackgroundPlayer` renders onto an Android hardware `SurfaceView` (`zOrderOnTop = false`), which punches a transparent hole through the Compose window to display video frames behind it.
  * **Lock-Screen `IncomingCallActivity`:** Configure an edge-to-edge transparent window (`enableEdgeToEdge()`, transparent status/navigation bars, and `window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))`) and do **not** wrap `IncomingCallScreen` in an opaque `Surface` or `Box` background.
  * **Unlocked Overlay (`CallWindowManager`):** Configure `WindowManager.LayoutParams` with `PixelFormat.TRANSLUCENT` so the `SurfaceView` hole-punch renders cleanly, and keep `applySystemBarInsets = true` (default) so top metadata and bottom controls clear status and navigation bars.
* **Single-Shot Action Callbacks (Block 6):** `onAnswer` and `onDecline` fire at most once per `event.callId` even under rapid taps or simultaneous swipe gestures. Trigger `CallActionExecutor.answerCall(event.callId)` / `declineCall(event.callId)` directly inside these callbacks, and pass updated `callState` (`CallState.ANSWERED` / `CallState.DECLINED`) if keeping the UI visible briefly during dismissal.
* **Automatic Decoder Cleanup (Blocks 6 & 7):** `VideoBackgroundPlayer` observes `LocalLifecycleOwner.current` and releases `ExoPlayer` decoders immediately on `Lifecycle.Event.ON_STOP`. Ensure `CallWindowManager`'s overlay lifecycle owner transitions to `ON_STOP` / `ON_DESTROY` when removing the overlay view.