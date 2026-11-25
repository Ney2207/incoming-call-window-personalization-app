# Block 2 (`:core:database`) — Formal Inter-Team Handoff Manifest

**Artifact:** `:core:database` (`v1.0.0`)  
**Upstream Contract:** `:core:model` (`v1.0.0` — Frozen, Zero Patches Applied)  
**Downstream Consumers:** Block 3 (`:core:media`), Block 5 (`:feature:customizer`), Block 6 (`:feature:interceptor`), Block 7 (`:composeApp`)  

---

## 1. Handoff Gate Verification (Master Proposal Section 7)

| Gate Criterion | Status | Evidence & Verification |
| :--- | :--- | :--- |
| **1. Contract Freeze** | PASSED | `:core:model` (`v1.0.0`) untouched. `CallRepositoryImpl` implements all 11 frozen methods of `com.customcall.core.model.contract.CallRepository`. |
| **2. Self-Contained Verification** | PASSED | `.\gradlew.bat :core:database:assemble :core:database:testDebugUnitTest` passes (`BUILD SUCCESSFUL`) with zero compiler warnings (`-Xexpect-actual-classes` enabled) and 7/7 unit & `< 5 ms` benchmark tests green. |
| **3. Integration Specification** | PASSED | `core/database/INTEGRATION.md` and `core/database/schemas/com.customcall.core.database.AppDatabase/1.json` generated and verified. |

---

## 2. Included Handoff Artifacts

* `core/database/INTEGRATION.md` — Public Koin bindings (`databaseModule`, `platformDatabaseModule`), Room schema overview, and `CallRepository` resolution semantics.
* `core/database/schemas/com.customcall.core.database.AppDatabase/1.json` — Exported Room v1 schema defining `themes`, `contact_rules` (indexed on `callerIdentifier`, `normalizedName`, `themeId`), and `app_settings`.
* `core/database/build.gradle.kts` & `src/{commonMain,androidMain,iosMain,commonTest}` — Compiled multiplatform module targeting Android (`minSdk = 26`, `compileSdk = 35`) and iOS (`iosX64`, `iosArm64`, `iosSimulatorArm64`).

---

## 3. Data & Storage Contracts Affecting Block 3 (`:core:media`)

1. **Internal Theme Storage Directory (`filesDir/themes/`):**
   * `CallThemeConfig.videoUri` persisted in the `themes` table stores the internal `ProcessedVideoAsset.outputUri` produced by Block 3's `VideoAssetProcessor`.
   * Storing transcoded assets inside private app storage (`filesDir/themes/`) prevents `SecurityException` failures when Block 6 wakes the device from lock screen without SAF URI grants.
2. **Empty `videoUri` Fallback Handling:**
   * When no user theme is configured or an active theme is deleted, `CallRepository.resolveThemeForCall(event)` returns `DefaultThemeProvider.BUILTIN_FALLBACK_THEME` (`id = "builtin_default"`, `videoUri = ""`).
   * Block 3's `VideoBackgroundPlayer` must handle a blank/empty `videoUri` gracefully (no-op / transparent surface without throwing an `ExoPlayer` source exception).
3. **Strict Module Isolation (Section 4 & Section 8 Guardrail):**
   * `:core:media` must **not** add `implementation(projects.core.database)`. Both `:core:database` and `:core:media` are sibling `:core` modules that depend solely on `projects.core.model` and are orchestrated together in `:feature:customizer` (Block 5) and `:composeApp` (Block 7).