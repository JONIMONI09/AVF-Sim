# Android App Module DOX

## Purpose
This module contains the Android application code (`com.example` package), Jetpack Compose UI, Foreground Services for process management, and C++ JNI bridge bindings.

## Ownership
- `src/main/java/com/example/MainActivity.kt` -> Primary view model, state engine, and process executor.
- `src/main/java/com/example/SetupWizardScreen.kt` -> App onboarding and feature overview.
- `src/main/java/com/example/VMForegroundService.kt` -> Process management for background process stability (WakeLock + Foreground Notification).
- `src/main/cpp/native-lib.cpp` -> Core JNI methods for running system binaries via direct CPU instructions (PRoot wrapper logic) bypassing standard runtime limitations.

## Work Guidance
- Use `System.loadLibrary("avfsimulator")` correctly placed within `companion object` blocks in Kotlin.
- Ensure all permissions required for virtualization (`android.permission.MANAGE_VIRTUAL_MACHINE`), networking, WakeLocks, and Foreground Services are requested at runtime when appropriate and declared in the manifest.
- The UI must inform the user about the VM's state effectively via Compose state updates.
- Native processes (such as QEMU and PRoot) are instantiated via either the `Shizuku` shell or a native JNI `forkAndExec` wrapper to bypass typical Android limitations on execution.

## Verification
- Lint checks and compilation using `gradle :app:compileDebugKotlin`.
- Run tests: `gradle :app:testDebugUnitTest`.
