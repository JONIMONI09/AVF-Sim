# AVF Simulator App (Baklava Edition)

A high-performance Android virtualization suite designed for Android 13-16+. This application leverages the **Android Virtualization Framework (AVF/pKVM)**, **QEMU**, and an optimized **PRoot Engine** to provide a seamless Linux execution environment on mobile hardware.

## 🚀 Key Features

- **Android 16 "Baklava" Ready**: Fully optimized for the latest pKVM capabilities, including `isAnyVmTypeSupported` checks and enhanced permission handling for custom VM types.
- **Hardware-Accelerated Virtualization**: Near-native performance using `/dev/kvm` and the official `VirtualMachineManager` API.
- **Podroid-Style PRoot Optimization**: 
    - Static PRoot engine with direct C++ JNI bridge.
    - Optimized bind mounts: `/dev`, `/proc`, `/sys`, `/dev/shm`.
    - Injection of critical environment variables (`PATH`, `TERM`, `HOME`).
- **Sparse Disk Management**:
    - Instant creation of virtual disk images (up to 100GB).
    - Sparsely allocated files save physical storage space while providing large guest capacity.
- **SAF Localization Engine**:
    - Bridges the gap between Android's Storage Access Framework (SAF) and native binaries.
    - Automatic localization of `content://` URIs to local `VirtualDisks` storage for QEMU/crosvm compatibility.
- **Robust Shizuku Integration**:
    - Automated binder acquisition and permission granting via reflection.
    - Essential for `MANAGE_VIRTUAL_MACHINE` permissions on non-rooted devices.
- **Foreground Service Persistence**: Uses Partial WakeLocks and Foreground Services to ensure VM stability during long-running background tasks.
- **Integrated Terminal & Display**: Embedded Xterm-compatible terminal and noVNC graphical viewer.

## 🛠 Architecture

The app dynamically selects the best available backend based on device capabilities:

1.  **AVF (Native)**: Utilizing `VirtualMachineManager` for hardware-backed security and performance.
2.  **Crosvm Bypass**: Direct interaction with `/dev/kvm` where framework restrictions allow.
3.  **PRoot (Static)**: Native emulation for unrooted devices, providing a high-speed Linux environment without a full kernel overhead.
4.  **QEMU**: Full system emulation for maximum compatibility with various architectures and legacy images.

## 📦 Installation & Setup

1. **System Check**: Use the 'Status & KVM' tab to verify pKVM availability.
2. **Shizuku Setup**: Highly recommended for non-root users. Install the Shizuku app and pair it to enable extended VM permissions.
3. **Storage Allocation**: Use the 'Image-Management' section to create a Sparse Disk or import an existing `.qcow2` or `.img` via SAF.
4. **Boot**: Configure your VM profile and start the session. Monitor progress via the integrated Terminal logs.

## 📄 Documentation

- [AGENTS.md](AGENTS.md): Technical guidelines and project rules for automated agents.
- [Diagnostic Guide](app/src/main/java/com/example/DiagnosticHelper.kt): Details on capability detection logic.

---
*Developed for advanced virtualization enthusiasts on the Android platform.*
