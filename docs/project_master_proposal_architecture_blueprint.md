# 📐 Project Master Proposal & Technical Specification

### Cross-Platform Custom Video Incoming Call Screen Engine (`CustomCall`)

| Document Metadata | Details | 
| ----- | ----- | 
| **Project Name** | CustomCall | 
| **Author & Copyright** | © 2026 Ney Cordovéz ([Custom Software License](../LICENSE.md)) | 
| **Architecture** | Clean Architecture + Kotlin Multiplatform (KMP) & Compose Multiplatform (CMP) | 
| **Target Platforms** | Android (API 26–36+) & iOS-Ready (`iosMain` portable) | 
| **Execution Model** | 7-Block Contract-First Serial Pipeline | 

## 📑 Table of Contents

1. [Executive Summary & Objectives](#1-executive-summary--objectives)

2. [Technical Feasibility & Platform Realities](#2-technical-feasibility--platform-realities)

3. [Planned Functionalities](#3-planned-functionalities)

4. [Prerequisites, Non-Functional Requirements & Guardrails](#4-prerequisites-non-functional-requirements--guardrails)

5. [Technology Stack](#5-technology-stack)

6. [Modular Repository Architecture](#6-modular-repository-architecture)

7. [Development Methodology & Serial Execution Workflow](#7-development-methodology--serial-execution-workflow)

8. [Detailed Block-by-Block Execution Plan](#8-detailed-block-by-block-execution-plan)

9. [Team Onboarding Instructions](#9-team-onboarding-instructions)

## 1. Executive Summary & Objectives

This document serves as the master architectural blueprint, technical specification, and operational execution plan for building a lightweight, modular **Custom Incoming Call Screen Application**. The application intercepts incoming calls from standard cellular networks (GSM/SIM) and third-party VoIP platforms (WhatsApp, Telegram, and extensible VoIP providers) to render a hardware-accelerated, customizable video call screen in place of or over the default system interface.

### Core Objectives

* **Universal Call Interception:** Capture incoming call events, caller metadata, and answer/decline actions across cellular telephony and major VoIP apps (WhatsApp, WhatsApp Business, Telegram).

* **Sub-200ms Video Rendering:** Display user-selected looping videos on incoming calls with near-zero startup latency, smooth frame pacing, and minimal RAM/battery overhead.

* **Granular Personalization:** Allow users to configure a global default video theme, assign custom videos and UI themes to specific contacts, and choose between multiple interactive button layouts.

* **Modern & Legacy Android Compatibility:** Support Android 8.0 (API 26) through the latest Android releases (Android 14–16+ / API 34–36+), handling strict background execution limits, full-screen intent permissions, and OEM battery restrictions (e.g., HyperOS/MIUI, One UI).

* **Future-Ready iOS Portability:** Architect the codebase using **Kotlin Multiplatform (KMP)** and **Compose Multiplatform (CMP)** so that **85% to 90%** of the codebase (domain logic, persistence, video UI, theme customizer) compiles directly to iOS without rewriting business logic or screens.

## 2. Technical Feasibility & Platform Realities

> \[!NOTE\]
> Each mobile operating system enforces strict security boundaries around telephony and notifications. All engineering teams must design strictly within the platform constraints outlined below.

| Call Source / Platform | Technical Mechanism | Capabilities & Platform Constraints | 
| ----- | ----- | ----- | 
| **Android: Cellular (SIM / GSM)** | `CallScreeningService` + `TelephonyCallback` (API 31+) / `PhoneStateListener` (API 26–30), or `InCallService` (`ROLE_DIALER`) | Full detection of `RINGING`, `OFFHOOK`, and `IDLE` states. `InCallService` enables complete replacement of the system dialer UI; `CallScreeningService` + overlay enables lightweight interception without replacing the dialer. | 
| **Android: VoIP (WhatsApp, Telegram)** | `NotificationListenerService` + `PendingIntent` Action Capture | Android restricts third-party VoIP call interception via Telecom APIs. The service monitors notifications tagged with `Notification.CATEGORY_CALL`, extracts caller metadata, captures the original "Answer" and "Decline" `PendingIntent` actions in memory, and triggers them from the custom UI. | 
| **Android: Screen Wake & Overlay** | `FullScreenIntent` + `SYSTEM_ALERT_WINDOW` | Android 14+ requires explicit `USE_FULL_SCREEN_INTENT` verification (`canUseFullScreenIntent()`). Lock-screen wake requires `setShowWhenLocked(true)` and `setTurnScreenOn(true)` on the call Activity. When the device is already unlocked, `TYPE_APPLICATION_OVERLAY` ensures the custom UI renders above heads-up notifications. | 
| **iOS (Future Target)** | `CallKit` + `PushKit` / `CXCallDirectoryProvider` | Apple's sandbox prevents any third-party app from reading WhatsApp/Telegram notifications or intercepting carrier GSM screens. On iOS, the shared KMP/CMP layers will power custom VoIP call screens, simulated calls, and caller ID extensions via CallKit bridges. | 

## 3. Planned Functionalities

1. **Multi-Source Incoming Call Detection**

   * Real-time detection of incoming cellular calls, WhatsApp, WhatsApp Business, and Telegram calls.

   * Automatic dismissal of the custom call screen when the caller hangs up, the call is answered elsewhere, or the notification is removed.

2. **Hardware-Accelerated Video Background Engine**

   * Seamless, gapless infinite video looping (`REPEAT_MODE_ONE`) behind caller details.

   * Automatic media ingestion pipeline that trims, downscales (maximum 1080p at 30 fps), and transcodes user-selected gallery videos into private internal app storage (`filesDir/themes/`) to guarantee instant loading and prevent `SecurityException` errors when waking from a locked screen.

3. **Customizable Call Themes & Controls**

   * Configurable background audio state (muted by default so it does not clash with the device ringtone, with an option to use the video's audio track).

   * Adaptive vertical gradient overlays to ensure high text contrast over bright or dark videos.

   * Selectable interaction styles: Classic Dual Buttons (`CLASSIC_DUAL`), Swipe-to-Answer Pill (`SWIPE_PILL`), and Minimalist Gesture Controls (`MINIMAL_GESTURE`), plus customizable accent colors.

4. **Contact-Specific Rule Engine**

   * Ability to bind a dedicated video and theme configuration to individual contacts (matched via normalized phone number or VoIP display name/identifier), overriding the global theme in **under 5 ms** upon call arrival.

5. **Guided Permission & OEM Survival Diagnostic Center**

   * Interactive onboarding screen verifying real-time status for Notification Listener access, Display Over Other Apps (`SYSTEM_ALERT_WINDOW`), Full-Screen Intent permission (Android 14+), Battery Optimization exemption, and manufacturer-specific autostart settings.

## 4. Prerequisites, Non-Functional Requirements & Guardrails

> \[!IMPORTANT\]
> Violating any architectural guardrail below is grounds for rejecting a block during handoff verification.

* **Cold-Start Rendering Latency:** The incoming call screen must initialize and render its first video frame in **under 200 ms** so the native OS or VoIP call screen does not flash visibly beforehand.

* **Memory & Thermal Efficiency:** Video decoding must use hardware surfaces (`SurfaceView` over `TextureView`) and release all player/decoder instances immediately upon `ON_STOP` or call termination.

* **Strict Dependency Hierarchy:** Dependencies must flow strictly upward (`:composeApp` → `:feature:*` → `:core:*`). No `:core` module may ever import classes from a `:feature` module. Circular dependencies are strictly prohibited.

* **Platform Isolation:** Shared modules (`:core:model`, `:core:database`, `:feature:call-screen`, `:feature:customizer`) must keep platform-specific APIs behind KMP `expect`/`actual` declarations or pure Kotlin interfaces.

## 5. Technology Stack

| Layer | Technology Choice | Purpose | 
| ----- | ----- | ----- | 
| **Language & UI** | Kotlin 2.x + Compose Multiplatform (CMP) | Shared declarative UI across Android and future iOS targets. | 
| **Architecture & Async** | Clean Architecture + Kotlin Coroutines & `StateFlow` | Reactive, non-blocking event propagation from OS interceptors to UI. | 
| **Dependency Injection** | Koin (Multiplatform) | Decoupled wiring of interfaces in `:core` to implementations in `:feature` and `:composeApp`. | 
| **Local Persistence** | Room Multiplatform (or SQLDelight) | Indexed local SQLite storage for themes, contact rules, and app preferences. | 
| **Media Playback & Processing** | AndroidX Media3 (`ExoPlayer` + `Transformer`) / `AVQueuePlayer` (iOS) | Hardware-accelerated video looping and background asset transcoding. | 
| **Android System Interception** | `NotificationListenerService`, `CallScreeningService`, `TelephonyCallback`, `WindowManager` | Native capture of VoIP notifications, GSM call states, and lock-screen rendering. | 

## 6. Modular Repository Architecture

```
CustomCallApp/
├── composeApp/                  # [Block 7] Root application, Koin DI graph, Navigation & Manifest
├── core/
│   ├── model/                   # [Block 1] Pure KMP domain entities, enums & boundary interfaces
│   ├── database/                # [Block 2] KMP database schema, DAOs & CallRepository implementation
│   └── media/                   # [Block 3] VideoBackgroundPlayer composable & VideoAssetProcessor
└── feature/
    ├── call-screen/             # [Block 4] Full-screen incoming call CMP UI & control styles
    ├── customizer/              # [Block 5] Theme editor, contact picker & OEM permission wizard
    └── interceptor/             # [Block 6] Android NotificationListener, GSM detector & WindowManager

```

## 7. Development Methodology & Serial Execution Workflow

This project is structured for **seven independent teams working in a strict sequential pipeline (Contract-First Serial Execution)**. No team starts development until the preceding block delivers its compiled, tested artifact. This eliminates mock drift, integration bottlenecks, and rework.

### Sequential Pipeline Diagram

```
flowchart TD
    B1["<b>Block 1: :core:model</b><br/>Core Architecture Team"] -->|"Frozen Domain Models & Interfaces"| B2["<b>Block 2: :core:database</b><br/>Data & Storage Team"]
    B1 -->|"Frozen Domain Models & Interfaces"| B3["<b>Block 3: :core:media</b><br/>Multimedia & Performance Team"]

    B3 -->|"VideoBackgroundPlayer Composable"| B4["<b>Block 4: :feature:call-screen</b><br/>UI / Compose Design Team"]
    B1 --> B4

    B2 -->|"Compiled CallRepository"| B5["<b>Block 5: :feature:customizer</b><br/>Product & User Flows Team"]
    B3 -->|"VideoAssetProcessor"| B5
    B4 -->|"IncomingCallScreen Preview"| B5

    B2 -->|"Compiled CallRepository"| B6["<b>Block 6: :feature:interceptor</b><br/>Android Systems & Low-Level SDK Team"]
    B4 -->|"IncomingCallScreen UI"| B6
    B5 -.->|"Permission Diagnostics Ready"| B6

    B5 --> B7["<b>Block 7: :composeApp</b><br/>Integration, QA & Release Engineering"]
    B6 -->|"Native Services & Lock-Screen Activity"| B7

```

### Mandatory Inter-Team Handoff Protocol

Every team must satisfy three criteria before handing off their block to the next team:

1. **Contract Freeze:** Public interfaces and models defined in Block 1 are immutable. If a downstream team uncovers a missing field, a minor version patch (`v1.0.1`) must be approved and published to `:core:model` before proceeding.

2. **Self-Contained Verification:** The block must compile cleanly with zero warnings and pass 100% of its unit or instrumented tests.

3. **1-Page Integration README:** Each team delivers a concise `INTEGRATION.md` inside their module directory documenting exposed public classes, Koin module definitions, and usage signatures for the next team.

## 8. Detailed Block-by-Block Execution Plan

### Block 1: Core Domain Models & Boundary Contracts (`:core:model`)

* **Assigned Team:** Core Architecture Team

* **Required Inputs:** None (Project initialization block).

* **Implementation Scope:**

  * Initialize the Gradle KMP/CMP multi-module project structure and centralized `libs.versions.toml` dependency catalog.

  * Implement pure Kotlin immutable domain models:

    ```
    enum class CallSource { PHONE_GSM, WHATSAPP, WHATSAPP_BUSINESS, TELEGRAM, OTHER_VOIP }
    
    enum class CallState { RINGING, ANSWERED, DECLINED, ENDED }
    
    enum class ButtonStyleType { CLASSIC_DUAL, SWIPE_PILL, MINIMAL_GESTURE }
    
    data class IncomingCallEvent(
        val callId: String,
        val source: CallSource,
        val callerName: String,
        val callerIdentifier: String,
        val avatarUri: String?,
        val hasAnswerAction: Boolean,
        val hasDeclineAction: Boolean,
        val timestamp: Long
    )
    
    data class CallThemeConfig(
        val id: String,
        val name: String,
        val videoUri: String,
        val isMuted: Boolean = true,
        val overlayGradientAlpha: Float = 0.35f,
        val buttonStyle: ButtonStyleType = ButtonStyleType.SWIPE_PILL,
        val accentColorHex: Long
    )
    
    data class ContactRule(
        val id: String,
        val callerIdentifier: String,
        val normalizedName: String,
        val sourceFilter: CallSource?,
        val themeId: String
    )
    
    ```

  * Define strict boundary interfaces for downstream teams: `CallRepository`, `VideoAssetProcessor`, `CallEventDispatcher`, and `CallActionExecutor`.

* **Deliverables (Output to Block 2):** Compiled `:core:model` artifact (`v1.0.0`), KDoc documentation, `core/model/INTEGRATION.md`, and frozen interface contracts.

* **Validation Criteria:** `commonTest` suite verifying model serialization, equality, and normalization helpers.

### Block 2: Local Persistence & Rule Resolution Engine (`:core:database`)

* **Assigned Team:** Data & Storage Team

* **Required Inputs:** Compiled `:core:model` module from Block 1.

* **Implementation Scope:**

  * Configure Room Multiplatform (or SQLDelight) targeting Android and iOS.

  * Create three indexed tables:

    * `themes`: Stores `CallThemeConfig` records and internal file paths.

    * `contact_rules`: Stores `ContactRule` mappings indexed by `callerIdentifier` and `normalizedName`.

    * `app_settings`: Stores global default theme ID, enabled call sources (GSM, WhatsApp, Telegram), and onboarding completion flags.

  * Implement `CallRepository` providing reactive `Flow` streams for UI lists and a high-speed lookup method `suspend fun resolveThemeForCall(event: IncomingCallEvent): CallThemeConfig` that prioritizes contact-specific rules over the global fallback theme.

* **Deliverables (Output to Blocks 3, 5, and 6):** Compiled `:core:database` module with schema migrations, Koin database module definition, and `core/database/INTEGRATION.md`.

* **Validation Criteria:** Unit tests confirming rule resolution executes in **under 5 ms** and handles fallback logic accurately when no contact rule matches.

### Block 3: Ultralight Video Engine & Asset Processor (`:core:media`)

* **Assigned Team:** Multimedia & Performance Team

* **Required Inputs:** Compiled `:core:model` module from Block 1.

* **Implementation Scope:**

  * Implement the multiplatform `@Composable fun VideoBackgroundPlayer(videoUri: String, isPlaying: Boolean, isMuted: Boolean, modifier: Modifier)` component.

  * **Android Implementation:** Use Media3 `ExoPlayer` configured with `Player.REPEAT_MODE_ONE`, hardware-backed `SurfaceView` rendering, and pre-buffering for sub-200ms first-frame display. Ensure deterministic lifecycle cleanup to prevent decoder leaks.

  * **iOS Stub/Bridge:** Prepare `expect`/`actual` structure ready for `AVQueuePlayer` + `AVPlayerLooper` inside `UIKitView`.

  * **Asset Pre-Processing (`VideoAssetProcessor`):** Build a background transcoding service (using Media3 `Transformer`) that accepts a user's raw gallery URI, trims/scales it (up to 1080p, 30 fps, H.264/HEVC), and saves it into internal private storage (`filesDir/themes/`).

* **Deliverables (Output to Blocks 4 and 5):** Compiled `:core:media` module exposing `VideoBackgroundPlayer` and `VideoAssetProcessor`, plus `core/media/INTEGRATION.md`.

* **Validation Criteria:** Benchmark test verifying seamless video looping without black frames between cycles, stable memory footprint, and successful internal file transcoding.

### Block 4: Custom Incoming Call Screen UI (`:feature:call-screen`)

* **Assigned Team:** UI / Compose Design Team

* **Required Inputs:**

  * Compiled `:core:model` (Block 1)

  * Compiled `:core:media` (Block 3)

* **Implementation Scope:**

  * Build the edge-to-edge full-screen Compose interface (`IncomingCallScreen(event, theme, onAnswer, onDecline)`).

  * Structure the screen into four distinct z-index layers inside a full-viewport `Box`:

    1. **Base Layer:** `VideoBackgroundPlayer` from Block 3 filling 100% of screen bounds.

    2. **Contrast Layer:** Configurable top and bottom vertical scrim gradients (`overlayGradientAlpha`) guaranteeing WCAG-compliant readability over any video luminance.

    3. **Caller Identity Layer:** Circular contact avatar (with monogram fallback), call-source badge (Phone, WhatsApp, Telegram icon), caller display name, and animated ringing status indicator.

    4. **Interactive Controls Layer:** Pluggable control components supporting `CLASSIC_DUAL`, `SWIPE_PILL`, and `MINIMAL_GESTURE` styles, emitting clean `onAnswer()` and `onDecline()` callbacks.

  * Create interactive Compose `@Preview` suites simulating different themes, button styles, and call sources.

* **Deliverables (Output to Blocks 5 and 6):** Compiled `:feature:call-screen` module ready for live preview embedding and lock-screen mounting, plus `feature/call-screen/INTEGRATION.md`.

* **Validation Criteria:** Smooth 60/120 fps rendering across screen aspect ratios with zero UI jank during state transitions.

### Block 5: Theme Customizer, Contact Rules & OEM Permission Wizard (`:feature:customizer`)

* **Assigned Team:** Product & User Flows Team

* **Required Inputs:**

  * Compiled `:core:model` (Block 1)

  * Compiled `:core:database` (Block 2)

  * Compiled `:core:media` (Block 3)

  * Compiled `:feature:call-screen` (Block 4)

* **Implementation Scope:**

  * **Theme Studio:** Gallery video picker (`PickVisualMedia`), video trim/mute/gradient/accent-color controls, and a real-time interactive preview rendering Block 4's `IncomingCallScreen`. Saves processed videos via Block 3 and persists configurations via Block 2.

  * **Contact Rule Manager:** Integration with Android `ContactsContract` (and manual VoIP handle input) allowing users to assign custom themes to specific contacts.

  * **OEM Permission & Survival Diagnostic Dashboard:** Real-time status checklist and deep-link launchers for:

    * Notification Listener Access (`Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`)

    * Display Over Other Apps (`Settings.ACTION_MANAGE_OVERLAY_PERMISSION`)

    * Full-Screen Intent Permission (`Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT` on Android 14+)

    * Battery Optimization Exemption (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) + manufacturer-specific autostart instructions (HyperOS/MIUI, One UI, ColorOS).

* **Deliverables (Output to Blocks 6 and 7):** Compiled `:feature:customizer` module containing all user-facing configuration flows and permission onboarding screens, plus `feature/customizer/INTEGRATION.md`.

* **Validation Criteria:** End-to-end user flow test: selecting a gallery video, customizing a theme, binding it to a contact, verifying persistence in Room, and toggling system permissions.

### Block 6: Native Android Call Interception Engine (`:feature:interceptor`)

* **Assigned Team:** Android Systems & Low-Level SDK Team

* **Required Inputs:**

  * Compiled `:core:model` (Block 1)

  * Compiled `:core:database` (Block 2)

  * Compiled `:feature:call-screen` (Block 4)

* **Implementation Scope:**

  * **VoIP Notification Interceptor (`CallNotificationListenerService`):**

    * Extends `NotificationListenerService`, filtering target packages (`com.whatsapp`, `com.whatsapp.w4b`, `org.telegram.messenger`, and extensible package list from `app_settings`).

    * Detects `Notification.CATEGORY_CALL` notifications, extracts caller name/avatar, caches the notification's Answer and Decline `PendingIntent` instances in an in-memory `CallSessionRegistry`, and implements `CallActionExecutor` to invoke them when the user interacts with the custom UI.

    * Listens to `onNotificationRemoved` to emit `CallState.ENDED` and dismiss the custom UI immediately.

  * **Cellular Call Interceptor:**

    * Combines `CallScreeningService` (Android 10+) with `TelephonyCallback` (Android 12+) / `PhoneStateListener` (Android 8–11) to capture `CALL_STATE_RINGING` and `CALL_STATE_IDLE` without forcing dialer replacement (with optional `InCallService` support for full dialer mode).

  * **Lock-Screen & Overlay Window Controller (`IncomingCallActivity` & `CallWindowManager`):**

    * Queries `CallRepository.resolveThemeForCall(event)` upon call detection.

    * Launches `IncomingCallActivity` via a high-priority `FullScreenIntent` notification configured with `setShowWhenLocked(true)`, `setTurnScreenOn(true)`, and `FLAG_KEEP_SCREEN_ON`, mounting Block 4's `IncomingCallScreen`.

    * Uses `WindowManager` (`TYPE_APPLICATION_OVERLAY`) when the device is already unlocked to render reliably above heads-up notifications.

* **Deliverables (Output to Block 7):** Compiled `:feature:interceptor` module with declared Android services, receiver intents, `CallActionExecutor` implementation, and `feature/interceptor/INTEGRATION.md`.

* **Validation Criteria:** Live device verification on Android 10 through Android 16+ receiving real GSM, WhatsApp, and Telegram calls in both locked-screen (deep sleep) and unlocked states, successfully answering and declining calls.

### Block 7: Application Assembly, Dependency Injection & Release (`:composeApp`)

* **Assigned Team:** Integration, QA & Release Engineering Team

* **Required Inputs:** All compiled modules from Blocks 1 through 6 (`:core:model`, `:core:database`, `:core:media`, `:feature:call-screen`, `:feature:customizer`, `:feature:interceptor`).

* **Implementation Scope:**

  * Configure the root Koin dependency graph binding all interfaces from `:core:model` to their concrete implementations across `:core:database`, `:core:media`, and `:feature:interceptor`.

  * Assemble the top-level navigation graph connecting the Home Dashboard, Theme Studio, Contact Rules, and Permission Diagnostic Wizard.

  * Finalize `AndroidManifest.xml` permissions, service declarations (`BIND_NOTIFICATION_LISTENER_SERVICE`, `BIND_CALL_SCREENING_SERVICE`, `FOREGROUND_SERVICE_PHONE_CALL`), and R8/ProGuard keep rules.

  * Execute full regression and stress testing (rapid back-to-back calls, low-memory conditions, battery-saver mode, screen rotation, and decoder cleanup).

* **Deliverables (Final Output):** Production-ready signed APK/AAB and verified multi-module repository ready for future `iosMain` target integration.

## 9. Team Onboarding Instructions

When your team receives this specification along with your **Assigned Block Number** and the **Input Artifacts** from the preceding block(s):

1. **Review Section 4 & Section 7:** Ensure your module's `build.gradle.kts` imports *only* the specified upstream `:core` or `:feature` modules listed in your block's **Required Inputs**.

2. **Adhere Strictly to Block 1 Contracts:** Implement or consume the exact data classes and interfaces defined in `:core:model`. Do not alter public signatures without triggering a formal contract patch.

3. **Build & Verify Locally:** Complete all items in your block's **Implementation Scope** and verify every item in **Validation Criteria** using automated tests and sample/device runs.

4. **Package Your Handoff:** Deliver your compiled module artifact alongside a 1-page `INTEGRATION.md` inside your module root summarizing your public entry points and Koin module bindings so the next team in the sequence can begin immediately.