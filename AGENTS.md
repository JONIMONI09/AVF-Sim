# AVF Simulator App (Remix)
## Purpose
This project is an advanced virtualization application for Android 16, utilizing Android Virtualization Framework (AVF/pKVM), QEMU, and PRoot (via a direct C++ JNI bridge) to run virtual machines natively or through emulation.

## Ownership
- `app/AGENTS.md` -> Application source code, UI, and Android components.

## Work Guidance
- Follow Material Design 3 guidelines for the UI in Jetpack Compose.
- Maintain fallback methods (QEMU/PRoot) for older Android versions or devices without AVF support.
- Manage native speed execution directly via C++ syscall bindings in `app/src/main/cpp`. For PRoot, always maintain Podroid-style system bind mounts (`/dev`, `/sys`, `/proc`) to ensure compatibility with standard Linux binaries.
- Environment variables for guest processes must include a standard Linux `PATH`, `TERM=xterm-256color`, and a valid `HOME` directory.
- VM processes should be isolated and run asynchronously in a foreground service with a WakeLock to prevent system throttling.

## Verification
- Run `gradle :app:assembleDebug` to verify.
- Test both the Shizuku path and the direct PRoot/QEMU binary execution paths.

## Child DOX Index
- `app/AGENTS.md` - Rules and instructions for the core Android Application module.
