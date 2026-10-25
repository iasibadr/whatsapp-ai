# Third-party software

Original application code: Copyright (c) 2026 Charadi Badr, MIT. The MIT license does not replace licenses of dependencies.

| Dependency | Version | License / source |
|---|---|---|
| OkHttp | 4.12.0 | Apache-2.0 — https://github.com/square/okhttp |
| Okio JVM | transitive; original WhatsApp build 3.6.0 | Apache-2.0 — https://github.com/square/okio |
| Kotlin stdlib | transitive; original WhatsApp build 2.1.0 | Apache-2.0 — https://github.com/JetBrains/kotlin |
| LibADB Android | 3.1.1 | Apache-2.0 option — https://github.com/MuntashirAkon/libadb-android |
| SPAKE2 Android | 2.2.1 | LGPL-3.0 — https://github.com/MuntashirAkon/spake2-java/tree/7615ddd680b990e14513ebb66eac4cb0dbf82464 |
| Conscrypt | 2.5.3 | Apache-2.0 and bundled notices — https://github.com/google/conscrypt |
| Bouncy Castle | 1.81 | MIT — https://www.bouncycastle.org |
| AndroidX annotations | transitive | Apache-2.0 — https://android.googlesource.com/platform/frameworks/support/ |
| JUnit (tests) | 4.13.2 | EPL-1.0 — https://github.com/junit-team/junit4 |

Full activation dependency notices are retained in [assets/local_activation_licenses.txt](assets/local_activation_licenses.txt) and [assets/local_activation_additional_licenses.txt](assets/local_activation_additional_licenses.txt). The latter includes LGPL/GPL texts and additional notices.

Dependencies are resolved externally; no private caches, signing keys or third-party SDK archives are copied into this repository. Build scripts and application sources are provided to allow rebuilding and replacement of library components. Modified builds can be signed with your own key; they cannot inherit the original application's signing identity.

WhatsApp, Meta, Android and OpenAI names identify compatibility targets or services, not affiliation or endorsement.
