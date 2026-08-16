# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Native Android app (Kotlin, XML views + ViewBinding — **no Jetpack Compose, no Navigation Component**).
Single Gradle module `:app`. Gradle 9.4.1 / AGP 9.2.1 / minSdk 24 / targetSdk 36 / compileSdk 36.1.

Fragments are avoided everywhere except `SettingsActivity`, which hosts a `PreferenceFragmentCompat`
(`SettingsActivity.SettingsFragment` → `res/xml/root_preferences.xml`) — the one framework-mandated exception.

Namespace and applicationId: `com.ntp.application_ai_assisstant` — the misspelling ("Assisstant") is baked into the
package name, `rootProject.name`, generated binding classes, and theme names. Keep it; do not "fix" it.

Code comments, log messages, and user-facing strings are written in **Vietnamese**. Match that when editing.

## Commands

Run from the repo root. On Windows/PowerShell use `.\gradlew.bat`; the Makefile targets assume a POSIX shell.

```powershell
.\gradlew.bat assembleDebug                # build debug APK
.\gradlew.bat installDebug                 # build + install on connected device/emulator
.\gradlew.bat lint                         # Android Lint -> app/build/reports/lint-results-debug.html
.\gradlew.bat test                         # JVM unit tests
.\gradlew.bat connectedAndroidTest         # instrumented tests (needs device/emulator)
.\gradlew.bat clean
```

