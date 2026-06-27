# Android App Module Guidelines

## Module Structure
- `com.example.StorageHelper`: Handles SAF localization and image management.
- `com.example.DiagnosticHelper`: Core system capability detection.
- `com.example.VMForegroundService`: Ensures long-running VM processes aren't killed.
- `native-lib.cpp`: C++ bridge for optimized process spawning.

## Podroid-Style Implementation Notes
- **PRoot**: Ensure `PATH` and `HOME` are injected in the JNI bridge.
- **Sparse Disks**: Use `RandomAccessFile.setLength()` to create sparse files efficiently.
- **Shizuku**: Use reflection to support both `newProcess` signatures (Shizuku v11+ vs older).

## Verification
- Compile: `gradle :app:assembleDebug`
- Check logs: `adb logcat | grep -i AVFSim`
