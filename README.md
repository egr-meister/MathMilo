# MathMilo

Offline calculator and daily arithmetic practice for children — native Android, Kotlin + Jetpack Compose.
No accounts, network, ads, analytics, notifications or permissions. Interface language: English.

## Features

| Area | What it does |
|---|---|
| **Calculator Dock** | Digits, decimal point, + − × ÷, =, C, ⌫ and ± (change sign, needed for negative operands). One binary operation at a time. |
| **Calculation history** | Latest 50 successful calculations. Tap an entry → *Use result* / *Turn result into practice*. Clearing asks for confirmation. Starts empty. |
| **Turn result into practice** | Builds one question whose answer equals an eligible calculator result. Does not touch the daily set. |
| **Daily Challenge Bridge** | 10 numbered planks (○ not yet · ✓ correct · ✗ to review). One persistent set per local date. |
| **Daily result** | Date, correct of 10, accuracy, question review, *Practice mistakes*, *Back to island*. |
| **Mistake Practice** | Unresolved mistakes grouped by operation; retry with reshuffled options; removed only after a correct retry. |
| **Progress Stars** | Today, completed days, daily vs extra answers, mistakes resolved, recent days (same star for every completed day). |
| **Settings** | Practice level, reduced decorative animation, clear history / practice / all data (grown-up hold-to-confirm or type RESET), bundled Privacy screen. |

## Architecture

Single `:app` module, manual DI (`AppContainer`), MVVM with `StateFlow` and lifecycle-aware collection.

```
domain/            pure Kotlin, no Android imports — fully unit-tested
  Operator.kt
  calculator/      CalculatorEngine (state machine), Arithmetic (BigDecimal), PracticeEligibility
  generation/      Question, PracticeLevel, ResultPracticeGenerator, Distractors, Explanations
  daily/           DailyGenerator, DayRules, TimeProvider (injectable clock/date)
  mistakes/        MistakeRules (dedupe key, grouping)
  progress/        PracticeService (all answer/mistake/retention rules), PracticeStore (persistence port), models
data/
  local/           Room entities, DAOs, MathMiloDatabase (+ Migrations), RoomPracticeStore, mappers
  repository/      AppContainer, PracticeRepository, CalculationRepository, PreferencesRepository (DataStore)
ui/
  island/          main screen: backdrop, Calculator Dock, Bridge, Mistake sign, Progress Stars
  calculator/      CalculatorViewModel, dock + keypad, history sheet
  question/        daily question, result-based practice, shared answer/feedback components
  results/ mistakes/ progress/ settings/ theme/ common/
```

`PracticeService` receives a `PracticeStore`, `TimeProvider` and `Random`, so every rule is tested
against an in-memory store with a fake clock and seeded randomness. All "mark answered / resolved /
completed" writes are conditional SQL updates (`… WHERE selectedAnswer IS NULL`) inside Room
transactions, so repeated taps or process recreation cannot double-count.

## Toolchain (pinned)

| Tool | Version |
|---|---|
| JDK | 17 (Temurin in CI) |
| Gradle (wrapper, committed) | 8.14.3 |
| Android Gradle Plugin | 8.13.0 |
| Kotlin / Compose compiler plugin | 2.2.10 |
| KSP | 2.2.10-2.0.2 |
| Compose BOM | 2025.08.00 (Material 3) |
| Room | 2.7.2 (KSP, schemas exported to `app/schemas/`) |
| DataStore | 1.1.7 |
| Navigation Compose | 2.9.3 |
| Lifecycle | 2.9.2 |
| Activity Compose | 1.10.1 |
| Coroutines | 1.10.2 |
| Android build-tools | 36.0.0 |

**compileSdk = 36, targetSdk = 36, minSdk = 26.** SDK values must never be lowered to get past a build error.

All versions live in `gradle/libs.versions.toml`. Every library used directly in code is declared as a direct dependency.

## Build commands

```bash
./gradlew testDebugUnitTest          # focused unit tests
./gradlew lintRelease                # release lint
./gradlew assembleDebug              # debug APK, no release credentials needed
./gradlew assembleRelease bundleRelease   # signed release (credentials required, see below)
scripts/verify_release.sh app/build/outputs/apk/release/app-release.apk \
  app/build/outputs/bundle/release/app-release.aab <keystore.p12> <alias>
```

Outputs:

* APK: `app/build/outputs/apk/release/app-release.apk` — for local installation (`adb install`)
* AAB: `app/build/outputs/bundle/release/app-release.aab` — **the only file to upload to Google Play**
* (if R8 enabled) mapping: `app/build/outputs/mapping/release/mapping.txt`

## Calculator limits and rounding

