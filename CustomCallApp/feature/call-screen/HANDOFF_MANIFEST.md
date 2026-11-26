# Block 4 (`:feature:call-screen`) — Formal Inter-Team Handoff Manifest

**Artifact:** `:feature:call-screen` (`v1.0.0`)  
**Upstream Contracts:** `:core:model` (`v1.0.0` — Frozen), `:core:media` (`v1.0.0` — Frozen)  
**Downstream Consumers:** Block 5 (`:feature:customizer`), Block 6 (`:feature:interceptor`), Block 7 (`:composeApp`)  

---

## 1. Handoff Gate Verification (Master Proposal Section 7)

| Gate Criterion | Status | Evidence & Verification |
| :--- | :--- | :--- |
| **1. Contract Freeze** | PASSED | Consumes `:core:model` (`IncomingCallEvent`, `CallThemeConfig`, `CallSource`, `CallState`, `ButtonStyleType`) and `:core:media` (`VideoBackgroundPlayer`) strictly as frozen (`v1.0.0`). Zero upstream patches or prohibited `:core:database` / `:feature:*` imports. |
| **2. Self-Contained Verification** | PASSED | `.\gradlew.bat :feature:call-screen:assemble :feature:call-screen:testDebugUnitTest` compiles cleanly with zero warnings (`-Xexpect-actual-classes` enabled) and passes 5/5 unit tests in `CallThemeUtilsTest`, plus 5 layoutlib-safe `@Preview` configurations in `IncomingCallScreenPreviews.kt`. |
| **3. Integration Specification** | PASSED | `feature/call-screen/INTEGRATION.md` delivered with `IncomingCallScreen` signatures, 4-layer z-index architecture, `SurfaceView` / `PixelFormat.TRANSLUCENT` window transparency rules, and embedded Theme Studio preview guidelines. |

---

## 2. Included Handoff Artifacts

* `feature/call-screen/INTEGRATION.md` — Public composable signatures, z-index layer breakdown, `applySystemBarInsets` rules for Theme Studio cards, and `SurfaceView` / `PixelFormat.TRANSLUCENT` requirements for `IncomingCallActivity` and `CallWindowManager`.
* `feature/call-screen/build.gradle.kts` & `src/{commonMain,androidDebug,commonTest}` — Compiled multiplatform UI module targeting Android (`minSdk = 26`, `compileSdk = 35`) and iOS (`iosX64`, `iosArm64`, `iosSimulatorArm64`).
* `feature/call-screen/HANDOFF_MANIFEST.md` — Gate verification status and cross-block integration checkpoints.

---

## 3. Cross-Block Integration Checkpoints

1. **Block 5 (`:feature:customizer`):**
   * Embed `IncomingCallScreen(..., applySystemBarInsets = false)` inside Theme Studio's live preview card.
   * Keep `videoUri = ""` and `isVideoPlaying = false` inside `@Preview` functions to avoid hardware `MediaCodec` instantiation in layoutlib.
2. **Block 6 (`:feature:interceptor`):**
   * Ensure `IncomingCallActivity` uses a transparent window background and `CallWindowManager` uses `PixelFormat.TRANSLUCENT` so `:core:media`'s hardware `SurfaceView` renders cleanly behind the Compose overlay layers.
   * Pass `applySystemBarInsets = true` (default) for full-screen lock-screen and overlay presentation.
3. **Block 7 (`:composeApp`):**
   * `:feature:call-screen` is pure UI with no Koin modules or extra `AndroidManifest.xml` permissions required.