Single unit test:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.ntp.application_ai_assisstant.ExampleUnitTest"
.\gradlew.bat :app:testDebugUnitTest --tests "*.ExampleUnitTest.addition_isCorrect"
```

Only the two IDE-generated sample tests exist (`ExampleUnitTest`, `ExampleInstrumentedTest`); there is no real test
coverage to extend yet.

### Build status

The two resource-processing failures that used to block every build (`@style/MultiSelectChip` undefined;
`activity_login.xml` variants disagreeing on the root element's ID) are **fixed** — don't go looking for them.
`:app:processDebugResources` and `:app:dataBindingGenBaseClassesDebug` both pass, and `assembleDebug` has produced a
debug APK end-to-end. Failures past that point are ordinary Kotlin compile errors in whatever is being worked on,
not the old structural breakage.

Note the second one is a standing trap rather than a one-off bug: every variant of a layout must agree on the root
element's ID, and disagreeing fails `:app:dataBindingGenBaseClassesDebug` outright. See the ViewBinding section.

Caveat: `make format` calls `./gradlew ktlintFormat`, but no ktlint plugin is applied in any build script — that task
does not exist. Formatting is enforced only by `.editorconfig` (4-space indent, 120-col max, final newline,
wildcard imports allowed). Adding ktlint means adding the plugin to `app/build.gradle.kts` + the version catalog.

Gradle configuration cache is on (`org.gradle.configuration-cache=true`), so build-script changes force a
configuration re-run; keep build logic configuration-cache safe.

Two Java versions are in play and are not the same knob: `gradle/gradle-daemon-jvm.properties` pins the **daemon**
toolchain to JDK 21 (foojay auto-provisioning via the resolver plugin in `settings.gradle.kts`), while
`compileOptions` sets source/target compatibility to **Java 11** for app bytecode. Changing one does not change
the other.

## Dependencies

Declare versions in `gradle/libs.versions.toml` and reference them via `libs.*` in `app/build.gradle.kts`.
A few deps (splashscreen, biometric, play-services-auth) are currently hardcoded coordinate strings — prefer the
catalog for anything new.

The Kotlin plugin is **not** applied explicitly; AGP 9 provides built-in Kotlin support. Don't add
`org.jetbrains.kotlin.android` unless you also rework the plugin setup.

Two things compile only through transitive deps and have no direct declaration: **kotlinx-coroutines**
(`lifecycleScope`, `StateFlow` — via `lifecycle-*-ktx`) and **RecyclerView** (used in `activity_discovery.xml` /
`activity_schedule.xml` — via `material`). If you start depending on them heavily, declare them explicitly rather
than relying on the transitive graph.

## Architecture

### Navigation: one Activity per screen, no fragments

The three main tabs are separate Activities (`ui/discovery`, `ui/schedule`, `ui/personal`). All navigation goes
through `util/AppRouter.kt`, a hand-written stand-in for a nav graph: **`AppRouter` is the only place allowed to
call `startActivity`**. If you want to know where the app can go, read that one file. Don't add a bare
`startActivity` in an Activity — add a route function to the router.

Its public surface:

- `bind(activity, bottomNavigationView, currentItemId)` — wires a tab up; the extension
  `AppCompatActivity.setupBottomNavigation(bottomNavigationView, currentItemId)` at the bottom of the same file is
  the shorthand the Activities actually call in `onCreate`.
- `openTab(from, itemId)` — tab switch; returns `false` for an id outside the route table, which is what tells
  `BottomNavigationView` not to select the item.
- `openHomeAfterLogin(from)` / `logout(from)` — enter and leave the logged-in area, both
  `NEW_TASK or CLEAR_TASK` + `finish()` so Back can't cross the auth boundary.
- `openSettings(from)` — ordinary push; Back returns to the caller.

Tabs **keep their state**. `openTab` uses `Intent.FLAG_ACTIVITY_REORDER_TO_FRONT` and does *not* `finish()` the
current Activity, so a tab already in the task is raised without being recreated (scroll position, entered text,
RecyclerView state all survive); only the first visit constructs it. This depends on
`android:launchMode="singleTop"` on all three tab `<activity>` entries — that attribute is load-bearing, not
decoration.

Because `REORDER_TO_FRONT` scrambles stack order, Back is handled explicitly rather than left to the system:
`setupTabBackBehavior` registers an `onBackPressedDispatcher` callback on non-root tabs that returns to
`AppRouter.START_TAB_ID` (`R.id.nav_discovery`, also the post-login landing screen). The root tab registers nothing,
so Back there exits the app — the Material bottom-nav contract.

Tab transition animations are suppressed on both sides of an API split, and the halves are not interchangeable:
`overridePendingTransition(0, 0)` after `startActivity` below API 34, `overrideActivityTransition(...)` inside the
*opened* Activity's `onCreate` at 34+ (`applyTabTransition`, called from `bind`).

Adding a tab screen: write the Activity (layout with a `BottomNavigationView`, `setupBottomNavigation` in
`onCreate`), then three registration edits — a row in `AppRouter.TABS`, a matching `<item>` in
`res/menu/bottom_nav_menu.xml`, and an `<activity ... launchMode="singleTop">` entry in `AndroidManifest.xml`.
The existing tab Activities need no changes; there is no `when` branch to extend.

`SettingsActivity` and `ScrollingActivity` sit outside the tab system: neither has a bottom nav, and
`SettingsActivity` uses plain `setContentView(R.layout.settings_activity)` rather than ViewBinding. `SettingsActivity`
is reachable via `AppRouter.openSettings` (from `PersonalActivity`); `ScrollingActivity` still has no call site.

### Login flow (the only fully-wired MVVM slice)

`LoginActivity` is the launcher, themed with the splash screen (`Theme.App.Starting` → `installSplashScreen()`).
`LoginViewModel` (via `LoginViewModelFactory`) → `LoginRepository` (in-memory user cache, no persistence) →
`LoginDataSource`. `LoginDataSource.login()` is a **stub**: it ignores credentials and returns a random-UUID
"Jane Doe". Login succeeds → `AppRouter.openHomeAfterLogin` → `DiscoveryActivity`.

State is exposed as `LiveData` (`loginFormState`, `loginResult`) observed in the Activity; UI models are
`LoginFormState` / `LoginResult` / `LoggedInUserView` (string-resource IDs for errors, not messages).

`data/Result.kt` defines a project-local sealed `Result<T>` that **shadows `kotlin.Result`** — always
`import com.ntp.application_ai_assisstant.data.Result` where you use it.

`Discovery`, `Schedule`, and `Personal` are scaffolds: inflate binding, wire bottom nav, `// TODO`. `Personal`
additionally hooks `tvSettings` / `btnLogout` to `AppRouter.openSettings` / `AppRouter.logout`. Their layouts
already contain a `RecyclerView` and there are item layouts (`item_discovery_card.xml`, `item_schedule.xml`), but no
adapter classes exist yet. There is no ViewModel/repository for these screens.