* Maximum absolute operand **and** result: **1,000,000** (inclusive). Larger results show a range error — never truncated or wrapped.
* Up to **6 fractional digits** per operand; extra digits are refused with a hint.
* `BigDecimal` arithmetic. Division rounds to 6 fractional digits with **HALF_UP**; multiplication results with more than 6 decimals are rounded the same way. Rounded results show **≈** and the note "≈ means the result was rounded".
* Trailing zeros are removed (`2.50` → `2.5`; `-0` → `0`).
* Division by zero shows a friendly message and creates no history entry.
* Input rules: one decimal point; leading zeros normalised; `.` on an empty operand → `0.`; an operator before the second operand replaces the operator; `=` needs two valid operands; repeated `=` does nothing; a digit after a result starts over; an operator after a result continues from it; ⌫ edits the current operand and, after a result, makes the result editable; C clears. A minus on an empty first operand starts a negative number; ± flips the sign of the current operand.
* Pressing an operator while a complete expression is pending evaluates it first (and records it), then continues.

## Result-to-practice rules

* Eligible: exact integer, not rounded, **0…1,000**. Otherwise: "Try a whole-number result from 0 to 1,000." The calculator keeps working.
* The calculator's own operation is preferred when a different valid expression exists; the exact calculator expression and its commutative swap are excluded.
* Non-negative integer operands, non-trivial where possible; division has a non-zero divisor and integer answer.
* Primes (no factor pair ≥ 2) fall back to + / −; zero always becomes a subtraction like `7 − 7`.
* Bounded candidate search, then a guaranteed `(r + k) − k` fallback.
* Calculator and result are hidden while answering; four distinct options; explanation afterwards.
* Not counted in the daily set; a wrong answer enters Mistake Practice.

## Daily generation and dates

* One set of 10 per **local calendar date** (`daily_sets.localDate` is unique). The whole set (questions + option order) is generated and saved in one transaction before the first question is shown; relaunching resumes it exactly.
* Mix: 2 per operation + 2 extra from two distinct operations that rotate by date (`epochDay mod 4`), then shuffled. No duplicates (reversed operands of + and × count as duplicates). An identical consecutive set is avoided by regenerating (bounded).
* Levels (Settings, default **Starter**) apply to the **next** set; today's set and its answers never change.

| Level | + / − operands | × factors | ÷ dividend | ÷ divisor |
|---|---|---|---|---|
| Starter | 1–10 | 1–5 | 1–20 | ≤ 10 |
| Growing | 1–50 | 1–10 | 1–100 | ≤ 10 |
| Explorer | 1–100 | 1–12 | 1–144 | ≤ 12 |

* Midnight during a question: that question can still be finished in its original set (the screen remembers the date it was opened on, even across process death). *Next* then shows **"A new day is ready"**.
* Earlier incomplete sets stay in history as incomplete and cannot be continued; their wrong answers remain in Mistake Practice. Unanswered questions are not errors.
* Clock moved back to a date that already has a set → that set is reused; no extra set is granted. No internet time check, and no claim of protection against deliberate clock changes.

## Mistake retry and score preservation

* Every wrong answer (daily or result practice) creates or updates one mistake row keyed by the normalised expression; a later wrong answer to a resolved expression reopens it.
* The list never shows the correct answer. A retry gets newly shuffled options (stored with a retry-attempt id so rotation/process death keep the same order and a second tap is ignored).
* A correct retry resolves the mistake after the feedback is shown on the retry screen; a wrong retry keeps it.
* Retries never modify the original daily answers or score. Lifetime totals live in a separate row.
* Retention: latest 90 daily sets, 50 result-practice records, 50 calculations. Totals and unresolved mistakes survive pruning.

## Offline, privacy, storage, backup

* Manifest declares **no permissions** (no `INTERNET`, no `ACCESS_NETWORK_STATE`); no networking library is included.
* Data: Room database + DataStore in app-private storage.
* `android:allowBackup="false"`, `fullBackupContent` (≤ Android 11) and `dataExtractionRules` (Android 12+) exclude all domains from cloud backup **and** device transfer.
* Privacy screen is bundled (Settings → Privacy).

### Permission verification

CI runs `aapt2 dump permissions` on the packaged release APK and fails on anything except the
signature-level `com.mathmilo.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` that androidx.core adds
(it is not a runtime permission and grants nothing to other apps). The merged release manifest's
`uses-permission` lines are appended to `verification-report.txt`.

## Android 16 (API 36) behaviour handled

* Edge-to-edge is mandatory: `enableEdgeToEdge()` + `WindowInsets.safeDrawing` (system bars and display cutouts) on every screen; system bars stay visible, no immersive mode.
* Predictive Back: `android:enableOnBackInvokedCallback="true"`; Back handled by Navigation Compose (question → island, retry → Mistake Practice, island → exit).
* Large screens ignore orientation/resizability locks: none are declared; layouts adapt (≥ 600 dp puts the calculator beside the landmarks).
* No `keepScreenOn`, no notifications, no foreground services.

