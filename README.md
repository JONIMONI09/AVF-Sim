# Android Virtualization Framework (AVF) Manager

A powerful Android application to manage, configure, and boot hardware-accelerated virtual machines using the native Android Virtualization Framework (AVF) and pKVM.

## Features

- **Native AVF & pKVM Support**: Boot virtual machines with near-native performance using Android's built-in Kernel-based Virtual Machine (KVM).
- **No Root Required (if AVF is supported natively)**: Automatically detects and utilizes the Android `VirtualMachineManager` API if the environment allows it.
- **Shizuku Integration**: Seamlessly elevates privileges via Shizuku to unlock `USE_CUSTOM_VIRTUAL_MACHINE` permissions on unrooted devices.
- **QEMU / Limbo Fallback**: Robust fallback options for legacy systems using QEMU binaries.
- **Custom Hardware Configuration**: Allocate RAM, virtual CPU cores (vCPUs), display modes (VNC / VirtIO-GPU), and attach custom ISO/IMG files.
- **Interactive Terminal**: An integrated, real-time command line terminal communicating securely with the active guest OS.

## Requirements

- **Android 13+ (Android 14/15 highly recommended)** for native Virtualization Framework support.
- **Hardware Virtualization Support**: Your SOC must support virtualization (KVM) and it must be exposed by the bootloader.
- *(Optional but recommended)* **Shizuku**: For bypassing framework restrictions on custom payloads without rooting your device.

## Usage Guide

1. **Verify Capabilities**: Check the 'Status & KVM' tab to ensure your firmware supports hardware acceleration (`/dev/kvm`).
2. **Authorize Shizuku**: If the framework prevents creating unverified VM instances, authorize Shizuku to automatically grant the required system permissions.
3. **Create a Profile**: Go to 'VM-Profile' and setup your desired OS configuration. Be sure to allocate appropriate RAM depending on the Linux distribution.
4. **Boot**: Hit 'Maschine Starten'. The terminal will open and display the raw boot sequence and kernel logs.

## Technical Details

The application attempts to act as a frontend GUI for either:
1. `/apex/com.android.virt/bin/crosvm`: The official ChromeOS Virtual Machine Monitor ported to Android AVF.
2. Android `VirtualMachineManager` Java APIs (for Microdroid payloads).
3. Statically compiled QEMU AArch64/x86_64 binaries.

*Developed with Jetpack Compose & Material Design 3.*
