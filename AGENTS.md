# AVF Simulator Project Rules

## Core Principles
1. **Performance First**: Prioritize native JNI execution or AVF over slower emulated backends where possible.
2. **Permission Handling**: Always check for Shizuku availability if standard framework calls fail.
3. **Android 16 Compatibility**: Support Baklava-specific capabilities (API 36+) while maintaining compatibility with API 35+.
4. **Stability**: Always use `VMForegroundService` with a `WakeLock` for any binary execution to prevent Android's OOM killer.
5. **Modern State**: Use specialized Compose state holders (`mutableIntStateOf`, etc.) and avoid legacy SDK checks (minSdk 35).

## Technical Standards
- **JNI Bridge**: `native-lib.cpp` is the source of truth for `forkAndExec`. Environment variables and FD management are mandatory. `prctl(PR_SET_PDEATHSIG, SIGTERM)` must be used to ensure process cleanup.
- **Storage**: Never use `content://` URIs directly in shell commands. Use `StorageHelper` to localize files first. Use a 1MB buffer for high-performance copies.
- **Asset Management**: Use `DownloadHelper` for all runtime binary and image downloads. Always verify file permissions (executable bit) after download.
- **UI**: Jetpack Compose with Material Design 3. Ensure touch mapping for VNC uses absolute pointer devices (`usb-tablet`) in QEMU profiles.
- **Diagnostics**: `DiagnosticHelper` must be updated whenever new capability checks are added.

## Verification
- `gradlew clean assembleDebug`
- Manual verification of Shizuku binder connectivity.
- Verification of Sparse Disk creation in `VirtualDisks` directory.
