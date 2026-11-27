# `:feature:customizer` Integration Specification (`v1.0.0`)

## 1. Module Overview, Targets & Dependency Boundaries

`:feature:customizer` implements the Theme Studio, Contact Rule Manager, and OEM Permission & Survival Diagnostic Wizard on top of `:core:model` (`v1.0.0`), `:core:database` (`v1.0.0`), `:core:media` (`v1.0.0`), and `:feature:call-screen` (`v1.0.0`).

* **Supported Targets:** Android (`minSdk = 26`, `compileSdk = 35`, Java 17) and iOS (`iosX64`, `iosArm64`, `iosSimulatorArm64`).
* **Strict Module Isolation:** `:feature:customizer` depends **only** on `projects.core.model`, `projects.core.database`, `projects.core.media`, and `projects.feature.callScreen`. It has zero dependencies on `:feature:interceptor` or `:composeApp`.
* **Manifest & Permissions:** Declares `android.permission.READ_CONTACTS` and `<queries>` entries for system settings and OEM autostart packages in `src/androidMain/AndroidManifest.xml` (merged automatically by AGP).

Downstream modules (`:composeApp` in Block 7) import `:feature:customizer` via Gradle type-safe project accessors:

```kotlin
// Inside commonMain.dependencies { ... }
implementation(projects.feature.customizer)
```

---

## 2. Koin Dependency Injection (`com.customcall.feature.customizer.di`)

Register `customizerModule` alongside upstream `databaseModule` and `mediaModule` in `:composeApp`'s root Koin graph:

```kotlin
startKoin {
    androidContext(this@CustomCallApplication) // Required on Android
    modules(
        databaseModule,
        mediaModule,
        customizerModule,
        // ... interceptorModule (Block 6)
    )
}
```

### Exposed Koin Bindings
* `viewModel<ThemeStudioViewModel>` — Manages theme drafts, Media3 `VideoAssetProcessor` transcoding, and `CallRepository` theme CRUD.
* `viewModel<ContactRulesViewModel>` — Manages contact-specific rules, `IdentifierNormalizer` canonicalization, and `CallSource?` scoping.
* `viewModel<PermissionWizardViewModel>` — Inspects system capabilities (`NotificationListener`, `Overlay`, `FullScreenIntent` on API 34+, `BatteryOptimization`) and OEM survival guides.
* `single<SystemDiagnosticController>` — Platform-specific diagnostic inspector (`AndroidSystemDiagnosticController` / `IosSystemDiagnosticController`).

---

## 3. Public Composable Entry Points (`com.customcall.feature.customizer.ui`)

Block 7 (`:composeApp`) can embed either the unified multi-tab hub (`CustomizerTab`: `STUDIO`, `CONTACTS`, `PERMISSIONS`) or individual Koin-wired screen routes:

```kotlin
// Unified 3-tab hub (Studio, Contacts, Setup) with automatic permission warning badge
@Composable
fun CustomizerHubScreen(
    modifier: Modifier = Modifier,
    initialTab: CustomizerTab = CustomizerTab.STUDIO,
)

// Individual Koin-wired screen routes for custom navigation graphs
@Composable
fun ThemeStudioRoute(
    modifier: Modifier = Modifier,
    viewModel: ThemeStudioViewModel = koinViewModel(),
)

@Composable
fun ContactRulesRoute(
    modifier: Modifier = Modifier,
    viewModel: ContactRulesViewModel = koinViewModel(),
)

@Composable
fun PermissionWizardRoute(
    modifier: Modifier = Modifier,
    viewModel: PermissionWizardViewModel = koinViewModel(),
)
```

---

## 4. Integration Notes for Block 6 (`:feature:interceptor`) & Block 7 (`:composeApp`)

* **Notification Listener Package Matching (Block 6):** `AndroidSystemDiagnosticController` verifies Notification Listener access by checking `Settings.Secure.getString(contentResolver, "enabled_notification_listeners")` against `context.packageName`. Ensure `CallNotificationListenerService` in Block 6 is declared inside the application package namespace (`:composeApp` / `:feature:interceptor` merged manifest).
* **Video Storage & Persistence Coordination:** `ThemeStudioViewModel` transcodes gallery videos into `filesDir/themes/<themeId>.mp4` via `VideoAssetProcessor` **before** persisting `CallThemeConfig` in `CallRepository`, and removes database rows **before** deleting `.mp4` files on theme deletion.