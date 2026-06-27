# AVF Simulator Project Rules

## Core Principles
1. **Performance First**: Prioritize native JNI execution or AVF over slower emulated backends where possible.
2. **Permission Handling**: Always check for Shizuku availability if standard framework calls fail.
3. **Android 16 Compatibility**: Maintain Baklava-specific checks (`isAnyVmTypeSupported`).
4. **Stability**: Always use `VMForegroundService` with a `WakeLock` for any binary execution to prevent Android's OOM killer.

## Technical Standards
- **JNI Bridge**: `native-lib.cpp` is the source of truth for `forkAndExec`. Environment variables and FD management are mandatory.
- **Storage**: Never use `content://` URIs directly in shell commands. Use `StorageHelper` to localize files first.
- **UI**: Jetpack Compose with Material Design 3. Ensure state hoisted to `MainActivity` or dedicated State objects.
- **Diagnostics**: `DiagnosticHelper` must be updated whenever new capability checks are added.

## Verification
- `gradle :app:assembleDebug`
- Manual verification of Shizuku binder connectivity.
- Verification of Sparse Disk creation in `VirtualDisks` directory.