## Signing (PKCS12) and GitHub Secrets

`app/build.gradle.kts` defines `signingConfigs.release` (`storeType = "PKCS12"`) and assigns it to the
release build type. Credentials come from environment variables or an uncommitted `keystore.properties`:

| Env var (CI) | keystore.properties |
|---|---|
| `ANDROID_KEYSTORE_PATH` (CI decodes the secret into a temp file) | `storeFile` |
| `ANDROID_KEYSTORE_PASSWORD` | `storePassword` |
| `ANDROID_KEY_ALIAS` | `keyAlias` |
| `ANDROID_KEY_PASSWORD` | `keyPassword` |

Release tasks fail with a clear error if any value is missing — there is **no debug-signing fallback**.
Debug builds need no credentials.

Repository secrets (Settings → Secrets and variables → Actions):
`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`.
Create the base64 value with `base64 -i mathmilo-release.p12` (macOS) or `base64 -w0` (Linux).
Never commit keystores or passwords (`.gitignore` blocks `*.p12`, `*.jks`, `keystore.properties`).

**Google Play App Signing:** the PKCS12 key above is the **upload key**. Play re-signs the app with the
**app-signing key** that Google holds. Lost upload key → request an upload-key reset in Play Console;
the app-signing key is unaffected. The AAB's upload certificate is self-signed — that is normal and CI does not reject it.

## CI (GitHub Actions — `.github/workflows/android.yml`)

1. JDK 17, Android SDK Platform 36 + build-tools 36.0.0, committed Gradle Wrapper (validated).
2. `testDebugUnitTest` and `lintRelease` (reports uploaded).
3. (push / manual runs) decode the PKCS12 keystore to `$RUNNER_TEMP`, `assembleRelease bundleRelease`.
4. `scripts/verify_release.sh`: `apksigner verify --print-certs` (fails on `CN=Android Debug` or a signer that is not the release key), `jarsigner -verify` + `keytool -printcert -jarfile` on the AAB (same checks), packaged permissions, native-library ELF alignment, `zipalign -c -P 16`.
5. Uploads APK, AAB, mapping (if any) and `verification-report.txt`; uploads `app/schemas/` (commit it after the first build).
6. Always deletes the decoded keystore. No emulator step.

## R8 / resource shrinking

Off by default (`mathmilo.minify=false` in `gradle.properties`). Order required by the spec:
1. Verify the signed, non-minified release (CI + device checks below).
2. Set `mathmilo.minify=true`, rebuild, repeat calculator, daily generation, mistake retry, persistence and navigation checks, keep `mapping.txt` (CI uploads it).

## 16 KB page-size compatibility

The app uses no NDK code and no dependency that is expected to ship `.so` files (framework SQLite is used, not bundled SQLite).
`scripts/check_elf_alignment.py` inspects the **actual** APK and AAB on every CI release run and prints
`NO_NATIVE_LIBS` or the LOAD-segment alignment of every library (fails below 16 KB for 64-bit libs);
`zipalign -c -P 16` checks APK packaging alignment. Read the result in `verification-report.txt`.
Targeting API 36 alone is not treated as proof. If a future dependency adds native code, also test on
a 16 KB emulator image before claiming runtime compatibility.

## Tests

`app/src/test` (JVM, JUnit 4, seeded randomness, fake clock, in-memory store):

* `CalculatorEngineTest` — arithmetic, HALF_UP rounding + ≈ flag, limits, divide-by-zero, input rules, repeated =, draft round-trip, result-to-practice eligibility.
* `GenerationTest` — operation balance + date rotation, uniqueness incl. reversed operands, level ranges, integer-only division, four distinct options and sign rules, distractor fallback, same-result generation for **every** value 0–1000 with guaranteed fallback, prime/zero handling, explanation correctness checked by re-evaluating every equation in every explanation.
* `PracticeServiceTest` — stable sets across relaunch, level applies next day, duplicate-answer prevention, single completion, mistake dedupe/resolve/reopen, retry option stability, retry isolation from scores, extra practice isolation, midnight + date rollback, retention without lost totals or mistakes.

## Local device verification (adb)

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
adb logcat -c && adb shell monkey -p com.mathmilo.app 1 && adb logcat --pid=$(adb shell pidof com.mathmilo.app)
adb shell am kill com.mathmilo.app            # process-death check (app in background)
adb shell cmd connectivity airplane-mode enable
```

See `VERIFICATION.md` for the checklist and current status.
