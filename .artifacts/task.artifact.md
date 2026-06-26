# Task: Podroid-style Refactoring & Bug Fixes

## Infrastructure
- [ ] Fix Git Credential Helper (Global config)

## PRoot & Virtualization Logic (Podroid-Style)
- [ ] Enhance PRoot command construction with fake-root and system bind mounts
- [ ] Implement environment variable management (PATH, LD_LIBRARY_PATH, TERM)
- [ ] Refactor `forkAndExec` in `MainActivity.kt` to support complex environments

## Binary & Rootfs Management
- [ ] Improve "Rootfs" detection and handling
- [ ] Add explicit architecture checks for binaries

## Documentation & Cleanup
- [ ] Update `README.md` with Podroid-style technical instructions
- [ ] Update `AGENTS.md` with new work guidance for native-speed execution
- [ ] Remove any remaining placeholder logic or comments

## Final Verification
- [ ] Run `gradle :app:assembleDebug`
- [ ] Verify logic via code inspection (since hardware is needed for full test)
