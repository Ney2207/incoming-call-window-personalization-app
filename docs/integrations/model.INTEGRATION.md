# `:core:model` Integration Specification (`v1.0.0` — Frozen Contract)

## 1. Overview & Dependency Setup

`:core:model` is a pure Kotlin Multiplatform (`commonMain`) module targeting Android (`minSdk = 26`, `compileSdk = 35`) and iOS (`iosX64`, `iosArm64`, `iosSimulatorArm64`). It carries zero Android SDK, UIKit, or Compose UI dependencies.

Downstream modules import this artifact via Gradle type-safe project accessors:

```kotlin
// Inside commonMain.dependencies { ... }
implementation(projects.core.model)
```

> **Contract Freeze Policy:** All public types, property names, nullability, default values, and interface signatures in `v1.0.0` are immutable. Any additive change required by a downstream block must be approved as a `v1.0.1` minor contract patch before altering `:core:model`.

---

## 2. Frozen Domain Models (`com.customcall.core.model`)

All domain models and enums are immutable and annotated with `@Serializable`.

### Enumerations (`CallEnums.kt`)
* **`CallSource`**: `PHONE_GSM`, `WHATSAPP`, `WHATSAPP_BUSINESS`, `TELEGRAM`, `OTHER_VOIP`
* **`CallState`**: `RINGING`, `ANSWERED`, `DECLINED`, `ENDED`
* **`ButtonStyleType`**: `CLASSIC_DUAL`, `SWIPE_PILL`, `MINIMAL_GESTURE`

### Core Entities (`CallModels.kt`)
* **`IncomingCallEvent`**
  * `callId: String`, `source: CallSource`, `callerName: String`, `callerIdentifier: String`, `avatarUri: String?`, `hasAnswerAction: Boolean`, `hasDeclineAction: Boolean`, `timestamp: Long`
* **`CallThemeConfig`**
  * `id: String`, `name: String`, `videoUri: String`, `isMuted: Boolean = true`, `overlayGradientAlpha: Float = 0.35f` (enforced `0.0f..1.0f`), `buttonStyle: ButtonStyleType = ButtonStyleType.SWIPE_PILL`, `accentColorHex: Long`
* **`ContactRule`**
  * `id: String`, `callerIdentifier: String`, `normalizedName: String`, `sourceFilter: CallSource?`, `themeId: String`
* **`AppSettings`**
  * `defaultThemeId: String? = null`, `enabledSources: Set<CallSource> = CallSource.entries.toSet()`, `customVoipPackages: Set<String> = emptySet()`, `isOnboardingCompleted: Boolean = false`

### Media Models (`MediaModels.kt`)
* **`VideoProcessingRequest`**: `themeId: String`, `sourceUri: String`, `trimStartMs: Long = 0L`, `trimEndMs: Long? = null`, `maxShortSidePx: Int = 1080`, `targetFps: Int = 30`, `stripAudio: Boolean = false`
* **`ProcessedVideoAsset`**: `themeId: String`, `outputUri: String`, `durationMs: Long`, `width: Int`, `height: Int`, `fileSizeBytes: Long`
* **`VideoProcessingState`** *(sealed interface)*: `InProgress(progress: Float)`, `Completed(asset: ProcessedVideoAsset)`, `Failed(message: String)`

---

## 3. Canonical Normalization (`com.customcall.core.model.util`)

`IdentifierNormalizer` guarantees identical match keys between database writes (Block 2) and live call interception (Block 6):

| Method | Behavior | Example |
| :--- | :--- | :--- |
| `normalizePhoneNumber(raw)` | Strips non-digits while preserving a leading `+` E.164 prefix. | `"+1 (555) 234-5678"` $\rightarrow$ `"+15552345678"` |
| `normalizeDisplayName(raw)` | Trims, collapses internal whitespace, and lowercases. | `"  Alice   M.  "` $\rightarrow$ `"alice m."` |
| `normalizeIdentifier(raw)` | Routes phone-like strings to `normalizePhoneNumber`, otherwise `normalizeDisplayName`. | `"@JohnDoe_Dev"` $\rightarrow$ `"@johndoe_dev"` |
| `phoneNumbersMatch(a, b)` | Compares two raw numbers via `normalizePhoneNumber` (returns `false` if empty). | `true` / `false` |

---

## 4. Boundary Interfaces (`com.customcall.core.model.contract`)

`:core:model` defines interfaces only and declares **no Koin modules**; downstream blocks bind these interfaces in their respective Koin modules:

| Interface | Implementing Block | Primary Responsibility |
| :--- | :--- | :--- |
| **`CallRepository`** | Block 2 (`:core:database`) | Reactive `Flow` observation and CRUD for `CallThemeConfig`, `ContactRule`, and `AppSettings`, plus `suspend fun resolveThemeForCall(event: IncomingCallEvent): CallThemeConfig` executing in **< 5 ms**. |
| **`VideoAssetProcessor`** | Block 3 (`:core:media`) | Transcodes external URIs into internal storage (`filesDir/themes/`), emitting `Flow<VideoProcessingState>`, and manages asset deletion/cleanup. |
| **`CallEventDispatcher`** | Block 6 (`:feature:interceptor`) | Exposes `activeCallEvent: StateFlow<IncomingCallEvent?>` and `callState: StateFlow<CallState>` for UI rendering and dismissal. |
| **`CallActionExecutor`** | Block 6 (`:feature:interceptor`) | Executes `suspend fun answerCall(callId: String): Boolean` and `suspend fun declineCall(callId: String): Boolean` against native GSM or captured VoIP `PendingIntent` actions. |

---

## 5. Block 2 (`:core:database`) Handoff Checklist

1. Map `themes`, `contact_rules`, and `app_settings` Room tables directly to `CallThemeConfig`, `ContactRule`, and `AppSettings`.
2. Index `contact_rules` on `callerIdentifier` and `normalizedName` so `resolveThemeForCall(event)` completes in **under 5 ms** with priority: exact normalized `callerIdentifier` $\rightarrow$ `normalizedName` $\rightarrow$ `AppSettings.defaultThemeId` $\rightarrow$ safe built-in fallback theme.
3. Expose a Koin `databaseModule` binding `single<CallRepository> { ... }`.