### Platform integrations live in `util/` as thin helper classes

- `NetworkMonitor` — ConnectivityManager callbacks as `StateFlow<Boolean>`; you call `startMonitoring()` /
  `stopMonitoring()` yourself, nothing is lifecycle-aware. `LoginActivity` starts it and never stops it.
- `BiometricHelper` — callback-based `BiometricPrompt`; wired to `binding.btnBiometric` in `LoginActivity`.
- `NotificationHelper` — channel `ai_assistant_channel`; used by `receiver/AlarmReceiver`.
- `AlarmHelper`, `BluetoothHelper`, `GoogleAuthHelper` — written but **never instantiated anywhere**. The Google
  button in `LoginActivity` only shows a Toast; `GoogleAuthHelper.requestIdToken` is commented out pending a web
  client ID.
- `receiver/SystemEventReceiver` listens for `PHONE_STATE` / `SMS_RECEIVED` and currently only logs.
- `widget/AI_Assistant` + `AI_AssistantConfigureActivity` provide the home-screen widget.

Manifest permissions for SMS, phone state, Bluetooth, notifications and exact alarms are declared, but **no runtime
permission request exists anywhere in the codebase** — no `registerForActivityResult` /
`RequestPermission` contract, no `checkSelfPermission` call. `BluetoothHelper.getPairedDevicesNames()` papers over
this with `@SuppressLint("MissingPermission")` and will throw on a real device until the request is added at the
call site.

`AlarmHelper` is the exception, and it's the pattern to copy: `canScheduleExactAlarms()` wraps the API-31 check,
`setExactAlarm()` silently degrades to `setAndAllowWhileIdle` and returns `false` rather than letting the system
throw `SecurityException`, and `requestExactAlarmPermission()` opens `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` for the
caller to use when the feature genuinely needs precision.

### ViewBinding and responsive layout variants

`buildFeatures { viewBinding = true }`. Several layouts have width-qualified variants
(`layout-w936dp/`, `layout-w1240dp/` for `activity_login` and `content_scrolling`). Two separate rules apply:

- When a view exists in only some variants, its binding field becomes **nullable** — hence
  `binding.btnGoogle?.setOnClickListener` in `LoginActivity`. Adding a view to one variant means adding it to all of
  them, or handling the null.
- Every variant of a layout must agree on the **root element's ID** — present in all or absent in all, same value.
  Disagreeing is a hard build failure in `dataBindingGenBaseClassesDebug`, not a warning — this one has already bitten
  the repo once (see "Build status" above).

## Lint rules that will fail the build

`lint.xml` promotes these to `error`: `HardcodedText` (all display strings must come from `strings.xml`),
`MissingPermission`, `StaticFieldLeak`, `HandlerLeak`. `TooManyViews`, `TooDeepLayout`, and `UnusedResources`
are warnings.

The `HardcodedText` violations that used to litter the tree are gone: no `android:text` / `android:title` literal
remains anywhere under `res/`, and `AndroidManifest.xml` activity labels are `@string/nav_*` rather than the old
inline "Cá nhân" / "Khám phá" / "Lịch trình". Keep it that way.

`HardcodedText` only inspects resource files, so Kotlin string literals are a convention here, not a lint gate.
`LoginActivity` routes its Toasts through `getString(...)`; the one remaining literal is the IDE-template Snackbar in
`ScrollingActivity.kt:24` ("Replace with your own action"), in a screen nothing launches.

