# Verification notes

Status legend: **PASSED** (actually performed, result recorded) · **PENDING** (not performed yet).
Nothing below is reported as passed unless it was run.

## Performed

| Check | Environment | Result |
|---|---|---|
| Domain unit tests (`CalculatorEngineTest`, `GenerationTest`, `PracticeServiceTest`, 48 tests) | Authoring sandbox, OpenJDK 21, Kotlin 2.0.21 compiler + JUnit 4.13.2 run directly (no Android Gradle build — Google Maven was unreachable there) | **PASSED** 48/48, 2026-10-01 |
| ELF alignment checker smoke test (zip without `.so` → `NO_NATIVE_LIBS`; 4 KB-aligned x86_64 `.so` → FAIL) | Same sandbox | **PASSED** |
| PKCS12 upload keystore generated (RSA 4096, alias `mathmilo`) | Same sandbox | Done — kept outside the repo |

## Pending — first CI run (GitHub Actions)

| Check | Status |
|---|---|
| Gradle sync with pinned AGP 8.13.0 / Kotlin 2.2.10 / KSP 2.2.10-2.0.2 | PENDING |
| Full Android compile (Compose UI, Room KSP) | PENDING |
| `testDebugUnitTest` via Gradle | PENDING |
| `lintRelease` | PENDING |
| Signed `assembleRelease` + `bundleRelease` | PENDING |
| `apksigner verify --print-certs`, not `CN=Android Debug`, signer = release key | PENDING |
| AAB `jarsigner -verify` + signer = release key | PENDING |
| Packaged permissions = none (besides androidx.core's signature-level receiver permission) | PENDING |
| Native libraries / 16 KB alignment of the real APK + AAB | PENDING (expected `NO_NATIVE_LIBS`; confirm in `verification-report.txt`) |
| Commit generated `app/schemas/com.mathmilo.data.local.MathMiloDatabase/1.json` | PENDING |

## Pending — device / emulator (record device, Android version, artifact)

| Check | Status |
|---|---|
| `adb install` signed release APK; `adb logcat` clean | PENDING |
| First launch in airplane mode | PENDING |
| Calculator: arithmetic, ≈ results, divide by zero, range error, history (empty at start, 50 max, clear confirm) | PENDING |
| Result practice: eligible (e.g. 6 × 4) and unsupported (e.g. 1 ÷ 3, 2000) | PENDING |
| Daily 10 questions, bridge planks, completion → result | PENDING |
| Resume after process death (`adb shell am kill` while backgrounded) — same question, no duplicate answer | PENDING |
| Mistake removed only after correct retry; daily result unchanged | PENDING |
| Date change (set device date forward/back) — new set / reuse | PENDING |
| Rotation, tablet/resizable window, 200 % font, TalkBack order and labels, predictive Back | PENDING |
| Settings resets (hold-to-confirm and typed RESET) | PENDING |
| No permission prompts, no crashes, no network use | PENDING |
| R8 + resource shrinking enabled and re-verified | PENDING (deliberately off until the steps above pass) |

Device: _fill in_ · Android version: _fill in_ · Artifact: _CI run # / SHA-256_
