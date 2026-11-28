# `:feature:interceptor` Integration Specification (`v1.0.0`)

## 1. Module Overview, Targets & Dependency Boundaries

`:feature:interceptor` implements the native Android call interception and window-rendering engine on top of `:core:model` (`v1.0.0`), `:core:database` (`v1.0.0`), and `:feature:call-screen` (`v1.0.0`).

* **Supported Targets:** Android (`minSdk = 26`, `compileSdk = 35`, Java 17) and iOS (`iosX64`, `iosArm64`, `iosSimulatorArm64` CallKit-ready stubs).
* **Strict Module Isolation:** `:feature:interceptor` depends **only** on `projects.core.model`, `projects.core.database`, and `projects.feature.callScreen`. It has zero dependencies on `:feature:customizer` or `:composeApp`.
* **Manifest & Permissions:** Declares `USE_FULL_SCREEN_INTENT`, `SYSTEM_ALERT_WINDOW`, `WAKE_LOCK`, `POST_NOTIFICATIONS`, `READ_PHONE_STATE`, `ANSWER_PHONE_CALLS`, `MANAGE_OWN_CALLS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_PHONE_CALL`, `IncomingCallActivity`, `CallNotificationListenerService`, `CustomCallScreeningService`, and `CustomInCallService` in `src/androidMain/AndroidManifest.xml` (merged automatically by AGP).

Downstream modules (`:composeApp` in Block 7) import `:feature:interceptor` via Gradle type-safe project accessors:

```kotlin
// Inside commonMain.dependencies { ... }
implementation(projects.feature.interceptor)
```

---

## 2. Koin Dependency Injection (`com.customcall.feature.interceptor.di`)

Register `interceptorModule` alongside upstream `databaseModule`, `mediaModule`, and `customizerModule` in `:composeApp`'s root Koin graph:

```kotlin
startKoin {
    androidContext(this@CustomCallApplication) // Required on Android
    modules(
        databaseModule,
        mediaModule,
        customizerModule,
        interceptorModule,
    )
}
```

### Exposed Koin Bindings
* `single<CallSessionRegistry>` — Thread-safe in-memory session cache holding `activeSession: StateFlow<ActiveCallSession?>`, pre-resolved `CallThemeConfig`, and platform handles (`VoipNotificationHandle`, `TelecomCallHandle`).
* `single<CallEventDispatcher>` (`DefaultCallEventDispatcher`) — Implements `:core:model`'s `CallEventDispatcher` (`activeCallEvent`, `callState`, `dispatchIncomingCall`, `updateCallState`, `clearActiveCall`).
* `single<CallActionExecutor>` (`PlatformCallActionExecutor`) — Executes captured VoIP notification `PendingIntent` actions or GSM `TelecomManager` / `InCallService` answer and decline commands.
* `single<IncomingCallCoordinator>` *(Android)* — Subscribes to `CallEventDispatcher`, pre-resolves themes via `CallRepository.resolveThemeForCall(event)` (< 5 ms), and routes presentation to `CallNotificationHelper` (`FullScreenIntent` -> `IncomingCallActivity`) when locked or `CallWindowManager` (`TYPE_APPLICATION_OVERLAY`) when unlocked.
* `single<CellularCallStateMonitor>` *(Android)* — Bridges `TelephonyCallback.CallStateListener` (API 31+) and `PhoneStateListener` (API 26–30) for GSM call state tracking.
* `single<VoipNotificationParser>`, `single<CallNotificationHelper>`, and `single<CallWindowManager>` *(Android)*.

---

## 3. Application Startup Hook (`IncomingCallCoordinator`)

In `:composeApp`'s `Application.onCreate()` (immediately after `startKoin { ... }`), start `IncomingCallCoordinator` so incoming VoIP and GSM events are intercepted even when Android wakes the process in the background via a bound system service:

```kotlin
class CustomCallApplication : Application(), KoinComponent {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@CustomCallApplication)
            modules(
                databaseModule,
                mediaModule,
                customizerModule,
                interceptorModule,
            )
        }

        val callCoordinator: IncomingCallCoordinator by inject()
        callCoordinator.start()
    }
}
```

---

## 4. Integration Notes for Block 7 (`:composeApp`)

* **Notification Listener Package Matching:** `CallNotificationListenerService` is declared in `:feature:interceptor`'s `AndroidManifest.xml` using its full class name (`com.customcall.feature.interceptor.voip.CallNotificationListenerService`). AGP automatically registers it under `:composeApp`'s `applicationId`, matching `AndroidSystemDiagnosticController`'s `context.packageName` check in `:feature:customizer`.
* **Runtime Permissions:** Alongside the special permissions guided by `PermissionWizardRoute` (`NotificationListener`, `SYSTEM_ALERT_WINDOW`, `USE_FULL_SCREEN_INTENT` on API 34+, and battery optimization), ensure `:composeApp` requests runtime `READ_PHONE_STATE`, `ANSWER_PHONE_CALLS`, `READ_CONTACTS`, and `POST_NOTIFICATIONS` (API 33+) permissions where applicable.
* **R8 / ProGuard Keep Rules:** Retain `CallNotificationListenerService`, `CustomCallScreeningService`, `CustomInCallService`, and `IncomingCallActivity` in `:composeApp`'s release ProGuard rules so Android system service reflection and `FullScreenIntent` launches remain intact.