**No Lint report has been produced yet, so the error-severity list above is still unverified against the tree.**
`:app:lint` depends on `compileDebugKotlin`, so any Kotlin compile error blocks it — check `assembleDebug` passes
before assuming a lint failure is a lint problem. Report lands at `app/build/reports/lint-results-debug.html`.

There is also a design-system note buried in `res/values/styles.xml`: named styles (`Text.Heading1`,
`Button.Primary`, `Button.Social`, `InputField`, `Container.Card`, `ListItem`, `ShapeAppearance.App.*`) already exist
and layouts are expected to use them instead of per-view attributes.

## Theming

Material 3 DayNight, `Theme.Application_AI_Assisstant` (parent `Theme.Material3.DayNight.NoActionBar`), with named
color tokens in `res/values/colors.xml` (`primary_500`, `background_primary`, `text_primary`, …) and
`values-night/` + `values-v31/` + `values-night-v31/` overrides. Use the token names and theme attributes
(`colorPrimary`, `colorSurface`) rather than raw hex in layouts.

<!-- gitnexus:start -->
# GitNexus — Code Intelligence

This project is indexed by GitNexus as **NativeKotlin** (1190 symbols, 1931 relationships, 40 execution flows). Use the GitNexus MCP tools to understand code, assess impact, and navigate safely.

> Index stale? Run `node .gitnexus/run.cjs analyze` from the project root — it auto-selects an available runner. No `.gitnexus/run.cjs` yet? `npx gitnexus analyze` (npm 11 crash → `npm i -g gitnexus`; #1939).

## Always Do

- **MUST run impact analysis before editing any symbol.** Before modifying a function, class, or method, run `impact({target: "symbolName", direction: "upstream"})` and report the blast radius (direct callers, affected processes, risk level) to the user.
- **MUST run `detect_changes()` before committing** to verify your changes only affect expected symbols and execution flows. For regression review, compare against the default branch: `detect_changes({scope: "compare", base_ref: "main"})`.
- **MUST warn the user** if impact analysis returns HIGH or CRITICAL risk before proceeding with edits.
- When exploring unfamiliar code, use `query({search_query: "concept"})` to find execution flows instead of grepping. It returns process-grouped results ranked by relevance.
- When you need full context on a specific symbol — callers, callees, which execution flows it participates in — use `context({name: "symbolName"})`.
- For security review, `explain({target: "fileOrSymbol"})` lists taint findings (source→sink flows; needs `analyze --pdg`).

## Never Do

- NEVER edit a function, class, or method without first running `impact` on it.
- NEVER ignore HIGH or CRITICAL risk warnings from impact analysis.
- NEVER rename symbols with find-and-replace — use `rename` which understands the call graph.
- NEVER commit changes without running `detect_changes()` to check affected scope.

## Resources

| Resource | Use for |
|----------|---------|
| `gitnexus://repo/NativeKotlin/context` | Codebase overview, check index freshness |
| `gitnexus://repo/NativeKotlin/clusters` | All functional areas |
| `gitnexus://repo/NativeKotlin/processes` | All execution flows |
| `gitnexus://repo/NativeKotlin/process/{name}` | Step-by-step execution trace |

## CLI

| Task | Read this skill file |
|------|---------------------|
| Understand architecture / "How does X work?" | `.claude/skills/gitnexus/gitnexus-exploring/SKILL.md` |
| Blast radius / "What breaks if I change X?" | `.claude/skills/gitnexus/gitnexus-impact-analysis/SKILL.md` |
| Trace bugs / "Why is X failing?" | `.claude/skills/gitnexus/gitnexus-debugging/SKILL.md` |
| Rename / extract / split / refactor | `.claude/skills/gitnexus/gitnexus-refactoring/SKILL.md` |
| Tools, resources, schema reference | `.claude/skills/gitnexus/gitnexus-guide/SKILL.md` |
| Index, status, clean, wiki CLI commands | `.claude/skills/gitnexus/gitnexus-cli/SKILL.md` |

<!-- gitnexus:end -->
