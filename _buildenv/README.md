# /app/_buildenv — HushTV release toolkit

Three scripts that wrap the dev/official build + deploy flow and
keep every release permanently tagged in git so "go back to v1.43.87"
becomes a one-line `git checkout`.

## Scripts

### `build-and-deploy-dev.sh`
The single command for the routine dev release flow:

```
/app/_buildenv/build-and-deploy-dev.sh
```

It runs `./gradlew assembleDevDebug`, scp's the APK + version.json
to the OTA server, then tags the current HEAD as `v{versionName}-dev`
via `tag-release.sh`.

### `promote-to-official.sh`
Promotes the current source tree to the Official channel. Builds the
official-flavor APK, uploads APK + version-official.json, and tags
HEAD as `v{versionName}-official`.

```
/app/_buildenv/promote-to-official.sh
```

### `tag-release.sh`
Helper used internally by the two scripts above. Reads versionName /
versionCode from `androidtv/app/build.gradle.kts` and tags HEAD as
`v{versionName}-{channel}`. Idempotent. Safe to call manually.

```
/app/_buildenv/tag-release.sh dev
/app/_buildenv/tag-release.sh official
```

### `checkout-version.sh`
Roll the source tree back to a tagged release.

```
/app/_buildenv/checkout-version.sh list                    # list all tagged releases
/app/_buildenv/checkout-version.sh 1.43.87                 # checkout dev tag
/app/_buildenv/checkout-version.sh 1.43.87 official        # checkout official tag
```

Refuses to run if the working tree has uncommitted changes — explicit
safety net.

## Workflow

```
# normal dev iteration
edit source...
edit /app/_buildenv/version.json (bump versionCode + versionName)
edit androidtv/app/build.gradle.kts (bump versionCode + versionName)
/app/_buildenv/build-and-deploy-dev.sh

# user is happy → promote to Official
edit /app/_buildenv/version-official.json
/app/_buildenv/promote-to-official.sh

# user says "go back to v1.43.87"
/app/_buildenv/checkout-version.sh 1.43.87
/app/_buildenv/build-and-deploy-dev.sh        # rebuild + redeploy that exact source
```

## Why these exist

Before May 2026, "go back to v1.43.87" meant `git log -S "1.43.87"`
hex-search through dozens of auto-commits, which wasted multiple
agent iterations and broke the user's trust. With the dev tag on
every release, that's now a one-line `git checkout`. Retroactively
applied to v1.43.86 → v1.43.93.
