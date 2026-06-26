# Implementation Plan - Podroid-style Refactoring & Git Fix

This plan refactors the virtualization logic to match the performance and compatibility of the Podroid GitHub repository, focusing on PRoot environment management and system-level bind mounts.

## User Review Required

> [!IMPORTANT]
> The Git error `fatal: could not read Username` is an environmental issue. I will attempt to set a global credential helper to mitigate this, but the user may still need to perform a manual login once.

## Proposed Changes

### 1. Git Environment Fix
Attempt to set a persistent credential helper to avoid the `askpass` failure.

#### [Terminal]
- `git config --global credential.helper store`

---

### 2. Podroid-style PRoot Logic
Update the PRoot execution logic to include essential system mounts and fake-root capabilities, mimicking how Podroid runs Linux environments.

#### [MainActivity.kt](file:///D:/AVF-Sim/app/src/main/java/com/example/MainActivity.kt)
- Update `bootVirtualMachine` for the `proot` backend.
- Add mounts: `/dev`, `/proc`, `/sys`, `/dev/shm`.
- Set environment variables: `PATH`, `TERM=xterm-256color`, `HOME=/root`.

---

### 3. Documentation Alignment
Update the project documentation to reflect the new technical standards.

#### [README.md](file:///D:/AVF-Sim/README.md)
- Update "Direct C++ PRoot Engine" description.
- Add instructions for Rootfs preparation.

#### [AGENTS.md](file:///D:/AVF-Sim/AGENTS.md)
- Add rules for maintaining "Podroid-style" performance.

---

### 4. Code Cleanup & Bug Fixes
- Fix potential NPEs in the file picker.
- Ensure `forkAndExec` handles large command strings correctly.

## Verification Plan

### Automated Tests
- `gradlew :app:assembleDebug`

### Manual Verification
- Code review of the constructed shell commands to ensure they match valid PRoot syntax used in Podroid.
- Verify that the Git config was updated.
