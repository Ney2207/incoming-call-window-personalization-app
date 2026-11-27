# `:core:database` Integration Specification (`v1.0.0`)

## 1. Overview & Dependency Setup

`:core:database` implements local SQLite persistence and sub-5ms call theme resolution on top of `:core:model` (`v1.0.0`). It targets Android (`minSdk = 26`, `compileSdk = 35`) and iOS (`iosX64`, `iosArm64`, `iosSimulatorArm64`) using Room Multiplatform and `BundledSQLiteDriver`.

Downstream feature and application modules import `:core:database` via Gradle type-safe project accessors:

```kotlin
// Inside commonMain.dependencies { ... }
implementation(projects.core.database)
```

---

## 2. Koin Dependency Injection (`com.customcall.core.database.di`)

Register `databaseModule` in the root Koin graph (`:composeApp` in Block 7). `databaseModule` automatically includes the platform-specific `platformDatabaseModule` (`androidMain` / `iosMain`):

```kotlin
startKoin {
    androidContext(this@CustomCallApplication) // Required on Android
    modules(
        databaseModule,
        // ... downstream feature modules
    )
}
```

### Exposed Koin Bindings
* `single<AppDatabase>` — Singleton Room database (`custom_call.db`) backed by `BundledSQLiteDriver`.
* `single<ThemeDao>`, `single<ContactRuleDao>`, `single<AppSettingsDao>` — Room DAOs for `themes`, `contact_rules`, and `app_settings`.
* `single<ThemeRuleResolver>` — Read-through in-memory lookup engine (`warmUp()` available for proactive preloading).
* `single<CallRepository>` — Concrete `CallRepositoryImpl` bound to the frozen `:core:model` contract.

---

## 3. Database Schema (`AppDatabase`, `version = 1`)

* **`themes`**: Stores `CallThemeConfig` records keyed by `id`.
* **`contact_rules`**: Stores `ContactRule` mappings indexed on `callerIdentifier`, `normalizedName`, and `themeId`.
* **`app_settings`**: Single-row configuration table (`id = 1`) storing `defaultThemeId`, `enabledSources`, `customVoipPackages`, and `isOnboardingCompleted`.
* **Exported Schema:** `core/database/schemas/com.customcall.core.database.AppDatabase/1.json`.

---

## 4. `CallRepository` Contract & Resolution Guarantees

Consumers inject `com.customcall.core.model.contract.CallRepository`:

* **Reactive Streams:** `observeThemes()`, `observeContactRules()`, and `observeAppSettings()` emit automatically whenever underlying tables change.
* **Themes CRUD:** `getThemeById(themeId)`, `upsertTheme(theme)`, and `deleteTheme(themeId)` (which cascades deletion to dependent `ContactRule` rows and clears `AppSettings.defaultThemeId` if it matched `themeId`).
* **Contact Rules CRUD:** `upsertContactRule(rule)` (canonicalizing `callerIdentifier` and `normalizedName` via `IdentifierNormalizer` prior to persistence) and `deleteContactRule(ruleId)`.
* **Settings Management:** `updateAppSettings(settings)` and `setDefaultThemeId(themeId)`.
* **Sub-5ms Call Theme Resolution:** `suspend fun resolveThemeForCall(event: IncomingCallEvent): CallThemeConfig` executes from an atomic in-memory snapshot in **< 5 ms** using strict priority order:
  1. Exact normalized `callerIdentifier` match (source-specific `sourceFilter == event.source` preferred over wildcard `null`).
  2. Normalized `callerName` match (source-specific `sourceFilter == event.source` preferred over wildcard `null`).
  3. Global fallback theme referenced by `AppSettings.defaultThemeId`.
  4. `DefaultThemeProvider.BUILTIN_FALLBACK_THEME` (`id = "builtin_default"`, `videoUri = ""`, `isMuted = true`, `buttonStyle = ButtonStyleType.SWIPE_PILL`).