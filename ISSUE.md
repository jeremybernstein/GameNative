# Hardcoded app ID breaks builds with applicationIdSuffix

## Problem

`/data/data/app.gamenative` is hardcoded throughout the codebase. Any build with a different application ID (e.g. `.debug` or `.gold` suffixes) breaks the controller input pipeline — evshim can't find gamepad.mem, WinHandler writes to the wrong path, Wine E: drive mount breaks.

## Affected areas

1. **evshim.c** (imagefs) — hardcoded gamepad.mem path; also uses `read()` which advances file offset (should be `pread()` at offset 0)
2. **WinHandler.java** — hardcoded gamepad.mem path
3. **BionicProgramLauncherComponent.java** — hardcoded gamepad.mem touch, evshim preload path
4. **Container.java** — hardcoded in MEDIACONV_ENV_VARS and DEFAULT_DRIVES
5. **WineUtils.java** — hardcoded E: drive path
6. **DXVKHelper.java** — hardcoded rootDir for DXVK cache path

## Proposed fix

### A. Java side

Replace all `/data/data/app.gamenative` with `"/data/data/" + BuildConfig.APPLICATION_ID`. ~5 files, straightforward.

Pass data dir to evshim via env var:
```java
envVars.put("EVSHIM_DATA_DIR", "/data/data/" + BuildConfig.APPLICATION_ID);
```

### B. evshim.c (rebuild into imagefs)

Three changes:
- Read `EVSHIM_DATA_DIR` env var, fall back to `/data/data/app.gamenative`
- Replace `read()` with `pread(fd, buf, sz, 0)` — current code advances file offset, only works once then hits EOF forever
- Replace `#include <SDL2/SDL.h>` with inline type stubs (SDL is resolved via dlsym at runtime, header unnecessary and unavailable outside imagefs build)

### Workaround in this PR

evshim is compiled from source via NDK, placed in jniLibs, and copied over the imagefs copy at launch in BionicProgramLauncherComponent. Works but fragile — ideally the imagefs evshim gets rebuilt with these fixes and the copy-over workaround is removed.
