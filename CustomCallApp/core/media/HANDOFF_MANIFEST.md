# Block 3 (`:core:media`) — Formal Inter-Team Handoff Manifest

**Artifact:** `:core:media` (`v1.0.0`)  
**Upstream Contract:** `:core:model` (`v1.0.0` — Frozen, Zero Patches Applied)  
**Downstream Consumers:** Block 4 (`:feature:call-screen`), Block 5 (`:feature:customizer`), Block 6 (`:feature:interceptor`), Block 7 (`:composeApp`)  

---

## 1. Handoff Gate Verification (Master Proposal Section 7)

| Gate Criterion | Status | Evidence & Verification |
| :--- | :--- | :--- |
| **1. Contract Freeze** | PASSED | `:core:model` (`v1.0.0`) untouched. `AndroidVideoAssetProcessor` and `IosVideoAssetProcessor` implement all 3 frozen methods of `com.customcall.core.model.contract.VideoAssetProcessor`. |
| **2. Self-Contained Verification** | PASSED | `.\gradlew.bat :core:media:assemble :core:media:testDebugUnitTest` passes (`BUILD SUCCESSFUL`) with zero compiler warnings (`-Xexpect-actual-classes` enabled) and 7/7 unit tests passing, plus on-device `VideoEngineBenchmarkTest` suite in `androidInstrumentedTest`. |
| **3. Integration Specification** | PASSED | `core/media/INTEGRATION.md` generated with complete layout, lifecycle, storage coordination, and Koin DI specifications. |

---

## 2. Included Handoff Artifacts

* `core/media/INTEGRATION.md` — Public Koin bindings (`mediaModule`), `VideoBackgroundPlayer` layout/SurfaceView/Preview rules, `VideoAssetProcessor` transcoding semantics, and `:core:database` coordination order.
* `core/media/build.gradle.kts` & `src/{commonMain,androidMain,iosMain,commonTest,androidUnitTest,androidInstrumentedTest}` — Compiled multiplatform module targeting Android (`minSdk = 26`, `compileSdk = 35`) and iOS (`iosX64`, `iosArm64`, `iosSimulatorArm64`).

---

## 3. Cross-Block Integration Checkpoints

1. **Block 4 (`:feature:call-screen`):**
   * Pass `Modifier.fillMaxSize()` explicitly to `VideoBackgroundPlayer`.
   * Render an opaque dark/gradient fallback background behind `VideoBackgroundPlayer` (for blank `videoUri` fallback themes) and keep upper layers translucent over the hardware `SurfaceView`.
   * Use `videoUri = ""` in `@Preview` composables to avoid hardware `MediaCodec` instantiation in layoutlib.
2. **Block 5 (`:feature:customizer`):**
   * Always wait for `VideoProcessingState.Completed(asset)` and store `asset.outputUri` (`file://.../themes/<themeId>.mp4`) into `CallRepository.upsertTheme(...)`—never persist raw gallery `content://` URIs.
   * Delete from `CallRepository` before calling `VideoAssetProcessor.deleteProcessedAsset(themeId)`.
3. **Block 6 (`:feature:interceptor`) & Block 7 (`:composeApp`):**
   * Register `mediaModule` alongside `databaseModule` inside `startKoin { androidContext(...); modules(...) }`.
   * `VideoBackgroundPlayer` automatically releases `ExoPlayer` decoders on `Lifecycle.Event.ON_STOP`; no extra manifest permissions are required by `:core:media`.