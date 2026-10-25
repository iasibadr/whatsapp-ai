# Build from source

Requires JDK 17+, Gradle 9.6.1, Android SDK platform 35 and build tools installed. Android Gradle Plugin is pinned to 9.3.1. Set ANDROID_HOME to your SDK location. No private machine path or SDK is bundled.

From the repository root:

```sh
gradle :app:assembleRelease :app:testDebugUnitTest :app:lintRelease --no-daemon
```

The unsigned release APK is produced under app/build/outputs/apk/release/. Initial dependency resolution requires access to Google Maven, Maven Central and the scoped JitPack repositories in settings.gradle. No API credential or phone is needed to compile. Do not connect a personal phone for CI.

The portable Gradle build replaces the original machine-specific direct Java/dex packaging script. It preserves the source layout and ARM64 native-library restriction. Policy tests with main methods are run separately using python3 scripts/test_policies.py.

## Signing

Sign and align your release with Android SDK zipalign/apksigner using your own private signing key. Never commit a keystore or password. Keep the key outside the repository and use an environment/file input for passwords, not a literal argument.

The supplied original WhatsApp APK uses a dedicated lab signing certificate; it is not presented as a production signing service. Source builds signed by another key cannot replace it in place. Do not uninstall an installed app casually: private app data may be lost. No signing key is distributed.

## Reproducibility boundary

These sources were recovered from the build directories corresponding to the supplied APKs. Publication changes concern build portability and documentation. A new APK can differ because of signing, build toolchain and packaging. We do not claim bit-for-bit reproduction or that a rebuilt APK has been validated on a phone.

The application code is MIT. Dependencies retain their licenses. You may rebuild with modified LGPL components and your own signature; see THIRD_PARTY_NOTICES.md for the upstream sources and bundled notices.

## Current lint outcome

The compile step succeeds, but `:app:lintRelease` currently exits nonzero with 21 errors and 86 warnings. This is a known reported failure, not a missing dependency. See AUDIT.md; CI retains a diagnostic lint step without treating this as production clearance.
