# Block 5 (`:feature:customizer`) — Formal Inter-Team Handoff Manifest

**Artifact:** `:feature:customizer` (`v1.0.0`)  
**Upstream Contracts:** `:core:model` (`v1.0.0`), `:core:database` (`v1.0.0`), `:core:media` (`v1.0.0`), `:feature:call-screen` (`v1.0.0`)  
**Downstream Consumers:** Block 6 (`:feature:interceptor`), Block 7 (`:composeApp`)  

---

## 1. Handoff Gate Verification (Master Proposal Section 7)

| Gate Criterion | Status | Evidence & Verification |
| :--- | :--- | :--- |
| **1. Contract Freeze** | PASSED | Consumes frozen `:core:model` entities (`CallThemeConfig`, `ContactRule`, `AppSettings`, `IncomingCallEvent`, `CallSource`, `ButtonStyleType`), `IdentifierNormalizer`, `CallRepository`, `VideoAssetProcessor`, and `:feature:call-screen`'s `IncomingCallScreen` without altering upstream contracts. |
| **2. Self-Contained Verification** | PASSED | `./gradlew :feature:customizer:assemble :feature:customizer:testDebugUnitTest` compiles cleanly with zero warnings (`-Xexpect-actual-classes` enabled) and passes all unit tests across `ThemeStudioViewModelTest`, `ContactRulesViewModelTest`, and `PermissionWizardViewModelTest`, plus layoutlib-safe `@Preview` suites in `CustomizerPreviews.kt`. |
| **3. Integration Specification** | PASSED | `feature/customizer/INTEGRATION.md` delivered with `customizerModule` Koin bindings, public route signatures (`CustomizerHubScreen`, `ThemeStudioRoute`, `ContactRulesRoute`, `PermissionWizardRoute`), and manifest permission details. |

---

## 2. Included Handoff Artifacts

* `feature/customizer/INTEGRATION.md` — Public composable routes, Koin DI module bindings, and cross-block integration rules.
* `feature/customizer/HANDOFF_MANIFEST.md` — Gate verification status and cross-block checkpoints.
* `feature/customizer/build.gradle.kts` & `src/{commonMain,androidMain,iosMain,androidDebug,commonTest}` — Compiled multiplatform feature module targeting Android (`minSdk = 26`, `compileSdk = 35`) and iOS (`iosX64`, `iosArm64`, `iosSimulatorArm64`).

---

## 3. Cross-Block Integration Checkpoints

1. **Block 6 (`:feature:interceptor`):**
   * Declare `CallNotificationListenerService` under the final application `packageName` so `AndroidSystemDiagnosticController` accurately detects when the user grants Notification Listener access.
   * Respect `AppSettings.enabledSources` and `AppSettings.customVoipPackages` persisted via `:core:database` (`CallRepository`).
2. **Block 7 (`:composeApp`):**
   * Register `customizerModule` alongside `databaseModule` and `mediaModule` in `startKoin { ... }`.
   * Route top-level navigation to `CustomizerHubScreen()` or individually to `ThemeStudioRoute()`, `ContactRulesRoute()`, and `PermissionWizardRoute()`.