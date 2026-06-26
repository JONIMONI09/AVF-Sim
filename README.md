# Android Virtualization Framework (AVF) Manager (Remix)

A powerful, high-performance Android application to manage, configure, and boot hardware-accelerated virtual machines. This application utilizes the native Android Virtualization Framework (AVF/pKVM), QEMU, and PRoot (via a direct C++ JNI bridge) to run virtual machines natively or through emulation with foreground service persistence.

## Features

- **Native AVF & pKVM Support**: Boot virtual machines with near-native performance using Android's built-in Kernel-based Virtual Machine (KVM) and `crosvm`.
- **Direct C++ PRoot Engine**: A high-performance native bridge utilizing a static PRoot engine with **Podroid-optimized bind mounts** (`/dev`, `/proc`, `/sys`, `/dev/shm`) and environment variables (`PATH`, `TERM`, `HOME`). This bypasses standard Android sandbox constraints to achieve near-native execution speeds for Linux environments.
- **Rootfs Management**: Support for custom root filesystems, allowing you to run full Linux distributions (Debian, Ubuntu, Alpine) directly within the PRoot wrapper.
- **QEMU / Limbo Fallback**: Robust fallback options for legacy systems using QEMU binaries.
- **Foreground Service Persistence**: Virtual machines run isolated in a background Android service utilizing Partial WakeLocks to prevent Android from aggressively killing the VM.
- **Integrated VM Display (noVNC)**: Seamlessly view and control the virtual machine's GUI via an embedded noVNC viewer rendering QEMU's WebSockets payload.
- **Shizuku Integration**: Seamlessly elevates privileges via Shizuku to unlock hardware nodes and permissions on unrooted devices.
- **Interactive Terminal**: An integrated, real-time command line terminal communicating securely with the active guest OS.

## Requirements

- **Android 13+ (Android 14/15/16 highly recommended)** for native Virtualization Framework support.
- **Hardware Virtualization Support**: Your SOC must support virtualization (KVM) and it must be exposed by the bootloader.
- *(Optional but recommended)* **Shizuku**: For bypassing framework restrictions on custom payloads without rooting your device.

## Usage Guide

1. **Verify Capabilities**: Check the 'Status & KVM' tab to ensure your firmware supports hardware acceleration (`/dev/kvm`).
2. **Authorize Shizuku**: If the framework prevents creating unverified VM instances, authorize Shizuku.
3. **Install Binaries**: Navigate to 'Status & KVM' and download the required QEMU and PRoot Engine binaries via the built-in loader.
4. **Create a Profile**: Go to 'VM-Profile' and setup your desired OS configuration. Be sure to allocate appropriate RAM depending on the Linux distribution.
5. **Boot**: Hit 'Maschine Starten'. The foreground service will acquire a WakeLock, and the terminal will output the kernel logs.
6. **VM Display**: Switch to the 'VM Display' tab to view the graphical interface of the OS (requires VNC enabled in the profile).

## Technical Details

The application attempts to act as a frontend GUI for either:
1. `/apex/com.android.virt/bin/crosvm`: The official ChromeOS Virtual Machine Monitor ported to Android AVF.
2. Direct C++ JNI `forkAndExec()` execution wrapping a static PRoot implementation for near-native emulation speeds.
3. Statically compiled QEMU AArch64 binaries.

*Developed with Jetpack Compose & Material Design 3.*
